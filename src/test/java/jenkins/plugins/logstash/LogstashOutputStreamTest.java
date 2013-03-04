package jenkins.plugins.logstash;

import jenkins.plugins.logstash.LogstashBuildWrapper.RedisBlock;
import jenkins.plugins.logstash.LogstashBuildWrapper.BuildBlock;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractCIBase;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.remoting.LocalChannel;
import hudson.util.AbstractTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.AbstractExecutorService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class LogstashOutputStreamTest {

    LogstashOutputStream los;
    ByteArrayOutputStream baos;

    RedisBlock rb;
    BuildBlock bb;

    @Before
    public void setUp() {
        baos = new ByteArrayOutputStream();
        los = new LogstashOutputStream(baos);

        rb = new RedisBlock("host", "80", "0", "pass", "type", "key");
        bb = new BuildBlock("job", "build", 0, "root job", 0);

        los.setUp(rb, bb);
    }

    @Test
    public void testEol() throws IOException {
        String msg = new String("test");
        los.eol(msg.getBytes(), msg.length());
        Assert.assertEquals(baos.toString(), msg);
    }

}
