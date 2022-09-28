package ca.umanitoba.dam.islandora.derivativetoolkit;

import static org.slf4j.LoggerFactory.getLogger;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import picocli.CommandLine;

@SpringBootApplication
public class DerivativeToolkitApplication {

	private static final Logger LOGGER = getLogger(DerivativeToolkitApplication.class);

	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(DerivativeToolkitApplication.class, args)));
	}
}
