package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {"gatekeeper.enabled=true", "jms.prefetchSize=0", "fedora.authUsername=",
		"fedora.authPassword=", "fedora.url=http://localhost:8080/fedora", "temporary.directory=/tmp",
		"error.maxRedeliveries=1", "gatekeeper.process_dsids=OCR",
		"gatekeeper.process_contentTypes=islandora:sp_large_image_cmodel",
		"gatekeeper.rest.port_number=8181", "gatekeeper.rest.path=/gatekeeperEnabledTest",
		"gatekeeper.input.queue=direct:start", "gatekeeper.output.queue=direct:output",
		"gatekeeper.dead.queue=direct:dead",
		"islandora.hostname=http://localhost:8111", "islandora.basepath=/islandora", "islandora.rest.infoUri=/info",
		"islandora.username=testUser", "islandora.password=testPass", "islandora.login_service=/login",
		"camel.component.activemq.autoStartup=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DerivativeToolkitApplicationTestsGatekeeper {

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void gatekeeperEnabled() {
		final var beanFactory = applicationContext.getAutowireCapableBeanFactory();
		final List<String> beanNames = List.of("queueSplitter", "derivativeWorker");
		int failCounter = 0;
		for (final var beanName : beanNames) {
			try {
				beanFactory.getBean(beanName);
			} catch (final NoSuchBeanDefinitionException e) {
				failCounter += 1;
			}
		}
		assertEquals(2, failCounter);
		beanFactory.getBean("gatekeeper");
	}
}
