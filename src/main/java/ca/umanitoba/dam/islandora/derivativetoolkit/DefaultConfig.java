package ca.umanitoba.dam.islandora.derivativetoolkit;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = DefaultConfig.TOOLKIT_CONFIG_FILE, ignoreResourceNotFound = true)
public class DefaultConfig {

    public static final String TOOLKIT_CONFIG_PROPERTY = "toolkit.config";

    public static final String TOOLKIT_CONFIG_FILE = "file:${" + TOOLKIT_CONFIG_PROPERTY + "}";

}
