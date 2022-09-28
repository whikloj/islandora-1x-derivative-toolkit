package ca.umanitoba.dam.islandora.derivativetoolkit.worker;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "worker.enabled")
public class WorkerProps {

    @Bean
    public RouteBuilder derivativeWorker() {
        return new WorkerRoutes();
    }
}
