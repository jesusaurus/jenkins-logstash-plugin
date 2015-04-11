/*
 * The MIT License
 *
 * Copyright 2014 K Jonathan Harker & Rusty Gerard
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

import hudson.console.ConsoleNote;
import hudson.console.PlainTextConsoleOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import net.sf.json.JSONObject;

/**
 * Output stream that writes each line to the provided delegate output stream
 * and also sends it to an indexer for logstash to consume.
 *
 * @author K Jonathan Harker
 * @author Rusty Gerard
 */
public class LogstashOutputStream extends PlainTextConsoleOutputStream {

  protected OutputStream delegate;
  protected LogstashIndexerDao dao;
  protected BuildData buildData;
  protected String jenkinsUrl;
  protected boolean connFailed = false;

  public LogstashOutputStream(OutputStream delegate, LogstashIndexerDao dao, BuildData buildData, String jenkinsUrl) {
    super(delegate);
    this.delegate = delegate;
    this.dao = dao;
    this.buildData = buildData;
    this.jenkinsUrl = jenkinsUrl;

    if (dao == null) {
      String msg = "[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.\n";

      try {
        delegate.write(msg.getBytes());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void eol(byte[] b, int len) throws IOException {
    delegate.write(b, 0, len);
    delegate.flush();

    String line = new String(b, 0, len).trim();
    line = ConsoleNote.removeNotes(line);

    if (!line.isEmpty() && dao != null && !connFailed) {
      JSONObject payload = dao.buildPayload(buildData, jenkinsUrl, Arrays.asList(line));
      long result = dao.push(payload.toString(), new PrintStream(delegate));

      if (result < 0) {
        String msg = "[logstash-plugin]: Failed to send log data to " + dao.getIndexerType() + ":" + dao.getHost() + ":" + dao.getPort() + ".\n";
        connFailed = true;
        delegate.write(msg.getBytes());
        delegate.flush();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() throws IOException {
    delegate.flush();
    super.flush();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    delegate.close();
    super.close();
  }
}
