package ca.umanitoba.dam.islandora.derivativetoolkit.gatekeeper;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "gatekeeper.enabled")
public class GatekeeperProps {

    @Bean
    public RouteBuilder gatekeeper() {
        return new GatekeeperRoutes();
    }

    @Bean(name = "staticStore")
    public StaticMap staticMap() {
        return new StaticMap();
    }
}
