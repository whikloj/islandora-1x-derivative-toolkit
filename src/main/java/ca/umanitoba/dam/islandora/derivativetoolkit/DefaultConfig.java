package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.slf4j.LoggerFactory.getLogger;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.component.jms.JmsConfiguration;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.annotation.Order;

@Configuration
@PropertySources({
    @PropertySource(value = DefaultConfig.TOOLKIT_CONFIG_FILE, ignoreResourceNotFound = true),
    @PropertySource(value = "classpath:application.properties")
})
@ComponentScan("ca.umanitoba.dam.islandora.derivativetoolkit")
@Order(0)
public class DefaultConfig {

    private static final Logger LOGGER = getLogger(DefaultConfig.class);

    public static final String TOOLKIT_CONFIG_PROPERTY = "toolkit.config";

    public static final String TOOLKIT_CONFIG_FILE = "file:${" + TOOLKIT_CONFIG_PROPERTY + "}";

    @Value("${camel.component.activemq.broker-url:}")
    private String brokerUrl;

    @Value("${camel.component.activemq.username:}")
    private String brokerUsername;

    @Value("${camel.component.activemq.password:}")
    private String brokerPassword;

    @Value("${camel.component.activemq.concurrent-consumers:10}")
    private int brokerMaxConnections;

    /**
     * @return JMS Connection factory bean.
     * @throws JMSException on failure to create new connection.
     */
    @Bean
    @ConditionalOnProperty(value = "camel.component.activemq.broker-url")
    public ConnectionFactory jmsConnectionFactory() throws JMSException {
        LOGGER.debug("jmsConnectionFactory: brokerUrl is {}", brokerUrl);
        if (!brokerUrl.isBlank()) {
            final var factory = new ActiveMQConnectionFactory();
            factory.setBrokerURL(brokerUrl);
            LOGGER.debug("jms username is {}", brokerUsername);
            if (!brokerUsername.isBlank() && !brokerPassword.isBlank()) {
                factory.createConnection(brokerUsername, brokerPassword);
            }
            return factory;
        }
        return null;
    }

    /**
     * @param connectionFactory the JMS connection factory.
     * @return A pooled connection factory.
     */
    public PooledConnectionFactory pooledConnectionFactory(final ConnectionFactory connectionFactory) {
        final var pooledConnectionFactory = new PooledConnectionFactory();
        pooledConnectionFactory.setMaxConnections(brokerMaxConnections);
        pooledConnectionFactory.setConnectionFactory(connectionFactory);
        return pooledConnectionFactory;
    }

    /**
     * @param connectionFactory the pooled connection factory.
     * @return the JMS configuration
     */
    public JmsConfiguration jmsConfiguration(final PooledConnectionFactory connectionFactory) {
        final var configuration = new JmsConfiguration();
        configuration.setConnectionFactory(connectionFactory);
        return configuration;
    }

}
