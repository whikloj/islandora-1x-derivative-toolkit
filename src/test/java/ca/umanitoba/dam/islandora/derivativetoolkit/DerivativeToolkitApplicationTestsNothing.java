package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DerivativeToolkitApplicationTestsNothing {

	@Autowired
	ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		final var beanFactory = applicationContext.getAutowireCapableBeanFactory();
		final List<String> beanNames = List.of("queueSplitter", "gatekeeper", "derivativeWorker");
		int failCounter = 0;
		for (final var beanName : beanNames) {
			try {
				beanFactory.getBean(beanName);
			} catch (final NoSuchBeanDefinitionException e) {
				failCounter += 1;
			}
		}
		assertEquals(3, failCounter);
	}

}
