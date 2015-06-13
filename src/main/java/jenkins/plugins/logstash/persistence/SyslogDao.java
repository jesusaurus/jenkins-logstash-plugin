package jenkins.plugins.logstash.persistence;

import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import java.io.IOException;


public class SyslogDao extends AbstractLogstashIndexerDao {
  final UdpSyslogMessageSender messageSender;
  
  public SyslogDao(String host, int port, String key, String username, String password) {
    this(null, host, port, key, username, password);
  }

  public SyslogDao(UdpSyslogMessageSender udpSyslogMessageSender, String host, int port, String key, String username, String password) {
    super(host, port, key, username, password);
    messageSender = udpSyslogMessageSender == null ? new UdpSyslogMessageSender() : udpSyslogMessageSender;
  }

  @Override
  public void push(String data) throws IOException {
    // Making the JSON document compliant to Common Event Expression (CEE)
    // http://www.rsyslog.com/json-elasticsearch/
    data = " @cee: "  + data;
    // SYSLOG Configuration
    messageSender.setDefaultMessageHostname(host);
    messageSender.setDefaultAppName("jenkins:");
    messageSender.setDefaultFacility(Facility.USER);
    messageSender.setDefaultSeverity(Severity.INFORMATIONAL);
    messageSender.setSyslogServerHostname(host);
    messageSender.setSyslogServerPort(port);
    messageSender.setMessageFormat(MessageFormat.RFC_5424);
    // Sending the message
    messageSender.sendMessage(data);
  }

  @Override
  public IndexerType getIndexerType() { return IndexerType.SYSLOG; }
}
