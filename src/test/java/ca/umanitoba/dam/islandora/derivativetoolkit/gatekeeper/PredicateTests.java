package ca.umanitoba.dam.islandora.derivativetoolkit.gatekeeper;

import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PredicateTests extends CamelTestSupport {

    private static Logger LOGGER = getLogger(PredicateTests.class);

    @EndpointInject(value = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(value = "direct:start")
    protected ProducerTemplate template;

    protected static ObjectMapper mapper;

    @BeforeAll
    public static void before() throws Exception {
        mapper = new ObjectMapper();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").filter(new ValidHeaderPredicate())
                        .to("mock:result");
            }
        };
    }


    @Test
    public void testEmptyMessage() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("", "",
                "notMatchedHeaderValue");

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testIngest() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/ingest";
        resultEndpoint.expectedMessageCount(1);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
    }

    public void testAddOBJ() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/addOBJ";
        resultEndpoint.expectedMessageCount(1);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testAddTN() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/addTN";
        resultEndpoint.expectedMessageCount(0);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testModifyMODS() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/modifyMODS";

        resultEndpoint.expectedMessageCount(0);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testModifyHOCR() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/modifyHOCR";

        resultEndpoint.expectedMessageCount(0);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testModifyOCR() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/modifyOCR";

        resultEndpoint.expectedMessageCount(0);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testModifyOBJ() throws Exception {
        String resourceDir = "gatekeeper/fedora3-events/modifyOBJ";
        resultEndpoint.expectedMessageCount(1);

        String expectedBody = getBody(resourceDir);

        template.sendBodyAndHeaders(expectedBody,
                getHeaders(resourceDir));

        resultEndpoint.assertIsSatisfied();
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
            return IOUtils.toString(loadResourceAsStream(resourceFile), StandardCharsets.UTF_8);
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
