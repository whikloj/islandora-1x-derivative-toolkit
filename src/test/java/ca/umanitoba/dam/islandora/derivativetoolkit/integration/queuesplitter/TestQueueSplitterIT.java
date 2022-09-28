package ca.umanitoba.dam.islandora.derivativetoolkit.integration.queuesplitter;

import static javax.jms.Session.AUTO_ACKNOWLEDGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import javax.jms.Message;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.Configuration;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

@CamelSpringBootTest
@SpringBootTest(classes = TestQueueSplitterIT.testConfig.class)
public class TestQueueSplitterIT {

    private static ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    public static void setUp(){
        connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    }

    @Test
    public void testRoutes() throws Exception {
        final var queueConnection = connectionFactory.createQueueConnection();
        queueConnection.start();
        final var queueSession = queueConnection.createQueueSession(false, AUTO_ACKNOWLEDGE);
        final var inputQueue = queueSession.createQueue("input");
        final var sender = queueSession.createSender(inputQueue);
        for (int x = 1; x < 6; x += 1) {
            final var message = queueSession.createTextMessage();
            message.setText("{\"id\":" + x + "}");
            sender.send(message);
        }
        sender.close();
        queueSession.close();

        final var readSession = queueConnection.createQueueSession(false, AUTO_ACKNOWLEDGE);
        final var outputAQueue = readSession.createQueue("outA");
        final var outputBQueue = readSession.createQueue("outB");
        final var outputCQueue = readSession.createQueue("outC");

        final var queues = List.of(outputAQueue, outputBQueue, outputCQueue);
        for (final var q : queues) {
            final var receiver = readSession.createConsumer(q);
            for (int x = 1; x < 6; x += 1) {
                final Message mesg = receiver.receive(3000L);

                assertNotNull(mesg);
                assertEquals("{\"id\":" + x + "}", ((TextMessage)mesg).getText());
            }
            final var noMesg = receiver.receive(1000L);
            assertNull(noMesg);
            receiver.close();
        }
        readSession.close();
        queueConnection.stop();
        queueConnection.close();
    }

    @Configuration
    @PropertySource(value = "classpath:testQueueSplitter.properties")
    @ComponentScan("ca.umanitoba.dam.islandora.derivativetoolkit")
    static class testConfig {

    }
}
