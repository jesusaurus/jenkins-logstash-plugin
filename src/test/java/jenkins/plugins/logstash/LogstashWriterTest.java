package jenkins.plugins.logstash;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.test.AbstractTestResultAction;
import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao.IndexerType;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jvnet.hudson.test.JenkinsRule;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LogstashWriterTest {
  @Rule public JenkinsRule j = new JenkinsRule();

  // Extension of the unit under test that avoids making calls to getInstance() to get the DAO singleton
  static LogstashWriter createLogstashWriter(final AbstractBuild<?, ?> testBuild,
                                             OutputStream error,
                                             final String url,
                                             final LogstashIndexerDao indexer,
                                             final BuildData data,
                                             final LogstashPayloadProcessor processor) {
    return new LogstashWriter(testBuild, error, null, processor) {
      @Override
      LogstashIndexerDao getDao() throws InstantiationException {
        if (indexer == null) {
          throw new InstantiationException("DoaTestInstantiationException");
        }

        return indexer;
      }

      @Override
      BuildData getBuildData() {
        assertNotNull("BuildData should never be requested for missing dao.", this.dao);

        // For testing, providing null data means use the actual method
        if (data == null) {
          return super.getBuildData();
        } else {
          return data;
        }
      }

      @Override
      String getJenkinsUrl() {
        return url;
      }
    };
  }

  static LogstashWriter createLogstashWriter(final AbstractBuild<?, ?> testBuild,
                                             OutputStream error,
                                             final String url,
                                             final LogstashIndexerDao indexer,
                                             final BuildData data) {
    return createLogstashWriter(testBuild, error, url, indexer, data, null);
  }

  ByteArrayOutputStream errorBuffer;

  @Mock LogstashIndexerDao mockDao;
  @Mock AbstractBuild mockBuild;
  @Mock AbstractTestResultAction mockTestResultAction;
  @Mock Project mockProject;

  @Mock BuildData mockBuildData;
  @Mock TaskListener mockListener;

  @Captor ArgumentCaptor<List<String>> logLinesCaptor;

  @Before
  public void before() throws Exception {

    when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
    when(mockBuild.getDisplayName()).thenReturn("LogstashNotifierTest");
    when(mockBuild.getProject()).thenReturn(mockProject);
    when(mockBuild.getParent()).thenReturn(mockProject);
    when(mockBuild.getBuiltOn()).thenReturn(null);
    when(mockBuild.getNumber()).thenReturn(123456);
    when(mockBuild.getTime()).thenReturn(new Date());
    when(mockBuild.getRootBuild()).thenReturn(mockBuild);
    when(mockBuild.getBuildVariables()).thenReturn(Collections.emptyMap());
    when(mockBuild.getSensitiveBuildVariables()).thenReturn(Collections.emptySet());
    when(mockBuild.getEnvironments()).thenReturn(null);
    when(mockBuild.getAction(AbstractTestResultAction.class)).thenReturn(mockTestResultAction);
    when(mockBuild.getLog(3)).thenReturn(Arrays.asList("line 1", "line 2", "line 3", "Log truncated..."));
    when(mockBuild.getEnvironment(null)).thenReturn(new EnvVars());

    when(mockTestResultAction.getTotalCount()).thenReturn(0);
    when(mockTestResultAction.getSkipCount()).thenReturn(0);
    when(mockTestResultAction.getFailCount()).thenReturn(0);
    when(mockTestResultAction.getFailedTests()).thenReturn(Collections.emptyList());

    when(mockProject.getName()).thenReturn("LogstashWriterTest");
    when(mockProject.getFullName()).thenReturn("parent/LogstashWriterTest");

    when(mockDao.buildPayload(Matchers.any(BuildData.class), Matchers.anyString(), Matchers.anyListOf(String.class)))
      .thenAnswer(new Answer() {
        @Override
        public JSONObject answer(InvocationOnMock invocation) {
          Object[] args = invocation.getArguments();
          JSONObject json = JSONObject.fromObject("{\"data\":{},\"message\": null,\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");
          json.element("message", args[2]);
          return json;
        }
      });

    Mockito.doNothing().when(mockDao).push(Matchers.anyString());
    when(mockDao.getIndexerType()).thenReturn(IndexerType.REDIS);
    when(mockDao.getDescription()).thenReturn("localhost:8080");

    errorBuffer = new ByteArrayOutputStream();
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(mockDao);
    verifyNoMoreInteractions(mockBuild);
    verifyNoMoreInteractions(mockBuildData);
    verifyNoMoreInteractions(mockTestResultAction);
    verifyNoMoreInteractions(mockProject);
    errorBuffer.close();
  }

  @Test
  public void constructorSuccess() throws Exception {
    createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", mockDao, null);

    // Verify that the BuildData constructor is what is being called here.
    // This also lets us verify that in the instantiation failure cases we do not construct BuildData.
    verify(mockBuild).getId();
    verify(mockBuild, times(1)).getResult();
    verify(mockBuild, times(2)).getParent();
    verify(mockBuild, times(2)).getProject();
    verify(mockBuild, times(1)).getStartTimeInMillis();
    verify(mockBuild, times(2)).getDisplayName();
    verify(mockBuild).getFullDisplayName();
    verify(mockBuild).getDescription();
    verify(mockBuild).getUrl();
    verify(mockBuild).getAction(AbstractTestResultAction.class);
    verify(mockBuild).getBuiltOn();
    verify(mockBuild, times(2)).getNumber();
    verify(mockBuild).getTime();
    verify(mockBuild, times(4)).getRootBuild();
    verify(mockBuild).getBuildVariables();
    verify(mockBuild).getSensitiveBuildVariables();
    verify(mockBuild).getEnvironments();
    verify(mockBuild).getEnvironment(null);

    verify(mockTestResultAction).getTotalCount();
    verify(mockTestResultAction).getSkipCount();
    verify(mockTestResultAction).getFailCount();
    verify(mockTestResultAction, times(1)).getFailedTests();

    verify(mockProject, times(2)).getName();
    verify(mockProject, times(2)).getFullName();

    // Verify results
    assertEquals("Results don't match", "", errorBuffer.toString());
  }

  @Test
  public void constructorSuccessNoDao() throws Exception {
    String exMessage = "InstantiationException: DoaTestInstantiationException\n" +
      "[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.\n";

    // Unit under test
    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", null, null);

    // Verify results
    assertEquals("Results don't match", exMessage, errorBuffer.toString());
    assertTrue("Connection not broken", writer.isConnectionBroken());
  }

  @Test
  public void writeSuccessNoDao() throws Exception {
    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", null, null);
    assertTrue("Connection not broken", writer.isConnectionBroken());

    String msg = "test";
    errorBuffer.reset();

    // Unit under test
    writer.write(msg);

    // Verify results
    assertEquals("Results don't match", "", errorBuffer.toString());
    assertTrue("Connection not broken", writer.isConnectionBroken());
  }

  @Test
  public void writeBuildLogSuccessNoDao() throws Exception {
    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", null, null);
    assertTrue("Connection not broken", writer.isConnectionBroken());

    errorBuffer.reset();

    // Unit under test
    writer.writeBuildLog(3);

    // Verify results
    assertEquals("Results don't match", "", errorBuffer.toString());
    assertTrue("Connection not broken", writer.isConnectionBroken());
  }

  @Test
  public void writeSuccess() throws Exception {
    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", mockDao, mockBuildData);
    String msg = "test";
    errorBuffer.reset();

    // Unit under test
    writer.write(msg);

    // Verify results
    // No error output
    assertEquals("Results don't match", "", errorBuffer.toString());

    verify(mockDao).buildPayload(Matchers.eq(mockBuildData), Matchers.eq("http://my-jenkins-url"), Matchers.anyListOf(String.class));
    verify(mockDao).push("{\"data\":{},\"message\":[\"test\"],\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");
  }

  @Test
  public void writeBuildLogSuccess() throws Exception {
    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", mockDao, mockBuildData);
    errorBuffer.reset();

    // Unit under test
    writer.writeBuildLog(3);

    // Verify results
    // No error output
    assertEquals("Results don't match", "", errorBuffer.toString());
    String lines = JSONArray.fromObject(mockBuild.getLog(3)).toString();
    verify(mockBuild, times(2)).getLog(3);

    verify(mockDao).buildPayload(Matchers.eq(mockBuildData), Matchers.eq("http://my-jenkins-url"), Matchers.anyListOf(String.class));
    verify(mockDao).push("{\"data\":{},\"message\":" + lines + ",\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");
  }

  @Test
  public void writeProcessedSuccess() throws Exception {
    String goodMsg = "test message";
    String ignoredMsg = "ignored input";
    String scriptString =
      "if (payload) {\n" +
      "  if (payload['message'][0] =~ /" + ignoredMsg + "/) {\n" +
      "    payload = null\n" +
      "  } else {\n" +
      "    console.println('l');\n" +
      "  }\n" +
      "  lastPayload = payload\n" +
      "} else {\n" +
      "  console.println('test build console message')\n" +
      "  payload = lastPayload\n" +
      "}";

    SecureGroovyScript script = new SecureGroovyScript(scriptString, true, null);
    LogstashScriptProcessor processor = new LogstashScriptProcessor(script, errorBuffer);
    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", mockDao, mockBuildData, processor);
    errorBuffer.reset();

    // Unit under test
    writer.write(goodMsg);
    writer.write(ignoredMsg);
    writer.write(goodMsg);
    writer.close();

    // Verify results
    // buffer contains 2 lines logged by the script, then standard tear down message and finally test message at close
    assertEquals("Results don't match", "l\nl\nTearing down Script Log Processor..\ntest build console message\n", errorBuffer.toString());

    InOrder inOrder = Mockito.inOrder(mockDao);

    // first message is generated and pushed to DAO
    inOrder.verify(mockDao).buildPayload(Matchers.eq(mockBuildData), Matchers.eq("http://my-jenkins-url"), Matchers.anyListOf(String.class));
    inOrder.verify(mockDao).push("{\"data\":{},\"message\":[\"" + goodMsg + "\"],\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");
    // now message only generated but filtered out by script thus not pushed to DAO
    inOrder.verify(mockDao, times(2)).buildPayload(Matchers.eq(mockBuildData), Matchers.eq("http://my-jenkins-url"), Matchers.anyListOf(String.class));
    // the message at close time is generated by the script so no call to DAO for that
    inOrder.verify(mockDao, times(2)).push("{\"data\":{},\"message\":[\"" + goodMsg + "\"],\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");
  }

  @Test
  public void writeSuccessConnectionBroken() throws Exception {
    Mockito.doNothing().doThrow(new IOException("BOOM!")).doNothing().when(mockDao).push(anyString());
    LogstashWriter los = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", mockDao, mockBuildData);


    String msg = "test";
    String exMessage = "[logstash-plugin]: Failed to send log data to REDIS:localhost:8080.\n" +
      "[logstash-plugin]: No Further logs will be sent to localhost:8080.\n" +
      "java.io.IOException: BOOM!";

    errorBuffer.reset();

    // Unit under test
    los.write(msg);

    // Verify results
    assertEquals("Results don't match", "", errorBuffer.toString());

    // Break the dao connnection
    errorBuffer.reset();

    // Unit under test
    los.write(msg);

    // Verify results
    assertTrue("Results don't match", errorBuffer.toString().startsWith(exMessage));
    assertTrue("Connection not broken", los.isConnectionBroken());

    // Verify logs still write but on further calls are made to dao
    errorBuffer.reset();
    // Unit under test
    los.write(msg);

    // Verify results
    assertEquals("Results don't match", "", errorBuffer.toString());

    //Verify calls were made to the dao logging twice, not three times.
    verify(mockDao, times(2)).buildPayload(Matchers.eq(mockBuildData), Matchers.eq("http://my-jenkins-url"), Matchers.anyListOf(String.class));
    verify(mockDao, times(2)).push("{\"data\":{},\"message\":[\"test\"],\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");
    verify(mockDao).getIndexerType();
    verify(mockDao, times(2)).getDescription();
  }

  @Test
  public void writeBuildLogGetLogError() throws Exception {
    // Initialize mocks
    when(mockBuild.getLog(3)).thenThrow(new IOException("Unable to read log file"));

    LogstashWriter writer = createLogstashWriter(mockBuild, errorBuffer, "http://my-jenkins-url", mockDao, mockBuildData);
    assertEquals("Errors were written", "", errorBuffer.toString());

    // Unit under test
    writer.writeBuildLog(3);

    // Verify results
    verify(mockBuild).getLog(3);

    List<String> expectedErrorLines =  Arrays.asList(
      "[logstash-plugin]: Unable to serialize log data.",
      "java.io.IOException: Unable to read log file");
    verify(mockDao).buildPayload(eq(mockBuildData), eq("http://my-jenkins-url"), logLinesCaptor.capture());
    List<String> actualLogLines = logLinesCaptor.getValue();
    String linesJSON = JSONArray.fromObject(actualLogLines).toString();
    verify(mockDao).push("{\"data\":{},\"message\":" + linesJSON + ",\"source\":\"jenkins\",\"source_host\":\"http://my-jenkins-url\",\"@version\":1}");

    assertThat("The exception was not sent to Logstash", actualLogLines.get(0), containsString(expectedErrorLines.get(0)));
    assertThat("The exception was not sent to Logstash", actualLogLines.get(1), containsString(expectedErrorLines.get(1)));
  }
}
