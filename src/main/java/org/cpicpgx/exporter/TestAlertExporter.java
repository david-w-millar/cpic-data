package org.cpicpgx.exporter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.cpicpgx.db.ConnectionFactory;
import org.cpicpgx.model.EntityType;
import org.cpicpgx.model.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.TreeMap;

/**
 * An exporter for writing test alerts to an excel sheet grouped by drug.
 *
 * @author Ryan Whaley
 */
public class TestAlertExporter extends BaseExporter {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static void main(String[] args) {
    TestAlertExporter exporter = new TestAlertExporter();
    try {
      exporter.parseArgs(args);
      exporter.export();
    } catch (Exception ex) {
      sf_logger.error("Error exporting test alerts", ex);
    }
  }

  public FileType getFileType() {
    return FileType.TEST_ALERT;
  }

  EntityType getEntityCategory() {
    return EntityType.DRUG;
  }

  public void export() throws Exception {
    Gson gson = new Gson();
    //noinspection UnstableApiUsage
    Type stringMapType = new TypeToken<TreeMap<String, String>>(){}.getType();

    try (Connection conn = ConnectionFactory.newConnection();
         PreparedStatement alertStmt = conn.prepareStatement("select t.cdsContext, t.alertText, t.activityScore, t.phenotype from test_alert t where t.drugid=? and t.population=?");
         PreparedStatement popStmt = conn.prepareStatement("select distinct population, genes from test_alert where drugid=? order by 1");
         PreparedStatement geneStmt = conn.prepareStatement("select distinct d.name, t.drugid from test_alert t join drug d on t.drugid = d.drugid");
         ResultSet grs = geneStmt.executeQuery()
    ) {
      while (grs.next()) {
        String drugName = grs.getString(1);
        String drugId = grs.getString(2);
        sf_logger.info("Writing {}", drugName);

        popStmt.setString(1, drugId);

        // initialize the workbook
        TestAlertWorkbook workbook = new TestAlertWorkbook(drugName);

        try (ResultSet prs = popStmt.executeQuery()) {
          while (prs.next()) {
            String population = prs.getString(1);
            Array geneSqlArray = prs.getArray(2);
            String[] geneArray = (String[])geneSqlArray.getArray();

            // add a sheet to the work book and write headers
            workbook.writeSheet(population, geneArray);

            alertStmt.setString(1, drugId);
            alertStmt.setString(2, population);
            try (ResultSet ars = alertStmt.executeQuery()) {
              while (ars.next()) {
                workbook.writeAlert(
                    geneArray,
                    ars.getString(1),
                    (String[])ars.getArray(2).getArray(),
                    drugName,
                    gson.fromJson(ars.getString(3), stringMapType),
                    gson.fromJson(ars.getString(4), stringMapType)
                );
              }
            }
          }
        }
        workbook.writeNotes(queryDrugNotes(conn, drugId, FileType.TEST_ALERT));

        writeWorkbook(workbook);
        addFileExportHistory(workbook.getFilename(), new String[]{drugId});
      }
      handleFileUpload();
    }
  }
}
