package jenkins.plugins.logstash;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;

import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao.IndexerType;
import net.sf.json.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@SuppressWarnings("resource")
@RunWith(MockitoJUnitRunner.class)
public class LogstashOutputStreamTest {

  ByteArrayOutputStream buffer;

  @Mock LogstashIndexerDao mockDao;
  @Mock BuildData mockBuildData;

  @Before
  public void before() throws Exception {
    when(mockDao.buildPayload(Matchers.any(BuildData.class), Matchers.anyString(), Matchers.anyListOf(String.class))).thenReturn(new JSONObject());
    Mockito.doNothing().when(mockDao).push(Matchers.startsWith("{}"));
    when(mockDao.getIndexerType()).thenReturn(IndexerType.REDIS);
    when(mockDao.getDescription()).thenReturn("localhost:8080");

    buffer = new ByteArrayOutputStream();
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(mockDao);
    verifyNoMoreInteractions(mockBuildData);
    buffer.close();
  }

  @Test
  public void constructorSuccess() throws Exception {
    new LogstashOutputStream(buffer, mockDao, mockBuildData, "http://my-jenkins-url");

    // Verify results
    assertEquals("Results don't match", "", buffer.toString());
  }

  @Test
  public void constructorSuccessNoDao() throws Exception {
    // Unit under test
    new LogstashOutputStream(buffer, null, mockBuildData, "http://my-jenkins-url");

    // Verify results
    assertEquals("Results don't match", "[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.\n[logstash-plugin]: No Further logs will be sent.\n", buffer.toString());
  }

  @Test
  public void eolSuccess() throws Exception {
    LogstashOutputStream los = new LogstashOutputStream(buffer, mockDao, mockBuildData, "http://my-jenkins-url");
    String msg = "test";
    buffer.reset();

    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());

    verify(mockDao).buildPayload(Matchers.eq(mockBuildData), Matchers.eq("http://my-jenkins-url"), Matchers.anyListOf(String.class));
    verify(mockDao).push("{}");
  }

  @Test
  public void eolSuccessNoDao() throws Exception {
    LogstashOutputStream los = new LogstashOutputStream(buffer, null, mockBuildData, "http://my-jenkins-url");
    String msg = "test";
    buffer.reset();

    // Unit under test
    los.eol(msg.getBytes(), msg.length());

    // Verify results
    assertEquals("Results don't match", msg, buffer.toString());
  }
}
