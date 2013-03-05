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
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import redis.clients.jedis.Jedis;

/**
 * Build wrapper that decorates the build's logger to insert a
 * {@link LogstashNote} on each output line.
 *
 * @author K Jonathan Harker
 */
public class LogstashBuildWrapper extends BuildWrapper {

    /**
     * Encapsulate configuration data from the optionalBlock.
     */
    public static class RedisBlock {
        public String host;
        public String port;
        public String numb;
        public String pass;

        public String dataType;
        public String key;
        public String type;

        @DataBoundConstructor
        public RedisBlock(String host, String port, String numb,
                          String pass, String dataType, String key) {
            this.host = host;
            this.port = port;
            this.numb = numb;
            this.pass = pass;
            this.dataType = dataType;
            this.key = key;
            this.type = new String("jenkins");
        }
    }

    public RedisBlock redis;
    public boolean useRedis;

    public static class BuildBlock {
        public String jobName;
        public String buildHost;
        public int buildNum;
        public String rootJobName;
        public int rootBuildNum;

        public BuildBlock(String jn, String bh, int bn, String rjn, int rbn) {
            jobName = jn;
            buildHost = bh;
            buildNum = bn;
            rootJobName = rjn;
            rootBuildNum = rbn;
        }
    }

    public BuildBlock build;

    private LogstashOutputStreamWrapper outputStream;

    /**
     * Create a new {@link LogstashBuildWrapper}.
     */
    @DataBoundConstructor
    public LogstashBuildWrapper(RedisBlock redis) {
        this.redis = redis;
        this.useRedis = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {

        String jobName = build.getProject().getDisplayName();
        String buildHost = build.getBuiltOn().getDisplayName();
        int buildNum = ((Run)build).number;
        String rootJobName = build.getProject().getRootProject().getDisplayName();
        int rootBuildNum = ((Run)build.getRootBuild()).number;

        this.build = new BuildBlock(jobName, buildHost, buildNum, rootJobName, rootBuildNum);
        outputStream.bBlock = this.build;

        return new Environment() {
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) {
        outputStream =  new LogstashOutputStreamWrapper(logger);
        return outputStream;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public FormValidation doCheckHost(@QueryParameter final String value) {
        return (value.trim().length() == 0) ? FormValidation.error("Host cannot be empty.") : FormValidation.ok();
    }

    /**
     * Output stream that writes each line to the provided delegate output
     * stream and also sends it to redis for logstash to consume.
     */
    private class LogstashOutputStreamWrapper extends LogstashOutputStream {

        /**
         * Create a new {@link LogstashOutputStream}.
         *
         * @param delegate
         *            the delegate output stream
         */
        private LogstashOutputStreamWrapper(OutputStream delegate) {
            super(delegate);

            if (LogstashBuildWrapper.this.useRedis) {
                try {
                    if (redis == null) {
                        delegate.write("No redis configured.".getBytes());
                    } else {
                        rBlock = redis;
                        if(connect()) {
                            String msg = new String("Logstash plugin connected to redis://" +
                                    LogstashBuildWrapper.this.redis.host + ":" +
                                    LogstashBuildWrapper.this.redis.port + "\n");
                            delegate.write(msg.getBytes());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void eol(byte[] b, int len) throws IOException {
            delegate.write(b, 0, len);
            delegate.flush();

            String line = new String(b, 0, len).trim();

            if (redis != null && useRedis && !line.isEmpty() && !connFailed) {
                try {
                    /*
                     * TODO: queue lines received before the BuildBlock is set up.
                     * Because because decorateLogger() is called before setUp(),
                     * the BuildBlock is not available to the LogstashOutputStreamWrapper
                     * constructor. Lines sent to the logger between these two calls (such
                     * as the 'Started by...' line) will generate null json data due to a
                     * lack of build information.
                     */
                    JSONObject json = makeJson(line);
                    if (json != null) {
                        jedis.rpush(redis.key, json.toString());
                    }
                } catch (java.lang.Throwable t) {
                    connFailed = true;
                    String msg = new String("Connection to redis failed. Disabling logstash output.\n");
                    delegate.write(msg.getBytes());
                    delegate.flush();
                    t.printStackTrace(new PrintStream(delegate));
                }
            }
        }
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
