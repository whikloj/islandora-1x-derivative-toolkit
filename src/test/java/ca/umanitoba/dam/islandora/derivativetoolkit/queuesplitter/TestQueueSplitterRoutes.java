package ca.umanitoba.dam.islandora.derivativetoolkit.queuesplitter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.camel.util.ObjectHelper.loadResourceAsStream;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@CamelSpringBootTest
@SpringBootTest(properties = {"queuesplitter.enabled=true", "queuesplitter.input_queue=direct:start",
        "queuesplitter.output_queues=direct:outputA,direct:outputB"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestQueueSplitterRoutes {

    @Produce(value = "direct:start")
    protected ProducerTemplate template;

    @Autowired
    CamelContext context;

    @Test
    public void testSend() throws Exception {
        final String route = "UmlDerivativeQueueSplitter";

        AdviceWith.adviceWith(context, route, a -> a.mockEndpointsAndSkip("direct:outputA|direct:outputB"));

        context.start();

        final String body = IOUtils.toString(loadResourceAsStream("queuesplitter/test_body.xml"), UTF_8);
        final Map<String, Object> headers = new HashMap<>();
        headers.put("expires", "0");
        headers.put("methodName", "ingest");
        headers.put("timestamp", "1489441748942");
        headers.put("destination", "/queue/fedora.apim.update");
        headers.put("pid", "islandora:123");
        headers.put("persistent", "true");
        headers.put("priority", "4");
        headers.put("message-id", "ID:localhost-36004-1489440749650-3:1:2:1:37");
        headers.put("subscription", "subscription-id");

        ((MockEndpoint)context.getEndpoint("mock:direct:outputA")).expectedMessageCount(1);
        ((MockEndpoint)context.getEndpoint("mock:direct:outputB")).expectedMessageCount(1);

        template.sendBodyAndHeaders(body, headers);

        MockEndpoint.assertIsSatisfied(context);
    }

}
