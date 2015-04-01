package jenkins.plugins.logstash;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.core.StringContains.containsString;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Project;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;

import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao.IndexerType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SuppressWarnings("rawtypes")
@RunWith(MockitoJUnitRunner.class)
public class LogstashBuildWrapperTest {
  // Extension of the unit under test that avoids making calls to Jenkins.getInstance() to get the DAO singleton
  static class MockLogstashBuildWrapper extends LogstashBuildWrapper {
    LogstashIndexerDao dao;

    MockLogstashBuildWrapper(LogstashIndexerDao dao) {
      super();
      this.dao = dao;
    }

    @Override
    LogstashIndexerDao getDao() throws InstantiationException {
      if (dao == null) {
        throw new InstantiationException("DoaTestInstantiationException");
      }

      return dao;
    }

    @Override
    String getJenkinsUrl() {
      return "http://my-jenkins-url";
    }
  }

  ByteArrayOutputStream buffer;

  @Mock AbstractBuild mockBuild;
  @Mock Project mockProject;
  @Mock LogstashIndexerDao mockDao;

  @Before
  public void before() throws Exception {
    when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
    when(mockBuild.getDisplayName()).thenReturn("LogstashNotifierTest");
    when(mockBuild.getProject()).thenReturn(mockProject);
    when(mockBuild.getBuiltOn()).thenReturn(null);
    when(mockBuild.getNumber()).thenReturn(123456);
    when(mockBuild.getDuration()).thenReturn(60L);
    when(mockBuild.getTimestamp()).thenReturn(new GregorianCalendar());
    when(mockBuild.getRootBuild()).thenReturn(mockBuild);
    when(mockBuild.getBuildVariables()).thenReturn(Collections.emptyMap());
    when(mockBuild.getLog(3)).thenReturn(Arrays.asList("line 1", "line 2", "line 3"));

    when(mockProject.getName()).thenReturn("LogstashNotifierTest");

    when(mockDao.getIndexerType()).thenReturn(IndexerType.REDIS);
    when(mockDao.getDescription()).thenReturn("localhost:8080");

    buffer = new ByteArrayOutputStream();
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(mockDao);
  }

  @Test
  public void decorateLoggerSuccess() throws Exception {
    MockLogstashBuildWrapper buildWrapper = new MockLogstashBuildWrapper(mockDao);

    // Unit under test
    OutputStream result = buildWrapper.decorateLogger(mockBuild, buffer);

    // Verify results
    assertNotNull("Result was null", result);
    assertTrue("Result is not the right type", result instanceof LogstashOutputStream);
    assertEquals("Results don't match", "", buffer.toString());
  }

  @Test
  public void decorateLoggerSuccessNoDao() throws Exception {
    MockLogstashBuildWrapper buildWrapper = new MockLogstashBuildWrapper(null);

    // Unit under test
    OutputStream result = buildWrapper.decorateLogger(mockBuild, buffer);

    // Verify results
    assertNotNull("Result was null", result);
    assertTrue("Result is not the right type", result instanceof LogstashOutputStream);
    assertThat("Results don't match", buffer.toString(), containsString("[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.\n[logstash-plugin]: No Further logs will be sent.\n"));
  }
}
