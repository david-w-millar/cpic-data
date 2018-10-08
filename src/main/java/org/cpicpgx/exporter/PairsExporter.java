package org.cpicpgx.exporter;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.cpicpgx.db.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Class to handle exporting a CPIC report out to filesystem.
 *
 * @author Ryan Whaley
 */
public class PairsExporter {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String sf_defaultFileName = "cpicPairs.csv";
  private static final String sf_pairQuery = "select " +
      "hgncid as \"Gene\", " +
      "d2.name as \"Drug\", " +
      "g.url as \"Guideline URL\", " +
      "level as \"CPIC Level\", " +
      "pgkbcalevel as \"PharmGKB Level of Evidence\", " +
      "pgxtesting as \"PGx Level of Evidence\", " +
      "array_to_string(citations, ';') as \"CPIC Publications (PMID)\" " +
      "from pair p " +
      "join drug d2 on p.drugid = d2.drugid " +
      "left join guideline g on p.guidelineid = g.id " +
      "order by p.level, d2.name";

  private Path directory;

  public static void main(String[] args) {
    try {
      Options options = new Options();
      options.addOption("d", true,"directory to write the cpic pairs to");
      CommandLineParser clParser = new DefaultParser();
      CommandLine cli = clParser.parse(options, args);

      PairsExporter export = new PairsExporter(Paths.get(cli.getOptionValue("d")));
      export.execute();
    } catch (ParseException e) {
      sf_logger.error("Couldn't parse command", e);
    }
  }
  
  private PairsExporter(Path directoryPath) {
    if (directoryPath == null) {
      throw new IllegalArgumentException("No directory given");
    }

    if (!directoryPath.toFile().exists()) {
      throw new IllegalArgumentException("Directory doesn't exist " + directoryPath);
    }
    if (!directoryPath.toFile().isDirectory()) {
      throw new IllegalArgumentException("Path is not a directory " + directoryPath);
    }

    directory = directoryPath;
  }
  
  private void execute() {
    sf_logger.debug("Will write to " + this.directory);
    
    Path pairsFile = this.directory.resolve(sf_defaultFileName);
    try (Connection conn = ConnectionFactory.newConnection()) {
      try (
          FileWriter fw = new FileWriter(pairsFile.toFile());
          PreparedStatement stmt = conn.prepareStatement(sf_pairQuery)
      ) {
        CSVPrinter csvPrinter = CSVFormat.DEFAULT.withHeader(stmt.getMetaData()).print(fw);
        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            csvPrinter.printRecord(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7)
            );
          }
        }
      }
    } catch (IOException e) {
      sf_logger.error("Couldn't write to filesystem", e);
    } catch (SQLException e) {
      sf_logger.error("Couldn't query the DB", e);
    }
    
    sf_logger.info("Wrote pairs to: {}", pairsFile);
  }
}