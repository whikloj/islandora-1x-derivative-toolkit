package ca.umanitoba.dam.islandora.derivativetoolkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import picocli.CommandLine;

/**
 * Provides the current version to picocli
 * @author whikloj
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    /**
     * Name of the file to locate the version in.
     */
    private static final String VERSION_FILENAME = "toolkit.properties";

    @Override
    public String[] getVersion() throws Exception {
        final var filestream = getClass().getClassLoader().getResourceAsStream(VERSION_FILENAME);
        final String version = new BufferedReader(
                new InputStreamReader(filestream, StandardCharsets.UTF_8))
                .lines()
                .filter(a -> a.startsWith("version"))
                .map(a -> a.split("=")[1])
                .map(String::trim)
                .findAny().orElse("0");
        return new String[] {version};
    }
}