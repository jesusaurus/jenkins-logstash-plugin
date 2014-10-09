/*
 * The MIT License
 *
 * Copyright 2014 Rusty Gerard
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

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;
import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import net.sf.json.JSONObject;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Post-build action to push build log to Logstash.
 *
 * @author Rusty Gerard
 * @since 1.0.0
 */
public class LogstashNotifier extends Notifier {

  private transient String jenkinsUrl;

  public int maxLines;
  public boolean failBuild;

  @DataBoundConstructor
  public LogstashNotifier(int maxLines, boolean failBuild) {
    this(maxLines, failBuild, Jenkins.getInstance().getRootUrl());
  }

  // Constructor for unit tests
  protected LogstashNotifier(int maxLines, boolean failBuild, String jenkinsUrl) {
    this.maxLines = maxLines;
    this.failBuild = failBuild;
    this.jenkinsUrl = jenkinsUrl;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    LogstashIndexerDao dao;
    try {
      dao = getDao();
    } catch (InstantiationException e) {
      listener.getLogger().println(ExceptionUtils.getStackTrace(e));
      listener.getLogger().println("[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.");
      return !failBuild;
    }

    if (dao == null) {
      listener.getLogger().println("[logstash-plugin]: Host name not specified. Unable to send log data.");
      return !failBuild;
    }

    // FIXME: build.getLog() won't have the last few lines like "Finished: SUCCESS" because this hasn't returned yet...
    List<String> logLines;
    try {
      if (maxLines < 0) {
        int length = (int) build.getLogText().length();
        logLines = build.getLog(length);
      } else {
        logLines = build.getLog(maxLines);
      }
    } catch (IOException e) {
      logLines = Collections.emptyList();
      listener.getLogger().println(ExceptionUtils.getStackTrace(e));
      listener.getLogger().println("[logstash-plugin]: Unable to serialize log data.");
      // Continue on without log information
    }

    BuildData buildData = new BuildData(build);
    JSONObject payload = dao.buildPayload(buildData, jenkinsUrl, logLines);
    long result = dao.push(payload.toString(), listener.getLogger());
    if (result < 0) {
      listener.getLogger().println("[logstash-plugin]: Failed to send log data to " + dao.getIndexerType() + ":" + dao.getHost() + ":" + dao.getPort() + ".");
      return !failBuild;
    }

    return true;
  }

  // Method to encapsulate calls to Jenkins.getInstance() for unit-testing
  protected LogstashIndexerDao getDao() throws InstantiationException {
    return LogstashInstallation.getLogstashDescriptor().getIndexerDao();
  }

  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.BUILD;
  }

  @Override
  public Descriptor getDescriptor() {
    return (Descriptor) super.getDescriptor();
  }

  @Extension
  public static class Descriptor extends BuildStepDescriptor<Publisher> {

    @Override
    public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
      return true;
    }

    public String getDisplayName() {
      return Messages.DisplayName();
    }
  }
}
