package ca.umanitoba.dam.islandora.derivatives.worker;

import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;


public class WorkerTests extends CamelBlueprintTestSupport {

    private static Logger LOGGER = getLogger(WorkerTests.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

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
    public void testMainDoBoth() throws Exception {
        final String route = "UmlDerivativeWorkerMain";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

                @Override
                public void configure() throws Exception {
                    replaceFromWith("direct:start");
                    mockEndpointsAndSkip("direct:getSourceFile");
                        mockEndpointsAndSkip("direct:generateBoth");
                        mockEndpointsAndSkip("direct:generateOCR");
                        mockEndpointsAndSkip("direct:generateHOCR");
                }
            });

        context.start();
        LOGGER.debug("Status is {}", context.getStatus());

        final String bodyJson = IOUtils.toString(loadResourceAsStream("test_hocr_ocr_input.json"), "UTF-8");

        getMockEndpoint("mock:direct:generateOCR").expectedMessageCount(0);
        getMockEndpoint("mock:direct:generateHOCR").expectedMessageCount(0);
        getMockEndpoint("mock:direct:generateBoth").expectedMessageCount(1);

        template.sendBody(bodyJson);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMainHocrOnly() throws Exception {
        final String route = "UmlDerivativeWorkerMain";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

                @Override
                public void configure() throws Exception {
                    replaceFromWith("direct:start");
                    mockEndpointsAndSkip("direct:getSourceFile");
                    mockEndpointsAndSkip("direct:generate*");

                }
            });

        context.start();
        LOGGER.debug("Status is {}", context.getStatus());

        final String bodyJson = IOUtils.toString(loadResourceAsStream("test_hocr_input.json"), "UTF-8");

        getMockEndpoint("mock:direct:generateOCR").expectedMessageCount(0);
        getMockEndpoint("mock:direct:generateHOCR").expectedMessageCount(1);
        getMockEndpoint("mock:direct:generateBoth").expectedMessageCount(0);

        template.sendBody(bodyJson);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMainInvalidDsid() throws Exception {
        final String route = "UmlDerivativeWorkerMain";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

                @Override
                public void configure() throws Exception {
                    replaceFromWith("direct:start");
                    mockEndpointsAndSkip("direct:getSourceFile");
                    mockEndpointsAndSkip("direct:generate*");

                }
            });

        context.start();
        LOGGER.debug("Status is {}", context.getStatus());

        final String bodyJson = IOUtils.toString(loadResourceAsStream("test_TN_input.json"), "UTF-8");

        getMockEndpoint("mock:direct:generateOCR").expectedMessageCount(0);
        getMockEndpoint("mock:direct:generateHOCR").expectedMessageCount(0);
        getMockEndpoint("mock:direct:generateBoth").expectedMessageCount(0);

        template.sendBody(bodyJson);

        assertMockEndpointsSatisfied();
    }

}
