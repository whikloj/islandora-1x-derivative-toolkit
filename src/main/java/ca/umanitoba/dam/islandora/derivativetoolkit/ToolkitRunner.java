package ca.umanitoba.dam.islandora.derivativetoolkit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

@Component
public class ToolkitRunner implements CommandLineRunner, ExitCodeGenerator {

    private final ToolkitCommand myCommand;

    private final CommandLine.IFactory iFactory;

    private int exitCode;

    public ToolkitRunner(final ToolkitCommand command, final CommandLine.IFactory factory) {
        myCommand = command;
        iFactory = factory;
    }

    @Override
    public void run(final String... args) throws Exception {
        exitCode = new CommandLine(myCommand, iFactory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
