package org.cpicpgx;

import org.apache.commons.cli.*;
import org.cpicpgx.exporter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.IllegalPathStateException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class represents an archive of data file artifacts generated from the CPIC database. This will write to a 
 * specified directory with subdirectories for the different types of data. This relies on {@link BaseExporter} classes 
 * that will write out batches of files.
 * 
 * This will write to a directory with a pre-determined name that uses the current date in the name.
 *
 * @author Ryan Whaley
 */
public class DataArtifactArchive {
  
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final SimpleDateFormat sf_dateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private static final String sf_dirNamePattern = "cpic_information_%s";
  private static final String sf_geneDirPattern = sf_dirNamePattern + "/genes";
  private static final String sf_drugDirPattern = sf_dirNamePattern + "/drugs";
  
  private Path m_baseDirectory;
  private Path m_geneCollectionDirectory;
  private Path m_drugCollectionDirectory;

  public static void main(String[] args) {
    try {
      DataArtifactArchive dataArtifactArchive = new DataArtifactArchive();
      dataArtifactArchive.parseArgs(args);
      dataArtifactArchive.write();
    } catch (Exception e) {
      sf_logger.error("Error making gene data artifact", e);
    }
  }
  
  private void parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    options.addOption("d", true,"path to directory to write files to");
    CommandLineParser clParser = new DefaultParser();
    CommandLine cli = clParser.parse(options, args);

    m_baseDirectory = Paths.get(cli.getOptionValue("d"));
  }
  
  private void write() {
    if (m_baseDirectory == null) {
      throw new IllegalStateException("No path to directory specified");
    }
    if (!m_baseDirectory.toFile().exists() || !m_baseDirectory.toFile().isDirectory()) {
      throw new IllegalPathStateException("Not a directory: " + m_baseDirectory);
    }
    
    m_geneCollectionDirectory = getDirectoryPath(sf_geneDirPattern);
    List<BaseExporter> exporters = new ArrayList<>();
    exporters.add(new AlleleDefinitionExporter());
    exporters.add(new AlleleFunctionalityReferenceExporter());
    exporters.add(new DiplotypePhenotypeExporter());
    exporters.add(new GeneResourceExporter());

    exporters.forEach(e -> {
      e.setDirectory(m_geneCollectionDirectory);
      try {
        e.export();
      } catch (Exception ex) {
        throw new RuntimeException("Error exporting " + e.getClass().getSimpleName(), ex);
      }
    });

    m_drugCollectionDirectory = getDirectoryPath(sf_drugDirPattern);
    List<BaseExporter> drugExporters = new ArrayList<>();
    drugExporters.add(new RecommendationExporter());
    drugExporters.add(new TestAlertExporter());

    drugExporters.forEach(e -> {
      e.setDirectory(m_drugCollectionDirectory);
      try {
        e.export();
      } catch (Exception ex) {
        throw new RuntimeException("Error exporting " + e.getClass().getSimpleName(), ex);
      }
    });
  }
  
  private Path getDirectoryPath(String filePath) {
    String dateLabel = sf_dateFormat.format(new Date());
    Path dir = m_baseDirectory.resolve(String.format(filePath, dateLabel));
    if (dir.toFile().mkdirs()) {
      sf_logger.info("Created new directory {}", dir);
    } else {
      sf_logger.info("Using existing directory {}", dir);
    }
    return dir;
  }
}