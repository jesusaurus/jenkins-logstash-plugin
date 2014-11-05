1.0.1
-----
* Return Jedis connection to pool immediately after use

1.0.0
-----
* Use JedisPool to fix concurrency issue with multiple running jobs
* Update Logstash event schema (https://logstash.jira.com/browse/LOGSTASH-675)
* Add more build data to the payload (build parameters, Jenkins ID, etc.)
* Move connection info into a global config shared between all jobs
* Add a post-build action to send multiple log lines as a single event
* Add support for RabbitMQ

0.7.4
-----

* Flush downstream OutputStream when we flush.
* If the connection to redis fails, stop using redis.
* Log the activation of Logstash.

0.7.3
-----

* Continue to output to the console log when redis is down.
* Add support for more types of builds.

0.7.2
-----

* No longer duplicating every line in metadata.
* Fix bug when no password is used for redis.

0.7.1
-----

* Remove data marked by ansi-conceal escape sequence.

0.7.0
=====

* Initial working release.
