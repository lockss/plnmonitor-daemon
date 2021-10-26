# plnmonitor-daemon
Daemon for the plnmonitor web app.

This software collects status information from all boxes in a LOCKSS network thanks to the LOCKSS SOAP WebService API (available since 1.65.5). Access is granted to each box web UI with a debug user account.
The network status data are stored into a local postgres database.

The plnmonitor-webapp then uses information collected in this local database to expose the overall LOCKSS network status in a web application. 

## Dependencies:
* Java 8
* Postgresql 9.4+
* LOCKSS 1.73+ 

## Installation

See [plnmonitor-installer](https://github.com/lockss/plnmonitor-installer)
