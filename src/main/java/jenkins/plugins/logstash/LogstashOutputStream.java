package jenkins.plugins.logstash;


import jenkins.plugins.logstash.LogstashBuildWrapper;
import jenkins.plugins.logstash.LogstashBuildWrapper.RedisBlock;
import jenkins.plugins.logstash.LogstashBuildWrapper.BuildBlock;

import hudson.console.PlainTextConsoleOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import net.sf.json.JSONObject;

import redis.clients.jedis.Jedis;


public class LogstashOutputStream extends PlainTextConsoleOutputStream {

    protected OutputStream delegate;

    protected boolean connFailed;
    protected Jedis jedis;

    protected RedisBlock rBlock;
    protected BuildBlock bBlock;

    public LogstashOutputStream(OutputStream d) {
        super(d);
        delegate = d;

        connFailed = false;
        jedis = null;
    }

    public void setUp(RedisBlock rb, BuildBlock bb) {
        rBlock = rb;
        bBlock = bb;
    }

    public boolean connect() {
        boolean result;
        try {
            int port = (int)Integer.parseInt(rBlock.port);
            jedis = new Jedis(rBlock.host, port);

            if (rBlock.pass != null && !rBlock.pass.isEmpty()) {
                jedis.auth(rBlock.pass);
            }

            int numb = (int)Integer.parseInt(rBlock.numb);
            if (numb != 0) {
               jedis.select(numb);
            }
            result = true;
        } catch (java.lang.Throwable t) {
            result = false;
            jedis = null;
            connFailed = true;

            StringWriter s = new StringWriter();
            PrintWriter p = new PrintWriter(s);
            t.printStackTrace(p);
            String error = "Unable to connect to redis: " + s.toString() + "\n";

            try {
                delegate.write(error.getBytes());
                delegate.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        delegate.write(b, 0, len);
        delegate.flush();

        String line = new String(b, 0, len).trim();

        if (jedis != null && !line.isEmpty() && !connFailed) {
            String blob = makeJson(line).toString();
            jedis.rpush(rBlock.key, blob);
        }
    }

    protected JSONObject makeJson(String line) {
        JSONObject fields = new JSONObject();
        fields.put("logsource", rBlock.type);
        fields.put("program", "jenkins");
        fields.put("job", bBlock.jobName);
        fields.put("build", bBlock.buildNum);
        fields.put("node", bBlock.buildHost);
        fields.put("root-job", bBlock.rootJobName);
        fields.put("root-build", bBlock.rootBuildNum);

        JSONObject json = new JSONObject();
        json.put("@fields", fields);
        json.put("@type", rBlock.type);
        json.put("@message", line);
        json.put("@source_host", new String("jenkins"));

        return json;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws IOException {
        delegate.flush();
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
