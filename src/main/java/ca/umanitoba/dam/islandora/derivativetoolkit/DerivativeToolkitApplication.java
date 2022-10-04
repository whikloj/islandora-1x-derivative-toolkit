package ca.umanitoba.dam.islandora.derivativetoolkit;

import static ca.umanitoba.dam.islandora.derivativetoolkit.DefaultConfig.TOOLKIT_CONFIG_PROPERTY;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;

import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DerivativeToolkitApplication {

	private static final Logger LOGGER = getLogger(DerivativeToolkitApplication.class);

	public static void main(String[] args) {
		final String prop = System.getProperty(TOOLKIT_CONFIG_PROPERTY, null);
		if (prop == null) {
			System.out.println("You need to specify the location of the configuration file with -Dfc3indexer.config" +
					".file=");
			return;
		} else {
			final File propFile = new File(prop);
			if (!(propFile.exists() || propFile.canRead())) {
				System.out.println("Property file " + prop + " is not a readable file.");
				return;
			}
		}
		SpringApplication.run(DerivativeToolkitApplication.class, args);
	}
}
