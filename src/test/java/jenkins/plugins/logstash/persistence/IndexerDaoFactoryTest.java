package jenkins.plugins.logstash.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao.IndexerType;

import org.junit.Test;

public class IndexerDaoFactoryTest {

  @Test
  public void getAllInstances() throws Exception {
    for (IndexerType type : IndexerType.values()) {
      LogstashIndexerDao dao = IndexerDaoFactory.getInstance(type, "localhost", 1234, "key", "username", "password");

      assertNotNull("Result was null", dao);
      assertEquals("Result implements wrong IndexerType", type, dao.getIndexerType());
    }
  }
}
