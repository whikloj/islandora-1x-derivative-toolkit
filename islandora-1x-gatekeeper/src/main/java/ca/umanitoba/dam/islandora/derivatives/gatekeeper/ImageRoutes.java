package ca.umanitoba.dam.islandora.derivatives.gatekeeper;

import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.slf4j.Logger;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.ObjectInformationRetriever;

public class ImageRoutes extends RouteBuilder {

    // Logger
    private static final Logger LOGGER = getLogger(ImageRoutes.class);

    @Override
    public void configure() throws Exception {


        // Error/Redelivery handler
		onException(Exception.class)
				.maximumRedeliveries("{{error.maxRedeliveries}}")
				.handled(true)
				.log(
						ERROR,
						LOGGER,
						"Error with message ${property.message}: ${exception.message}\n\n${exception.stacktrace}");

		/**
		 * Input queue and main route.
		 */
		from("{{input.queue}}")
                .routeId("UmlDerivativeMainRouter")
                .filter(new ValidHeaderPredicate())
                .to("direct:getObjectInfo")
                .to("direct:filterByContentType")
                .to("direct:filterByDsid")
                .to("{{output.queue}}");

		/**
		 * Get the resource's information from Islandora.
		 */
		from("direct:getObjectInfo")
		    .routeId("UmlDerivativeGetInfo")
            .log(DEBUG, "in direct:getObjectInfo")
		    .bean(ObjectInformationRetriever.class, "getInfo")
		    .setProperty("objectJson", bodyAs(String.class));

		/**
		 * Compare the destination dsids from Islandora to the configuration set.
		 * Filter out those that don't have the derivatives we want to process.
		 */
        from("direct:filterByDsid").routeId("UmlDerivativeFilterByDSID")
            .log(DEBUG, "in direct:filterByDsid")
                .log(DEBUG, "Message is ${body}")
                .filter(new ExpressionDefinition() {

                    @PropertyInject(value = "islandora.process_dsids")
                    protected String process_dsid;

                    @Override
                    public boolean matches(Exchange exchange) {

                    String json = (String) exchange.getProperty("objectJson");
                        ReadContext ctx = JsonPath.parse(json);
                        Set<String> receivedDSIDs = ctx.read("$.derivative_info[].destination_dsid");
                        @SuppressWarnings("serial")
                    Set<String> validDSIDs = new HashSet<String>() {

                        {
                            Arrays.asList(process_dsid.split(","));
                        }
                    };
                        validDSIDs.retainAll(receivedDSIDs);
                        if (validDSIDs.size() > 0) {
                            Map<String, Object> headers = exchange.getOut().getHeaders();
                            headers.remove("X-Islandora-Process-Dsids");
                            headers.put("X-Islandora-Process-Dsids", validDSIDs
                                    .stream().collect(Collectors.joining(",")));
                        }
                        return (validDSIDs.size() == 0);
                    }

            }).setBody(exchangeProperty("objectJson"));

        /**
         * Compare the content-models from Islandora to the configuration set.
         * Filter out those that don't have the content-models we want to process.
         */
        from("direct:filterByContentType")
                .routeId("UmlDerivativeFilterByContentType")
            .log(DEBUG, "in direct:filterByContentType")
                .log(DEBUG, "Message is ${body}")
                .filter(new ExpressionDefinition() {

                    @PropertyInject(value = "islandora.process_contentTypes")
                    protected String process_contentTypes;

                    @Override
                    public boolean matches(Exchange exchange) {

                    String json = (String) exchange.getProperty("objectJson");
                    ReadContext ctx = JsonPath.parse(json);
                    Set<String> objectTypes = ctx.read("$.object_info.models");
                        Set<String> validTypes = new HashSet<String>(
                                Arrays.asList(process_contentTypes.split(",")));
                        validTypes.retainAll(objectTypes);
                        return (validTypes.size() == 0);
                    }
                });
    }


}
