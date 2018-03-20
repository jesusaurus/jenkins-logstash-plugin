/*
 * The MIT License
 *
 * Copyright 2014 Rusty Gerard and Liam Newman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.logstash;


import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.Run;
import jenkins.model.Jenkins;
import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.IndexerDaoFactory;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A writer that wraps all Logstash DAOs.  Handles error reporting and per build connection state.
 * Each call to write (one line or multiple lines) sends a Logstash payload to the DAO.
 * If any write fails, writer will not attempt to send any further messages to logstash during this build.
 *
 * @author Rusty Gerard
 * @author Liam Newman
 * @author Aleksandar Kostadinov
 * @since 1.0.5
 */
public class LogstashWriter {

  private final OutputStream errorStream;
  private final Run<?, ?> build;
  private final TaskListener listener;
  private final BuildData buildData;
  private final String jenkinsUrl;
  private final LogstashIndexerDao dao;
  private boolean connectionBroken;
  private Charset charset;

  @CheckForNull
  private final LogstashPayloadProcessor payloadProcessor;

  public LogstashWriter(Run<?, ?> run, OutputStream error, TaskListener listener) {
    this(run, error, listener, null, null);
  }

  public LogstashWriter(Run<?, ?> run, OutputStream error, TaskListener listener, Charset charset, LogstashPayloadProcessor payloadProcessor) {
    this.errorStream = error != null ? error : System.err;
    this.build = run;
    this.listener = listener;
    this.charset = charset;
    this.payloadProcessor = payloadProcessor;
    this.dao = this.getDaoOrNull();
    if (this.dao == null) {
      this.jenkinsUrl = "";
      this.buildData = null;
    } else {
      this.jenkinsUrl = getJenkinsUrl();
      this.buildData = getBuildData();
      dao.setCharset(charset);
    }
  }

  /**
   * gets the charset that Jenkins is using during this build.
   * @return
   */
  public Charset getCharset()
  {
    return charset;
  }

  // for testing only
  LogstashIndexerDao getDao()
  {
    return dao;
  }

  /**
   * Sends a logstash payload for a single line to the indexer.
   * Call will be ignored if the line is empty or if the connection to the indexer is broken.
   * If write fails, errors will logged to errorStream and connectionBroken will be set to true.
   *
   * @param line
   *          Message, not null
   */
  public void write(String line) {
    if (!isConnectionBroken() && StringUtils.isNotEmpty(line)) {
      this.write(Arrays.asList(line));
    }
  }

  /**
   * Sends a logstash payload containing log lines from the current build.
   * Call will be ignored if the connection to the indexer is broken.
   * If write fails, errors will logged to errorStream and connectionBroken will be set to true.
   *
   * @param maxLines
   *          Maximum number of lines to be written.  Negative numbers mean "all lines".
   */
  public void writeBuildLog(int maxLines) {
    if (!isConnectionBroken()) {
      // FIXME: build.getLog() won't have the last few lines like "Finished: SUCCESS" because this hasn't returned yet...
      List<String> logLines;
      try {
        if (maxLines < 0) {
          logLines = build.getLog(Integer.MAX_VALUE);
        } else {
          logLines = build.getLog(maxLines);
        }
      } catch (IOException e) {
        String msg = "[logstash-plugin]: Unable to serialize log data.\n" +
          ExceptionUtils.getStackTrace(e);
        logErrorMessage(msg);

        // Continue with error info as logstash payload
        logLines = Arrays.asList(msg.split("\n"));
      }

      write(logLines);
    }
  }

  /**
   * @return True if errors have occurred during initialization or write.
   */
  public boolean isConnectionBroken() {
    return connectionBroken || build == null || dao == null || buildData == null;
  }

  // Method to encapsulate calls for unit-testing
  LogstashIndexerDao createDao() throws InstantiationException {
    LogstashInstallation.Descriptor descriptor = LogstashInstallation.getLogstashDescriptor();
    return IndexerDaoFactory.getInstance(descriptor.getType(), descriptor.getHost(), descriptor.getPort(),
        descriptor.getKey(), descriptor.getUsername(), descriptor.getPassword());
  }

  BuildData getBuildData() {
    if (build instanceof AbstractBuild) {
      return new BuildData((AbstractBuild) build, new Date(), listener);
    } else {
      return new BuildData(build, new Date(), listener);
    }
  }

  @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
      justification="Jenkins 2.0 will never return null. So wait for upgrade.")
  String getJenkinsUrl() {
    return Jenkins.getInstance().getRootUrl();
  }

  /**
   * Write a list of lines to the indexer as one Logstash payload.
   */
  private void write(List<String> lines) {
    write(dao.buildPayload(buildData, jenkinsUrl, lines));
  }

  /**
   * Write JSONObject payload to the Logstash indexer.
   * @since 1.0.5
   */
  private void write(JSONObject payload) {
    if (payloadProcessor != null) {
      JSONObject processedPayload = payload;
      try {
        processedPayload = payloadProcessor.process(payload);
      } catch (Exception e) {
        String msg =  ExceptionUtils.getMessage(e) + "\n" +
          "[logstash-plugin]: Error in payload processing.\n";

        logErrorMessage(msg);
      }
      if (processedPayload != null) { writeRaw(processedPayload); }
    } else {
      writeRaw(payload);
    }
  }

  /**
   * Write JSONObject payload to the Logstash indexer.
   * @since 1.0.5
   */
  private void writeRaw(JSONObject payload) {
    try {
      dao.push(payload.toString());
    } catch (IOException e) {
      String msg = "[logstash-plugin]: Failed to send log data to " + dao.getIndexerType() + ":" + dao.getDescription() + ".\n" +
        "[logstash-plugin]: No Further logs will be sent to " + dao.getDescription() + ".\n" +
        ExceptionUtils.getStackTrace(e);
      logErrorMessage(msg);
    }
  }

  /**
   * Construct a valid indexerDao or return null.
   * Writes errors to errorStream if dao constructor fails.
   *
   * @return valid {@link LogstashIndexerDao} or return null.
   */
  private LogstashIndexerDao getDaoOrNull() {
    try {
      return createDao();
    } catch (InstantiationException e) {
      String msg =  ExceptionUtils.getMessage(e) + "\n" +
        "[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.\n";

      logErrorMessage(msg);
    }
    return null;
  }

  /**
   * Write error message to errorStream and set connectionBroken to true.
   */
  @SuppressFBWarnings(
    value="DM_DEFAULT_ENCODING",
    justification="TODO: not sure how to fix this")
  private void logErrorMessage(String msg) {
    try {
      connectionBroken = true;
      errorStream.write(msg.getBytes(charset));
      errorStream.flush();
    } catch (IOException ex) {
      // This should never happen, but if it does we just have to let it go.
      ex.printStackTrace();
    }
  }

  /**
   * Signal payload processor that there will be no more lines
   */
  public void close() {
    if (payloadProcessor != null) {
      JSONObject payload = null;
      try {
        // calling finish() is mandatory to avoid memory leaks
        payload = payloadProcessor.finish();
      } catch (Exception e) {
        String msg =  ExceptionUtils.getMessage(e) + "\n" +
          "[logstash-plugin]: Error with payload processor on finish.\n";

        logErrorMessage(msg);
      }
      if (payload != null) writeRaw(payload);
    }
  }
}
