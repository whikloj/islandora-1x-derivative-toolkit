package ca.umanitoba.dam.islandora.derivativetoolkit.gatekeeper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@CamelSpringBootTest
@SpringBootTest(properties = {"gatekeeper.enabled=true", "fedora.authUsername=",
        "fedora.authPassword=", "fedora.url=http://localhost:8080/fedora",
        "error.maxRedeliveries=1", "gatekeeper.process_dsids=OCR,HOCR",
        "gatekeeper.process_contentTypes=islandora:sp_large_image_cmodel",
        "gatekeeper.rest.port_number=8282", "gatekeeper.rest.path=/gatekeeper",
        "gatekeeper.input.queue=direct:start", "gatekeeper.output.queue=direct:end",
        "gatekeeper.dead.queue=direct:dead",
        "islandora.hostname=http://localhost:8111", "islandora.basepath=/islandora", "islandora.rest.infoUri=/info",
        "islandora.username=testUser", "islandora.password=testPass", "islandora.login_service=/login",
        "camel.component.activemq.autoStartup=false", // Don't start ActiveMQ component.
})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestGatekeeperRoutes {

    private static Logger LOGGER = getLogger(TestGatekeeperRoutes.class);

    @Produce(value = "direct:start")
    protected ProducerTemplate template;

    @Autowired
    CamelContext context;

    public void adviceRoute(final String route) throws Exception {
        AdviceWith.adviceWith(context, route, a -> {
            a.mockEndpointsAndSkip("direct:*");
            a.mockEndpointsAndSkip("http:*");
        });
    }

    @Test
    public void testFilter() throws Exception {
        adviceRoute("UmlDerivativeMainRouter");

        final String hasOcr = IOUtils.toString(loadResourceAsStream(
                "gatekeeper/rest_responses/large_image_w_OCR.json"), UTF_8);
        final String noMatchingDsid = IOUtils.toString(loadResourceAsStream(
                        "gatekeeper/rest_responses/large_image_wo_OCR.json"),
                UTF_8);
        final String invalidType = IOUtils.toString(loadResourceAsStream(
                        "gatekeeper/rest_responses/full_info_compound.json"),
                UTF_8);

        context.start();

        ((MockEndpoint)context.getEndpoint("mock:direct:getObjectInfo")).expectedMessageCount(3);
        ((MockEndpoint)context.getEndpoint("mock:direct:formatOutput")).expectedMessageCount(1);

        template.sendBodyAndHeader(hasOcr, "methodName", "ingest");
        template.sendBodyAndHeader(noMatchingDsid, "methodName", "ingest");
        template.sendBodyAndHeader(invalidType, "methodName", "ingest");

        MockEndpoint.assertIsSatisfied(context);
    }

    // Multiple consumers error.
    @Test
    public void testFormatOutput() throws Exception {

        AdviceWith.adviceWith(context, "UmlDerivativeFormatOutput", a -> a.mockEndpointsAndSkip("direct:end"));

        context.start();

        final String bodyJson =
                IOUtils.toString(loadResourceAsStream(
                        "gatekeeper/rest_responses/format_output_input.json"), UTF_8);
        final String expectedJson =
                IOUtils.toString(loadResourceAsStream(
                        "gatekeeper/rest_responses/format_output_output.json"), UTF_8);
        final List<String> expectedList = Arrays.asList(expectedJson);

        ((MockEndpoint) context.getEndpoint("mock:direct:end")).expectedBodiesReceived(expectedList);

        template.sendBody("direct:formatOutput", bodyJson);

        MockEndpoint.assertIsSatisfied(context);
    }
}
