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
package hudson.plugins.logstash;

import hudson.Extension;
import hudson.Launcher;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.OutputStream;
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

    private String jobName;
    private String buildHost;
    private int buildNum;
    private String rootJobName;
    private int rootBuildNum;

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
        this.jobName = build.getProject().getDisplayName();
        this.buildHost = build.getBuiltOn().getDisplayName();
        this.buildNum = ((Build)build).number;
        this.rootJobName = build.getProject().getRootProject().getDisplayName();
        this.rootBuildNum = ((Build)build.getRootBuild()).number;

        return new Environment() {
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) {
        return new LogstashOutputStream(logger);
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
    private class LogstashOutputStream extends LineTransformationOutputStream {

        /**
         * The delegate output stream.
         */
        private final OutputStream delegate;

        private final Jedis jedis;

        /**
         * Create a new {@link LogstashOutputStream}.
         *
         * @param delegate
         *            the delegate output stream
         */
        private LogstashOutputStream(OutputStream delegate) {
            this.delegate = delegate;

            if (LogstashBuildWrapper.this.useRedis) {
                int port = (int)Integer.parseInt(LogstashBuildWrapper.this.redis.port);
                this.jedis = new Jedis(LogstashBuildWrapper.this.redis.host, port);

                String pass = LogstashBuildWrapper.this.redis.pass;
                if (pass != null) {
                    this.jedis.auth(pass);
                }

                int numb = (int)Integer.parseInt(LogstashBuildWrapper.this.redis.numb);
                if (numb != 0) {
                   this.jedis.select(numb);
                }
            } else {
                // finals must be initialized
                this.jedis = null;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void eol(byte[] b, int len) throws IOException {
            delegate.write(b, 0, len);
            String line = new String(b, 0, len).trim().replaceAll("\\p{C}", "");

            //remove ansi-conceal sequences
            Pattern p = Pattern.compile(".*?\\[8m.*?\\[0m.*?");
            while (p.matcher(line).matches()) {
                int start = line.indexOf("[8m");
                int end = line.indexOf("[0m") + 3;
                line = line.substring(0, start) + line.substring(end);
            }

            if (LogstashBuildWrapper.this.redis != null) {
                JSONObject fields = new JSONObject();
                fields.put("message", line);
                fields.put("logsource", LogstashBuildWrapper.this.redis.type);
                fields.put("program", "jenkins");
                fields.put("job", LogstashBuildWrapper.this.jobName);
                fields.put("build", LogstashBuildWrapper.this.buildNum);
                fields.put("node", LogstashBuildWrapper.this.buildHost);
                fields.put("root-job", LogstashBuildWrapper.this.rootJobName);
                fields.put("root-build", LogstashBuildWrapper.this.rootBuildNum);

                JSONObject json = new JSONObject();
                json.put("@fields", fields);
                json.put("@type", LogstashBuildWrapper.this.redis.type);
                json.put("@message", line);

                this.jedis.rpush(LogstashBuildWrapper.this.redis.key, json.toString());
            }
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
