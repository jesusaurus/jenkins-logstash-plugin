package jenkins.plugins.logstash;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@SuppressWarnings("resource")
@RunWith(MockitoJUnitRunner.class)
public class LogstashOutputStreamTest {
  // Extension of the unit under test that avoids making calls to getInstance() to get the DAO singleton
  static LogstashOutputStream createLogstashOutputStream(OutputStream delegate, LogstashWriter logstash) {
    return new LogstashOutputStream(delegate, logstash);
  }

  ByteArrayOutputStream buffer;
  @Mock LogstashWriter mockWriter;

  @Before
  public void before() throws Exception {
    buffer = new ByteArrayOutputStream();
    Mockito.doNothing().when(mockWriter).write(anyString());
    when(mockWriter.isConnectionBroken()).thenReturn(false);
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(mockWriter);
    buffer.close();
  }

  @Test
  public void constructorSuccess() throws Exception {
    new LogstashOutputStream(buffer, mockWriter);

    // Verify results
    assertEquals("Results don't match", "", buffer.toString());
  }

  @Test
  public void eolSuccess() throws Exception {
    LogstashOutputStream los = new LogstashOutputStream(buffer, mockWriter);
    String msg = "test";
    buffer.reset();

    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());
    verify(mockWriter).isConnectionBroken();
    verify(mockWriter).write(msg);
  }

  @Test
  public void eolSuccessConnectionBroken() throws Exception {
    String msg = "test";
    LogstashOutputStream los = new LogstashOutputStream(buffer, mockWriter);

    String exMessage = "[logstash-plugin]: Failed to send log data to REDIS:localhost:8080.\n" +
      "[logstash-plugin]: No Further logs will be sent.\n" +
      "java.io.IOException: BOOM!\n";

    buffer.reset();

    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());

    // Break the dao connnection after this write
    buffer.reset();

    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());
    when(mockWriter.isConnectionBroken()).thenReturn(true);

    // Verify logs still write but on further calls are made to dao
    buffer.reset();
    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());

    //Verify calls were made to the dao logging twice, not three times.
    verify(mockWriter, times(2)).write(msg);
    verify(mockWriter, times(3)).isConnectionBroken();
  }

  @Test
  public void eolSuccessNoDao() throws Exception {
    when(mockWriter.isConnectionBroken()).thenReturn(true);
    LogstashOutputStream los = new LogstashOutputStream(buffer, mockWriter);
    String msg = "test";
    buffer.reset();

    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());
    verify(mockWriter).isConnectionBroken();
  }
}
