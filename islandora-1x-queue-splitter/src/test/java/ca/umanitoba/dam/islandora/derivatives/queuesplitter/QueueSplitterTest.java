package ca.umanitoba.dam.islandora.derivatives.queuesplitter;

import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;


public class QueueSplitterTest extends CamelBlueprintTestSupport {

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

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
        return "OSGI-INF/blueprint/blueprint-test.xml";
    }

    @Test
    public void testSend() throws Exception {
        final String route = "UmlDerivativeQueueSplitter";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

                @Override
                public void configure() throws Exception {
                        replaceFromWith("direct:start");
                        mockEndpointsAndSkip("direct:outputA");
                        mockEndpointsAndSkip("direct:outputB");
                }
            });

        context.start();

        final String body = IOUtils.toString(loadResourceAsStream("test_body.xml"), "UTF-8");
        final Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("expires", "0");
        headers.put("methodName", "ingest");
        headers.put("timestamp", "1489441748942");
        headers.put("destination", "/queue/fedora.apim.update");
        headers.put("pid", "islandora:123");
        headers.put("persistent", "true");
        headers.put("priority", "4");
        headers.put("message-id", "ID:localhost-36004-1489440749650-3:1:2:1:37");
        headers.put("subscription", "subscription-id");

        getMockEndpoint("mock:direct:outputA").expectedMessageCount(1);
        getMockEndpoint("mock:direct:outputB").expectedMessageCount(1);

        template.sendBodyAndHeaders(body, headers);

        assertMockEndpointsSatisfied();
    }
}
