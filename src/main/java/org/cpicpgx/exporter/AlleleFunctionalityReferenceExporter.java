package org.cpicpgx.exporter;

import org.cpicpgx.db.ConnectionFactory;
import org.cpicpgx.model.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * This class queries the functional_reference table and then dumps the contents out to excel workbooks
 *
 * @author Ryan Whaley
 */
public class AlleleFunctionalityReferenceExporter extends BaseExporter {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static void main(String[] args) {
    AlleleFunctionalityReferenceExporter exporter = new AlleleFunctionalityReferenceExporter();
    try {
      exporter.parseArgs(args);
      exporter.export();
    } catch (Exception ex) {
      sf_logger.error("Error exporting allele functionality reference", ex);
    }
  }

  public FileType getFileType() {
    return FileType.ALLELE_FUNCTION_REFERENCE;
  }

  public void export() throws Exception {
    try (Connection conn = ConnectionFactory.newConnection();
         PreparedStatement geneStmt = conn.prepareStatement("select distinct a.genesymbol from allele a where clinicalfunctionalstatus is not null order by 1");
         PreparedStatement alleleStmt = conn.prepareStatement("select a.name, a.activityvalue, a.functionalstatus, a.clinicalfunctionalstatus, a.clinicalfunctionalsubstrate, " +
             "a.citations, a.strength, a.findings, a.functioncomments " +
             "from allele a where a.genesymbol=? order by a.id");
         ResultSet grs = geneStmt.executeQuery()
    ) {
      while (grs.next()) {
        String symbol = grs.getString(1);
        
        AlleleFunctionalityReferenceWorkbook workbook = new AlleleFunctionalityReferenceWorkbook(symbol);
        
        alleleStmt.setString(1, symbol);
        try (ResultSet rs = alleleStmt.executeQuery()) {
          while (rs.next()) {
            String alleleName = rs.getString(1);
            String activity = rs.getString(2);
            String function = rs.getString(3);
            String clinFunction = rs.getString(4);
            String clinSubstrate = rs.getString(5);
            Array ctiations = rs.getArray(6);
            String strength = rs.getString(7);
            String findings = rs.getString(8);
            String comments = rs.getString(9);

            String[] citationArray = ctiations == null ? new String[]{} : (String[])ctiations.getArray();

            workbook.writeAlleleRow(alleleName, activity, function, clinFunction, clinSubstrate, citationArray, strength, findings, comments);
          }
        }
        
        workbook.writeNotes(queryNotes(conn, symbol, FileType.ALLELE_FUNCTION_REFERENCE));

        workbook.writeChangeLog(queryChangeLog(conn, symbol, getFileType()));

        writeWorkbook(workbook);
        addFileExportHistory(workbook.getFilename(), new String[]{symbol});
      }
      handleFileUpload();
    }
  }
}
