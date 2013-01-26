Jenkins Logstash Plugin
=======================

This plugin adds support for sending a job's console log to Logstash via Redis.

Install
=======

* Generate the `hpi` file with the command: `mvn package`

* Put the `hpi` file in the directory `$JENKINS_HOME/plugins`
* Restart jenkins

Configure
=========

Currently supported methods of input/output:

* redis {format => 'json_event'}

License
=======

The Logstash Plugin is licensed under the MIT License.

Contributing
============

* Fork the project on [Github](https://github.com/jesusaurus/jenkins-logstash-plugin)
* Make your feature addition or bug fix, write tests, commit.
* Send me a pull request. Bonus points for topic branches.
