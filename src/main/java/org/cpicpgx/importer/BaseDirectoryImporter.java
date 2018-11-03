package org.cpicpgx.importer;

import org.apache.commons.cli.*;
import org.cpicpgx.util.WorkbookWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Abstract class for classes that want to crawl all files in a directory and do something with them
 *
 * @author Ryan Whaley
 */
abstract class BaseDirectoryImporter {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  static final String EXCEL_EXTENSION = ".xlsx";
  static final String CSV_EXTENSION = ".csv";
  
  private Path directory;

  /**
   * Parse arguments from the command line.
   * 
   * Expect a "d" argument that gives the directory to crawl through. Must exist and must contain files.
   * @param args an array of command line arguments
   * @throws ParseException can occur from bad argument syntax
   */
  void parseArgs(String [] args) throws ParseException {
    Options options = new Options();
    options.addOption("d", true,"directory containing files to process (*.xlsx)");
    CommandLineParser clParser = new DefaultParser();
    CommandLine cli = clParser.parse(options, args);

    String directoryPath = cli.getOptionValue("d");
    setDirectory(directoryPath);
  }

  /**
   * Gets the String file extension to look for in the given directory. This should be something like ".xlsx" or ".csv".
   * @return a file extension to filter for
   */
  abstract String getFileExtensionToProcess();

  /**
   * Run the importer. Requires the "directory" to be set
   */
  public void execute() {
    Arrays.stream(Objects.requireNonNull(this.directory.toFile().listFiles()))
        .filter(f -> f.getName().toLowerCase().endsWith(getFileExtensionToProcess().toLowerCase()) && !f.getName().startsWith("~$"))
        .forEach(getFileProcessor());
  }

  /**
   * A {@link Consumer} that will take a {@link File} objects and then run {@link BaseDirectoryImporter#processWorkbook(WorkbookWrapper)}
   * on them. You either need to override {@link BaseDirectoryImporter#processWorkbook(WorkbookWrapper)} or override 
   * this method to do something different with the {@link File} 
   * @return a Consumer of File objects
   */
  Consumer<File> getFileProcessor() {
    return (File file) -> {
      sf_logger.info("Reading {}", file);

      try (InputStream in = Files.newInputStream(file.toPath())) {
        WorkbookWrapper workbook = new WorkbookWrapper(in);
        processWorkbook(workbook);
      } catch (Exception ex) {
        throw new RuntimeException("Error processing file " + file, ex);
      }
    };
  }

  /**
   * This method is meant to pass in a parsed {@link WorkbookWrapper} object and then do something with it. This must 
   * be overriden and will throw an error if it is not.
   * @param workbook a workbook pulled from the specified directory
   * @throws Exception will be thrown if this method is not overridden
   */
  void processWorkbook(WorkbookWrapper workbook) throws Exception {
    throw new RuntimeException("Workbook processor not implemented in this importer");
  }

  /**
   * Sets the directory to work with. Will fail if the directory doesn't exist or doesn't have files
   * @param directory a directory path
   */
  void setDirectory(String directory) {
    if (directory == null) {
      throw new IllegalArgumentException("Need a directory");
    }
    
    setDirectory(Paths.get(directory));
  }

  /**
   * Sets a directory to search for files to process. The path must exist and be for a directory (not a file)
   * @param directory a directory in the filesystem
   */
  void setDirectory(Path directory) {
    this.directory = directory;

    if (!this.directory.toFile().exists()) {
      throw new IllegalArgumentException("Directory doesn't exist " + this.directory);
    }
    if (!this.directory.toFile().isDirectory()) {
      throw new IllegalArgumentException("Path is not a directory " + this.directory);
    }
    if (this.directory.toFile().listFiles() == null) {
      throw new IllegalArgumentException("Directory is empty " + this.directory);
    }
  }
}