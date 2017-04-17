package ca.umanitoba.dam.islandora.derivatives.gatekeeper;

import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;

public class TestRoutes extends CamelBlueprintTestSupport {

    private static Logger LOGGER = getLogger(TestRoutes.class);

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
    public void testFilterValid() throws Exception {
        final String route = "UmlDerivativeMainRouter";

        context.getRouteDefinition(route).adviceWith(context,
				new AdviceWithRouteBuilder() {

					@Override
					public void configure() throws Exception {
						replaceFromWith("direct:start");
                    mockEndpointsAndSkip("(direct:getObjectInfo|direct:formatOutput)");

					}
				});

        context.start();

        String bodyJson = IOUtils.toString(loadResourceAsStream("rest_responses/large_image_w_OCR.json"), "UTF-8");

        getMockEndpoint("mock:direct:getObjectInfo").expectedMessageCount(1);
        getMockEndpoint("mock:direct:formatOutput").expectedMessageCount(1);

        template.sendBodyAndHeader(bodyJson, "methodName", "ingest");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFilterInvalidDsid() throws Exception {
        final String route = "UmlDerivativeMainRouter";

        context.getRouteDefinition(route).adviceWith(context, new AdviceWithRouteBuilder() {

            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("(direct:getObjectInfo|direct:formatOutput)");

            }
        });

        context.start();

        String bodyJson = IOUtils.toString(loadResourceAsStream("rest_responses/large_image_wo_OCR.json"), "UTF-8");

        getMockEndpoint("mock:direct:getObjectInfo").expectedMessageCount(1);
        getMockEndpoint("mock:direct:formatOutput").expectedMessageCount(0);

        template.sendBodyAndHeader(bodyJson, "methodName", "ingest");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFilterInvalidType() throws Exception {
        final String route = "UmlDerivativeMainRouter";

        context.getRouteDefinition(route).adviceWith(context, new AdviceWithRouteBuilder() {

            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("(direct:getObjectInfo|direct:formatOutput)");

            }
        });

        context.start();

        String bodyJson = IOUtils.toString(loadResourceAsStream("rest_responses/full_info_compound.json"), "UTF-8");

        getMockEndpoint("mock:direct:getObjectInfo").expectedMessageCount(1);
        getMockEndpoint("mock:direct:formatOutput").expectedMessageCount(0);

        template.sendBodyAndHeader(bodyJson, "methodName", "ingest");

        assertMockEndpointsSatisfied();
    }

}
