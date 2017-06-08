## Introduction

This is the remote worker program that runs **tesseract** against a file retrieved from a URI and then PUT/POSTs the result to another URI. 

Ideally you have this setup on a separate server with multiple cores as tesseract runs single threaded. It consumes from a queue which might be remote and might require a username and password to connect to.
 
The tool consumes the message output by the **islandora-1x-gatekeeper**, it checks to see if the request is for OCR, HOCR or both. If the request is _both_ then a single thread will handle this request as we can request the OBJ once and reuse it locally to reduce network traffic.
  
Otherwise, it builds the path to the source object and source datastream (for example http://fedora.server/fedora/objects/test:pid/datastreams/SOURCE/content) and retrieves it using configured authentication credentials. 
  
It then streams the source file to _tesseract_ to perform either HOCR or OCR and captures the streamed output. This output is then PUT or POSTed to the correct DSID at the end to create or update the datastream.

## Security notes

This tool requires access to your Fedora repository as an _administrator_.

My decision to use this path is that Fedora is naturally better for dealing with as a web client and if the derivative worker is going to add/update any objects in your repository then administrator level privileges make sense.

## Deployment

This is an OSGI feature, which is designed to be deployed in an OSGI container (like Apache Karaf).
It is **not** currently deployed to Maven Central or any other repositories (:sad: sorry), see the parent [islandora-1x-derivative-toolkit](../) 
for instructions to build and load the `features.xml`.

## Configuration
The karaf configuration file `../etc/ca.umanitoba.dam.islandora.derivatives.worker.cfg` has the following options.

```
error.maxRedeliveries=3
```
The number of times to retry in case of an unexpected failure. This does not include handled errors, like a bad password.

```
jms.brokerUrl=tcp://127.0.0.1:61616
```
The protocol, address and port of the JMS Broker to connect to.

```
jms.username=
jms.password=
```
The authentication credentials (if applicable) for the JMS Broker.

```
input.queue=activemq:queue:derivatives
```
The queue to read messages for the derivative worker from.

```
concurrent.consumers=1
```
How many messages to process at one time.

**Note**: As tesseract is single-threaded, it is not useful to exceed the number of cpu cores you have available.

```
fedora.url=http://localhost:8080/fedora
```
The hostname and base path to your Fedora instance.

```
fedora.authUsername=fedoraAdmin
fedora.authPassword=fedoraAdmin
```      
The authentication credentials for your Fedora.

```
tesseract.path=/usr/bin/tesseract
```
The path to your tesseract application.
