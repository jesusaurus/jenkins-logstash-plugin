/*
 * The MIT License
 *
 * Copyright 2013 Hewlett-Packard Development Company, L.P.
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
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import jenkins.model.Jenkins;
import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Build wrapper that decorates the build's logger to insert a
 * {@link LogstashNote} on each output line.
 *
 * @author K Jonathan Harker
 */
public class LogstashBuildWrapper extends BuildWrapper {

  private transient LogstashOutputStream outputStream;

  /**
   * Create a new {@link LogstashBuildWrapper}.
   */
  @DataBoundConstructor
  public LogstashBuildWrapper() {}

  /**
   * {@inheritDoc}
   */
  @Override
  public Environment setUp(@SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

    return new Environment() {};
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OutputStream decorateLogger(@SuppressWarnings("rawtypes") AbstractBuild build, OutputStream logger) {
    LogstashIndexerDao dao = null;
    try {
      dao = getDao();
    } catch (InstantiationException e) {
      try {
        logger.write(ExceptionUtils.getStackTrace(e).getBytes());
      } catch (IOException e1) {
        e.printStackTrace();
      }

    }

    BuildData buildData = new BuildData(build, new Date());
    String jenkinsUrl = getJenkinsUrl();
    outputStream = new LogstashOutputStream(logger, dao, buildData, jenkinsUrl);

    return outputStream;
  }

  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  // Method to encapsulate calls to Jenkins.getInstance() for unit-testing
  LogstashIndexerDao getDao() throws InstantiationException {
    return LogstashInstallation.getLogstashDescriptor().getIndexerDao();
  }

  String getJenkinsUrl() {
    return Jenkins.getInstance().getRootUrl();
  }

  /**
   * Registers {@link LogstashBuildWrapper} as a {@link BuildWrapper}.
   */
  @Extension
  public static class DescriptorImpl extends BuildWrapperDescriptor {

    public DescriptorImpl() {
      super(LogstashBuildWrapper.class);
      load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return Messages.DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(AbstractProject<?, ?> item) {
      return true;
    }
  }
}
