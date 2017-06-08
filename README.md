## Introduction

This bundle of tools is to push the creation of derivatives to a (or multiple) separate machines. Each tool has a more in-depth explanation of its use.

**Note**: Currently this only supports the creation of OCR and HOCR, but can be extended to other derivatives in future.

## Deployment
This is an OSGI feature, which is designed to be deployed in an OSGI container (like Apache Karaf).
It is **not** currently deployed to Maven Central or any other repositories (sorry).

To deploy this:

1. Clone the repository to the system you wish to deploy it on.

`git clone https://github.com/whikloj/islandora-1x-derivative-toolkit.git`

2. Build the application.

```
cd islandora-1x-derivative-toolkit
./gradlew clean build
```

3. Install the applications **as root**, so they are available to the system when you install them to your OSGI container.

`sudo ./gradlew install`

**Note**: Make note of your islandora-1x-derivative-toolkit directory, I'll refer to it as `$toolkit-directory` later.

### Apache Karaf

1. Enter your Apache Karaf client

```
> ../karaf/bin/client
Logging in as karaf
        __ __                  ____      
       / //_/____ __________ _/ __/      
      / ,<  / __ `/ ___/ __ `/ /_        
     / /| |/ /_/ / /  / /_/ / __/        
    /_/ |_|\__,_/_/   \__,_/_/         

  Apache Karaf (4.0.9)

Hit '<tab>' for a list of available commands
and '[cmd] --help' for help on a specific command.
Hit 'system:shutdown' to shutdown Karaf.
Hit '<ctrl-d>' or type 'logout' to disconnect shell from current session.

karaf@root()>
```

2. Add the `features.xml`, **note** replace `$toolkit-directory` with the correct absolute path from above.

```
karaf@root()> feature:repo-add file:$toolkit-directory/karaf/build/resources/main/features.xml
karaf@root()>
```

3. You can verify it was added with `feature:repo-list`

```
karaf@root()> feature:repo-list
Repository                            | URL
--------------------------------------------------------------------------------------------------
org.ops4j.pax.cdi-0.12.0              | mvn:org.ops4j.pax.cdi/pax-cdi-features/0.12.0/xml/features
framework-4.0.9                       | mvn:org.apache.karaf.features/framework/4.0.9/xml/features
islandora-1x-derivative-toolkit-0.0.1 | file:/home/ubuntu/islandora-1x-derivative-toolkit/karaf/build/resources/main/features.xml
activemq-core-5.14.5                  | mvn:org.apache.activemq/activemq-karaf/5.14.5/xml/features-core
jclouds-1.9.2                         | mvn:org.apache.jclouds.karaf/jclouds-karaf/1.9.2/xml/features
org.ops4j.pax.web-4.3.0               | mvn:org.ops4j.pax.web/pax-web-features/4.3.0/xml/features
cxf-3.1.8                             | mvn:org.apache.cxf.karaf/apache-cxf/3.1.8/xml/features
enterprise-4.0.9                      | mvn:org.apache.karaf.features/enterprise/4.0.9/xml/features
org.ops4j.pax.cdi-1.0.0.RC1           | mvn:org.ops4j.pax.cdi/pax-cdi-features/1.0.0.RC1/xml/features
standard-4.0.9                        | mvn:org.apache.karaf.features/standard/4.0.9/xml/features
activemq-5.14.5                       | mvn:org.apache.activemq/activemq-karaf/5.14.5/xml/features
spring-4.0.9                          | mvn:org.apache.karaf.features/spring/4.0.9/xml/features
camel-2.18.1                          | mvn:org.apache.camel.karaf/apache-camel/2.18.1/xml/features
org.ops4j.pax.jdbc-1.0.1              | mvn:org.ops4j.pax.jdbc/pax-jdbc-features/1.0.1/xml/features
```

There it is third line down.
    
4. You can now add any or all of the 3 features `islandora-1x-gatekeeper`, `islandora-1x-queue-splitter` or `islandora-1x-derivative-worker`

```
karaf@root()> feature:install <your choice>
karaf@root()>
```

Installed, now configure it with the instructions at the appropriate page below.

### Components

1. [islandora-1x-queue-splitter](islandora-1x-queue-splitter)
  
   This is a simple multicaster it allows you to direct your incoming JMS messages to two output queues.
    
2. [islandora-1x-gatekeeper](islandora-1x-gatekeeper)
  
   This tool compares the incoming message against a set of configurable values and if valid, passes it along on a new queue.
  
3. [islandora-1x-derivative-worker](islandora-1x-derivative-worker)
  
   This tool is a simple microservice that runs **tesseract** against a file retrieved from a URI on your Fedora server, a places the resulting output into a new URI in your Fedora server.

### Future development

Currently the derivative worker does only OCR, it would be good to either:
1. Have it perform any required derivatives for a single source.
2. Have it retrieve the source image and pass it out to possibly more distributed worker to perform the final work.
