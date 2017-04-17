package ca.umanitoba.dam.islandora.derivatives.gatekeeper;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.BeanInject;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

import ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.ObjectInformationInterface;

public class ImageRoutes extends RouteBuilder {

    // Logger
    private static final Logger LOGGER = getLogger(ImageRoutes.class);

    @BeanInject("ObjectInfo")
    ObjectInformationInterface objBean;

    @PropertyInject("gatekeeper.process_contentTypes")
    protected String process_contentTypes;

    @PropertyInject("gatekeeper.process_dsids")
    protected String process_dsid;

    @PropertyInject("output.queue")
    protected String outputqueue;

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void configure() throws Exception {

        LOGGER.warn("output queue is ({})", outputqueue);



		/**
		 * Input queue and main route.
		 */
		from("{{input.queue}}")
                .routeId("UmlDerivativeMainRouter")
            .startupOrder(15)
                .filter(new ValidHeaderPredicate())
                .to("direct:getObjectInfo")
            .setProperty("dsids", simple("{{gatekeeper.process_dsids}}"))
            .setProperty("types", simple("{{gatekeeper.process_contentTypes}}"))
            .filter(new IslandoraInfoFilter()).to("direct:formatOutput");

		/**
		 * Get the resource's information from Islandora.
		 */
		from("direct:getObjectInfo")
		    .routeId("UmlDerivativeGetInfo")
            .startupOrder(10)
            .log(DEBUG, LOGGER, "in direct:getObjectInfo")
            .bean(objBean, "getInfo");

        /**
         * Take the message (which should be object information) and form a small json message for output.
         */
        from("direct:formatOutput").routeId("UmlDerivativeFormatOutput").process(exchange -> {
                // Transform to a new smaller message for our workers.
                String dsids = exchange.getProperty("dsids", String.class);
                Set<String> process_dsids = new HashSet<String>(Arrays.asList(dsids.split(",")));
                String json = exchange.getIn().getBody(String.class);
                ReadContext ctx = JsonPath.parse(json);

                String pid = ctx.read("$.object_info.pid");
                List<Map<String, Object>> derivative_map = ctx.read("$.derivative_info[*]");

                derivative_map.stream().filter(t -> process_dsids.contains(t.keySet())).forEach(t -> {
                    t.remove("weight");
                    t.remove("function");
                    t.remove("file");
                });

                String map = mapper.writeValueAsString(derivative_map);
                LOGGER.debug("map is ({})", map);
                String outputJson = String.format("{ \"pid\": \"%s\", \"derivatives\" : [ %s ]}", pid, map);
            exchange.getIn().setBody(outputJson);
            exchange.getIn().setHeader(CONTENT_TYPE, "application/json");
        }).to("{{output.queue}}");

		/**
		 * Compare the destination dsids from Islandora to the configuration set.
		 * Filter out those that don't have the derivatives we want to process.
		 */
        // from("direct:filterBy").routeId("UmlDerivativeFilter")
        // .startupOrder(1)
        // .log(DEBUG, "in direct:filterByDsid")

    }


}
