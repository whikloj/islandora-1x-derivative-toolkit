## Introduction

This is a simple multicaster it allows you to direct your Fedora messages to two queues. In my design of this tool, one output would go to the **islandora-1x-gatekeeper** and one would go to your normal Solr indexer.

## Deployment

This is an OSGI feature, which is designed to be deployed in an OSGI container (like Apache Karaf).
It is **not** currently deployed to Maven Central or any other repositories (:sad: sorry), see the parent [islandora-1x-derivative-toolkit](../) 
for instructions to build and load the `features.xml`.

## Configuration
The karaf configuration file `../etc/ca.umanitoba.dam.islandora.derivatives.queuesplitter.cfg` has the following options.

```
# JMS broker URL
jms.broker=tcp://127.0.0.1:61616
```
The protocol, address and port of the JMS Broker to connect to.

```
# JMS authentication (if required)
jms.username=
jms.password
```
The authentication credentials (if applicable) for the JMS Broker.

```
# Queue to read from
input.queue=activemq:queue:input
```
The queue to consume messages from.

```
# Gatekeeper input queue
gateway.queue=activemq:queue:derivatives
```
The queue to put output message for the gatekeeper on.

```
# Normal output queue
standard.queue=activemq:queue:standard
```
The queue to put output message for the Solr indexer on.

**Note**: If you reverse the gatekeeper and normal queues, the system doesn't care. 
I could call them `output1` and `output2` as they are interchangeable.
