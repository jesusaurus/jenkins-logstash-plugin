package jenkins.plugins.logstash.persistence;

import static net.sf.json.test.JSONAssert.assertEquals;
import hudson.model.Result;

import java.util.Collections;
import java.util.Map;

import net.sf.json.JSONObject;

import org.junit.Test;

public class BuildDataTest {

  static final String EMPTY_STRING = "{\"buildNum\":0,\"buildDuration\":0,\"rootBuildNum\":0}";
  static final String FULL_STRING = "{\"id\":\"TEST_JOB_123\",\"result\":\"SUCCESS\",\"projectName\":\"PROJECT_NAME\",\"displayName\":\"DISPLAY NAME\",\"fullDisplayName\":\"FULL DISPLAY NAME\",\"description\":\"DESCRIPTION\",\"url\":\"http://localhost:8080/jenkins/jobs/PROJECT_NAME/123\",\"buildHost\":\"http://localhost:8080/jenkins\",\"buildLabel\":\"master\",\"buildNum\":123,\"buildDuration\":100,\"timestamp\":\"2000-02-01T00:00:00-0800\",\"rootProjectName\":\"ROOT PROJECT NAME\",\"rootProjectDisplayName\":\"ROOT PROJECT DISPLAY NAME\",\"rootBuildNum\":456,\"buildVariables\":{}}";

  @Test
  public void toJsonEmptySuccess() throws Exception {
    BuildData buildData = new BuildData();

    // Unit under test
    JSONObject result = buildData.toJson();

    // Verify results
    assertEquals("Results don't match", JSONObject.fromObject(EMPTY_STRING), result);
  }

  @Test
  public void toJsonFullSuccess() throws Exception {
    BuildData buildData = makeFullBuildData();

    // Unit under test
    JSONObject result = buildData.toJson();

    // Verify results
    assertEquals("Results don't match", JSONObject.fromObject(FULL_STRING), result);
  }

  BuildData makeFullBuildData() {
    Map<String, String> buildVariables = Collections.emptyMap();
    BuildData buildData = new BuildData();

    buildData.setBuildDuration(100);
    buildData.setBuildHost("http://localhost:8080/jenkins");
    buildData.setBuildLabel("master");
    buildData.setBuildNum(123);
    buildData.setBuildVariables(buildVariables);
    buildData.setDescription("DESCRIPTION");
    buildData.setDisplayName("DISPLAY NAME");
    buildData.setFullDisplayName("FULL DISPLAY NAME");
    buildData.setId("TEST_JOB_123");
    buildData.setProjectName("PROJECT_NAME");
    buildData.setResult(Result.SUCCESS);
    buildData.setRootBuildNum(456);
    buildData.setRootProjectDisplayName("ROOT PROJECT DISPLAY NAME");
    buildData.setRootProjectName("ROOT PROJECT NAME");
    buildData.timestamp = "2000-02-01T00:00:00-0800";
    buildData.setUrl("http://localhost:8080/jenkins/jobs/PROJECT_NAME/123");

    return buildData;
  }
}
