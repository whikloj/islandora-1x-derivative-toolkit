package ca.umanitoba.dam.islandora.derivativetoolkit.queuesplitter;

import static org.apache.camel.LoggingLevel.DEBUG;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "queuesplitter.enabled")
public class QueueSplitterProps {

    private static final Logger LOGGER = getLogger(QueueSplitterProps.class);

    @Bean
    public RouteBuilder queueSplitter() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                LOGGER.warn("QueueSplitter Routes Enabled");
                from("{{queuesplitter.input_queue}}")
                        .routeId("UmlDerivativeQueueSplitter")
                        .description("Takes a message off input and multicasts to all in the recipient list.")
                        .log(DEBUG, LOGGER, "Received message on {{queuesplitter.input_queue}} forwarding on.")
                        .multicast().parallelProcessing().recipientList(simple("{{queuesplitter.output_queues}}"), ",");
            }
        };
    }
}
