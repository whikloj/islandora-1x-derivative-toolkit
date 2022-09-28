package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {"worker.enabled=true", "jms.prefetchSize=0", "fedora.authUsername=",
		"fedora.authPassword=", "fedora.url=http://localhost:8080/fedora", "worker.temporary.directory=/tmp",
		"worker.tesseract.path=/fake/tesseract", "worker.convert.path=/fake/convert",
		"worker.input.queue=direct:start", "error.maxRedeliveries=1",
		"camel.component.activemq.autoStartup=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DerivativeToolkitApplicationTestsWorker {

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void workerEnabled() {
		final var beanFactory = applicationContext.getAutowireCapableBeanFactory();
		final List<String> beanNames = List.of("queueSplitter", "gatekeeper");
		int failCounter = 0;
		for (final var beanName : beanNames) {
			try {
				beanFactory.getBean(beanName);
			} catch (final NoSuchBeanDefinitionException e) {
				failCounter += 1;
			}
		}
		assertEquals(2, failCounter);
		beanFactory.getBean("derivativeWorker");
	}
}
