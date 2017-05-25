package ca.umanitoba.dam.islandora.derivatives.queuesplitter;

import static org.apache.camel.LoggingLevel.DEBUG;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;

public class QueueSplitter extends RouteBuilder {

    private static final Logger LOGGER = getLogger(QueueSplitter.class);

    @Override
    public void configure() throws Exception {

        // Dead simple multicast to a set of recipients in parallel.
        from("{{input.queue}}")
            .routeId("UmlDerivativeQueueSplitter")
            .description("Takes a message off input and multicasts to all in the recipient list.")
            .log(DEBUG, LOGGER, "Received message on {{input.queue}} forwarding on.")
            .multicast().parallelProcessing().to("{{gateway.queue}}", "{{standard.queue}}");
    }

}
