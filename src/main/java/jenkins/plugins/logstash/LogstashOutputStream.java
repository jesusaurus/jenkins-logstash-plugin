package jenkins.plugins.logstash;

import hudson.console.ConsoleNote;
import hudson.console.PlainTextConsoleOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import jenkins.plugins.logstash.persistence.BuildData;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;
import net.sf.json.JSONObject;

/**
 * Output stream that writes each line to the provided delegate output stream
 * and also sends it to an indexer for logstash to consume.
 *
 * @author K Jonathan Harker
 * @author Rusty Gerard
 */
public class LogstashOutputStream extends PlainTextConsoleOutputStream {

  protected OutputStream delegate;
  protected LogstashIndexerDao dao;
  protected BuildData buildData;
  protected String jenkinsUrl;
  protected boolean connFailed = false;

  public LogstashOutputStream(OutputStream delegate, LogstashIndexerDao dao, BuildData buildData, String jenkinsUrl) {
    super(delegate);
    this.delegate = delegate;
    this.dao = dao;
    this.buildData = buildData;
    this.jenkinsUrl = jenkinsUrl;

    if (dao == null) {
      String msg = "[logstash-plugin]: Unable to instantiate LogstashIndexerDao with current configuration.\n";

      try {
        delegate.write(msg.getBytes());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void eol(byte[] b, int len) throws IOException {
    delegate.write(b, 0, len);
    delegate.flush();

    String line = new String(b, 0, len).trim();
    line = ConsoleNote.removeNotes(line);

    if (!line.isEmpty() && dao != null && !connFailed) {
      JSONObject payload = dao.buildPayload(buildData, jenkinsUrl, Arrays.asList(line));
      long result = dao.push(payload.toString(), new PrintStream(delegate));

      if (result < 0) {
        String msg = "[logstash-plugin]: Failed to send log data to " + dao.getIndexerType() + ":" + dao.getHost() + ":" + dao.getPort() + ".\n";
        connFailed = true;
        delegate.write(msg.getBytes());
        delegate.flush();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void flush() throws IOException {
    delegate.flush();
    super.flush();
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
