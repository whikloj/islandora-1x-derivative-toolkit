## Introduction


## Dependencies
This tool needs to access a custom endpoint on your Islandora instance. This endpoint is provided by the [islandora_rest_services](https://github.com/uml-digitalinitiatives/islandora_rest_services) module which itself relies on the [islandora_rest](https://github.com/discoverygarden/islandora_rest) module.

To allow for permitted access to the above endpoint, this tool needs to login using the [Services](https://www.drupal.org/project/services) module.

## Deployment
This is an OSGI feature, which is designed to be deployed in an OSGI container (like Apache Karaf).
It is **not** currently deployed to Maven Central or any other repositories (:sad: sorry), see the parent [islandora-1x-derivative-toolkit](https://github.com/whikloj/islandora-1x-derivative-toolkit) 
for instructions to build and load the `features.xml`.


## Configuration
The karaf configuration file `../etc/ca.umanitoba.dam.islandora.derivatives.gatekeeper.cfg` has the following options.
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
input.queue=activemq:queue:offsite_derivatives
```
The queue to consume Fedora emitted messages from.

```
output.queue=activemq:queue:derivative_queue
```
The queue to put output message for the derivative worker on.

```
objectInfo.dead.queue=activemq:queue:gk_info_dead_letter
```
In the case of difficulty getting Islandora object information (ie. content-models, derivative map) due to an error, messages are redirected to this queue.

```
concurrent.consumers=1
```
How many messages to process at one time.

**Note**: the more consumers the more requests are made to your Islandora instance at one time.

```
gatekeeper.process_dsids=OCR,HOCR
```
A comma separated list of the destination DSIDs we can handle.

```
gatekeeper.process_contentTypes=islandora:sp_large_image_cmodel
```
A comma separated list of the content-models we can handle.

```
islandora.rest.infoUri=/rest/v1/object/%PID%/full_info
```
The URL of your Islandora endpoint, the sequence `%PID%` is replaced with the current objects pid.

```
islandora.username=
islandora.password=
```
Authentication credentials for Drupal

```
islandora.hostname=http://127.0.0.1:80
```
The hostname and port of your Islandora server

```
islandora.basepath=/islandora
```
The base path to your islandora instance

```
islandora.login.service=/umldam
```
The name of your [Services](http://www.drupal.org/project/services) endpoint with user login/logout services.


## Workflow description
This tool receives standard Fedora messages from a queue, it checks immediately for one of two requirements
  1. That the message has a `methodName == ingest`
  2. That the message has a `methodName == (modifyDatastreamByReference || addDatastream)` and the &lt;atom:category scheme="fedora-types:dsID"&gt; contains **OBJ**
  
If **one** of the two above conditions is true, the gatekeeper then makes a request to Drupal using configured login credentials and to a configured endpoint to retrieve a JSON representation of the object and its derivative map.
  
This representation contains the Islandora objects _content-models_ which are compared against the configured list and the derivative maps destination\_dsids which are compared against the configured list.
  
If the object has a matching content-model and requires the creation of a derivative that matches our configured list, the message is allowed through.
  
The final step for any message allowed through is to reformat the message to appear as
```
{ 
  "pid": "<object PID>",
  "derivatives" : [<array of derivative listings>]
}
```
where the _array of derivative listings_ is of the format
```
{
  "source_dsid" : "<source DSID, which should be OBJ>",
  "destination_dsid" : "<destination DSID, like OCR or HOCR>"
}
```
  
It then outputs this message on a configurable output queue.