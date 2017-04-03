package ca.umanitoba.dam.islandora.derivatives.gatekeeper;

import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestRoutes extends CamelBlueprintTestSupport {

    private static Logger LOGGER = getLogger(TestRoutes.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    protected static ObjectMapper mapper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mapper = new ObjectMapper();
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-test.xml";
    }

    @Override
    public boolean isUseDebugger() {
        // must enable debugger
        return true;
    }

    @Override
    protected void debugBefore(Exchange exchange, org.apache.camel.Processor processor,
        ProcessorDefinition<?> definition, String id, String label) {
        log.info("Before " + definition + " with body " + exchange.getIn().getBody());
    }

    @Override
    protected void debugAfter(Exchange exchange, org.apache.camel.Processor processor,
        ProcessorDefinition<?> definition, String id, String label, long timeTaken) {
        log.info("After " + definition + " with body " + exchange.getIn().getBody());
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        final Properties props = new Properties();
        props.put("input.queue", "direct:foo");
        props.put("output.queue", "direct:out");
        props.put("islandora.process_dsids", "OCR,HOCR");
        props.put("islandora.process_contentTypes", "islandora:sp_large_image_cmodel");
        return props;
    }


    @Test
    public void testFilterDsidValid() throws Exception {
        final String route = "UmlDerivativeMainRouter";

        context.getRouteDefinition(route).adviceWith(context,
				new AdviceWithRouteBuilder() {

					@Override
					public void configure() throws Exception {
						replaceFromWith("direct:start");
                    mockEndpointsAndSkip("direct:getObjectInfo,direct:filterByContentType");
					}
				});
        context.start();
        // template.start();

        String bodyJson = IOUtils.toString(loadResourceAsStream("rest_responses/large_image_w_OCR.json"), "UTF-8");

        getMockEndpoint("mock:direct:filterByContentType").expectedMessageCount(1);
        resultEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader(bodyJson, "methodName", "ingest");

        assertMockEndpointsSatisfied();
    }

    /**
     * Returns the XML Document body for a Message.
     *
     * @param resourceDir the Path containing the body.xml and headers.json
     * @return the body contents.
     */
    public String getBody(String resourceDir) {
        String resourceFile = Paths.get(resourceDir, "body.xml").toString();
        try {
            return IOUtils.toString(loadResourceAsStream(resourceFile), "UTF-8");
        } catch (IOException e) {
            LOGGER.error("Could not load file ({})", resourceFile);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the Headers for a Message.
     *
     * @param resourceDir the Path containing the body.xml and headers.json
     * @return map of header names to header objects.
     */
    public Map<String, Object> getHeaders(String resourceDir) {

        String resourceFile = Paths.get(resourceDir, "headers.json").toString();

        try {
            String jsonData = IOUtils.toString(loadResourceAsStream(resourceFile), "UTF-8");
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = mapper.readValue(jsonData, Map.class);
            return headers;
        } catch (JsonParseException e) {
            System.err.println("Parse error with string");
            e.printStackTrace();
        } catch (JsonMappingException e) {
            System.err.println("Mapping exception with string");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException with string");
            e.printStackTrace();
        }
        return null;
    }

}
