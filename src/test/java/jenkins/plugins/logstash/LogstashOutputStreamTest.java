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

    @Before
    public void setUp() {
        baos = new ByteArrayOutputStream();
        los = new LogstashOutputStream(baos);

        los.rBlock = new RedisBlock("host", "80", "0", "pass", "type", "key");
        los.bBlock = new BuildBlock("job", "build", 0, "root job", 0);
    }

    @Test
    public void testEol() throws IOException {
        String msg = new String("test");
        los.eol(msg.getBytes(), msg.length());
        Assert.assertEquals(baos.toString(), msg);
    }

}
