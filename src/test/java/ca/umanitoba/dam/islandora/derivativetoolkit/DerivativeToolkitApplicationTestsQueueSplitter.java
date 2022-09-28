package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {"queuesplitter.enabled=true", "queuesplitter.input_queue=direct:start",
		"queuesplitter.output_queues=direct:output1,direct:output2"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DerivativeToolkitApplicationTestsQueueSplitter {

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void queueSplitterEnabled() {
		final var beanFactory = applicationContext.getAutowireCapableBeanFactory();
		final List<String> beanNames = List.of("gatekeeper", "derivativeWorker");
		int failCounter = 0;
		for (final var beanName : beanNames) {
			try {
				beanFactory.getBean(beanName);
			} catch (final NoSuchBeanDefinitionException e) {
				failCounter += 1;
			}
		}
		assertEquals(2, failCounter);
		beanFactory.getBean("queueSplitter");
	}
}
