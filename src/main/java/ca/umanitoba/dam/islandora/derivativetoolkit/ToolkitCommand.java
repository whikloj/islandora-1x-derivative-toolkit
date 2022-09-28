package ca.umanitoba.dam.islandora.derivativetoolkit;

import static ca.umanitoba.dam.islandora.derivativetoolkit.DefaultConfig.TOOLKIT_CONFIG_PROPERTY;
import static org.slf4j.LoggerFactory.getLogger;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

@CommandLine.Command(name = "islandora-derivative-toolkit", mixinStandardHelpOptions = true, sortOptions = false,
        versionProvider = VersionProvider.class)
@Component
public class ToolkitCommand implements Callable<Integer> {

    private final Logger LOGGER = getLogger(ToolkitCommand.class);

    @Autowired
    AnnotationConfigApplicationContext applicationContext;

    /**
     * Configuration file.
     */
    @CommandLine.Option(names = {"--config", "-c"}, required = true, order = 1,
            description = "The path to the configuration file")
    private Path configurationFilePath;

    @Override
    public Integer call() {
        if (configurationFilePath != null) {
            System.setProperty(TOOLKIT_CONFIG_PROPERTY, configurationFilePath.toFile().getAbsolutePath());
        }
        while (applicationContext.isRunning()) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                throw new RuntimeException("This should never happen");
            }
        }
        return 0;
    }
}
