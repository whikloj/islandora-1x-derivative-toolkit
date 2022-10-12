## Introduction

This bundle of tools is to push the creation of derivatives to a (or multiple) separate machines. Each tool has a more in-depth explanation of its use.

**Note**: Currently this only supports the creation of OCR and HOCR, but can be extended to other derivatives in future.
## Workflow

1. Fedora emits messages as per normal.
2. Messages are received by the islandora-1x-queue-splitter and passed to 2 output queues.
3. Gatekeeper receives on one queue.
3. Checks with Drupal for more information.
1. If the object is of a type we want and the derivatives are ones we can do. If yes, pass along a new message.
4. Derivative worker picks up this new message with the PID and derivative source and destination dsids to use.
5. Get the source file from Fedora
6. Perform the requested action (OCR or HOCR)
7. Put the newly created data back in Fedora (either a PUT or POST).

---
![workflow animated git](https://user-images.githubusercontent.com/2857697/26948147-eb2785aa-4c5a-11e7-947d-680ce2697e49.gif)


## Deployment
This is a runnable JAR using SpringBoot.
It is **not** currently deployed to Maven Central or any other repositories (sorry).

To deploy this:

1. Clone the repository to the system you wish to deploy it on.

`git clone https://github.com/whikloj/islandora-1x-derivative-toolkit.git`

2. Build the application.

```
cd islandora-1x-derivative-toolkit
./gradlew clean build
```

The JAR file will be located in the `./build/libs` directory.

### Configuration

**Note**: The `activemq` endpoint prefix is mandatory for all ActiveMQ queue references
due to springboot's autoconfiguration.

#### Common configuration options

```shell
# ActiveMQ information
camel.component.activemq.enabled=true
camel.component.activemq.broker-url=tcp://127.0.0.1:61666
camel.component.activemq.username=
camel.component.activemq.password=
camel.component.activemq.concurrent-consumers=1

# How many times to retry
error.maxRedeliveries=10
```
#### Queue Splitter

```shell
# Queue Splitter properties.
queuesplitter.enabled=true
queuesplitter.input_queue=activemq:queue:fedora
queuesplitter.output_queues=activemq:queue:gatekeeper,activemq:queue:solr_index
```
#### Gatekeeper
Gatekeeper needs to login to Islandora do determine the derivatives that are generated.

```shell
# Islandora information
# Islandora host
islandora.hostname=http://localhost:8111
# Base path to the islandora instance
islandora.basepath=/islandora
# The REST info URI pattern, use %PID% for the pid.
islandora.rest.infoUri=/info/for/%PID%/data
# Login credentials
islandora.username=testUser
islandora.password=testPass
# You must have services configured to allow a remote login.
islandora.login_service=/login

# Gatekeeper properties
gatekeeper.enabled=true
gatekeeper.input.queue=activemq:queue:gatekeeper
gatekeeper.output.queue=activemq:queue:derivatives
gatekeeper.dead.queue=activemq:queue:dead_end
# Comma separated list of DSIDs can be processed
gatekeeper.process_dsids=HOCR,OCR
# Comma separated list of content types that can be processed
gatekeeper.process_contentTypes=islandora:sp_large_image_cmodel
# Port number for the internal REST endpoint
gatekeeper.rest.port_number=8181
# Base path for the internal REST endpoint 
gatekeeper.rest.path=/gatekeeper
```
#### Worker
Worker pulls the original file and puts the derivatives direct to Fedora.

```shell
# Fedora information
fedora.url=http://localhost:8080/fedora
fedora.authUsername=
fedora.authPassword=

# HOCR/OCR worker properties
worker.enabled=true
worker.input.queue=activemq:queue:derivatives
# Temporary directory for processing HOCR and OCR in one pass.
worker.temporary.directory=/tmp
# Path to Tesseract
worker.tesseract.path=/bin/tesseract
# Path to ImageMagick convert 
worker.convert.path=/bin/convert
# How many messages to prefetch from the remote JMS. 
# Set to 1 to avoid losing messages on a service restart
worker.jms.prefetchSize=0
# How many workers to run, should not exceed your CPUs.
worker.jms.consumers=1
```
### Future development

Currently the derivative worker does only OCR, it would be good to either:
1. Have it perform any required derivatives for a single source.
2. Have it retrieve the source image and pass it out to possibly more distributed worker to perform the final work.
