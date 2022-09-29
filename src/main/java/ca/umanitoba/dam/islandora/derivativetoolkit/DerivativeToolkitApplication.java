package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DerivativeToolkitApplication {

	private static final Logger LOGGER = getLogger(DerivativeToolkitApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(DerivativeToolkitApplication.class, args);
	}
}
