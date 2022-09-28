package ca.umanitoba.dam.islandora.derivativetoolkit.worker;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
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
@SpringBootTest(properties = {"worker.enabled=true", "worker.jms.prefetchSize=0", "fedora.authUsername=",
        "fedora.authPassword=", "fedora.url=http://localhost:8080/fedora", "worker.temporary.directory=/tmp",
        "worker.tesseract.path=/fake/tesseract", "worker.convert.path=/fake/convert",
        "worker.input.queue=direct:start", "error.maxRedeliveries=1",
        "camel.component.activemq.autoStartup=false"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestWorkerRoutes {

    private static Logger LOGGER = getLogger(TestWorkerRoutes.class);

    @EndpointInject(value = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(value = "direct:start")
    protected ProducerTemplate template;

    @Autowired
    CamelContext context;

    @Test
    public void testMainDoBoth() throws Exception {
        final String route = "UmlDerivativeWorkerMain";

        AdviceWith.adviceWith(context, route, a -> {
                    a.replaceFromWith("direct:start");
                    a.mockEndpointsAndSkip("direct:getSourceFile");
                    a.mockEndpointsAndSkip("direct:generate*");
                }
        );

        context.start();

        final String bodyJson = IOUtils.toString(loadResourceAsStream("worker/test_hocr_ocr_input.json"), UTF_8);

        final MockEndpoint generateOCR = (MockEndpoint) context.getEndpoint("mock:direct:generateOCR");
        generateOCR.expectedMessageCount(0);
        final MockEndpoint generateHOCR = (MockEndpoint) context.getEndpoint("mock:direct:generateHOCR");
        generateHOCR.expectedMessageCount(0);
        final MockEndpoint generateBoth = (MockEndpoint) context.getEndpoint("mock:direct:generateBoth");
        generateBoth.expectedMessageCount(1);

        template.sendBody(bodyJson);

        generateOCR.assertIsSatisfied();
        generateHOCR.assertIsSatisfied();
        generateBoth.assertIsSatisfied();
    }

    @Test
    public void testMainHocrOnly() throws Exception {
        final String route = "UmlDerivativeWorkerMain";

        AdviceWith.adviceWith(context, route, a -> {
                    a.replaceFromWith("direct:start");
                    a.mockEndpointsAndSkip("direct:getSourceFile");
                    a.mockEndpointsAndSkip("direct:generate*");

                }
        );

        context.start();

        final String bodyJson = IOUtils.toString(loadResourceAsStream("worker/test_hocr_input.json"), UTF_8);

        final MockEndpoint generateOCR = (MockEndpoint) context.getEndpoint("mock:direct:generateOCR");
        generateOCR.expectedMessageCount(0);
        final MockEndpoint generateHOCR = (MockEndpoint) context.getEndpoint("mock:direct:generateHOCR");
        generateHOCR.expectedMessageCount(1);
        final MockEndpoint generateBoth = (MockEndpoint) context.getEndpoint("mock:direct:generateBoth");
        generateBoth.expectedMessageCount(0);

        template.sendBody(bodyJson);

        generateOCR.assertIsSatisfied();
        generateHOCR.assertIsSatisfied();
        generateBoth.assertIsSatisfied();
    }

    @Test
    public void testMainInvalidDsid() throws Exception {
        final String route = "UmlDerivativeWorkerMain";

        AdviceWith.adviceWith(context, route, a -> {
                    a.replaceFromWith("direct:start");
                    a.mockEndpointsAndSkip("direct:getSourceFile");
                    a.mockEndpointsAndSkip("direct:generate*");

                }
        );

        context.start();

        final String bodyJson = IOUtils.toString(loadResourceAsStream("worker/test_TN_input.json"), UTF_8);

        final MockEndpoint generateOCR = (MockEndpoint) context.getEndpoint("mock:direct:generateOCR");
        generateOCR.expectedMessageCount(0);
        final MockEndpoint generateHOCR = (MockEndpoint) context.getEndpoint("mock:direct:generateHOCR");
        generateHOCR.expectedMessageCount(0);
        final MockEndpoint generateBoth = (MockEndpoint) context.getEndpoint("mock:direct:generateBoth");
        generateBoth.expectedMessageCount(0);

        template.sendBody(bodyJson);

        generateOCR.assertIsSatisfied();
        generateHOCR.assertIsSatisfied();
        generateBoth.assertIsSatisfied();
    }
}
