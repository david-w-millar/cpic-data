package org.cpicpgx.importer;

import org.apache.commons.lang3.StringUtils;
import org.cpicpgx.db.ConnectionFactory;
import org.cpicpgx.exception.NotFoundException;
import org.cpicpgx.util.RowWrapper;
import org.cpicpgx.util.WorkbookWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to parse references for functional assignments from a directory of excel files.
 *
 * @author Ryan Whaley
 */
public class FunctionReferenceImporter extends BaseDirectoryImporter {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final Pattern sf_geneLabelPattern = Pattern.compile("GENE:\\s(\\w+)");
  private static final int COL_IDX_ALLELE = 0;
  private static final int COL_IDX_ACTIVITY = 1;
  private static final int COL_IDX_FUNCTION = 2;
  private static final int COL_IDX_CLINICAL_FUNCTION = 3;
  private static final int COL_IDX_CLINICAL_SUBSTRATE = 4;
  private static final int COL_IDX_PMID = 5;
  private static final int COL_IDX_STRENGTH = 6;
  private static final int COL_IDX_FINDINGS = 7;
  private static final int COL_IDX_COMMENTS = 8;

  private static final String[] sf_deleteStatements = new String[]{
      "delete from function_reference"
  };
  private static final String DEFAULT_DIRECTORY = "allele_functionality_reference";

  public static void main(String[] args) {
    rebuild(new FunctionReferenceImporter(), args);
  }

  public FunctionReferenceImporter() { }
  
  String getFileExtensionToProcess() {
    return EXCEL_EXTENSION;
  }

  public String getDefaultDirectoryName() {
    return DEFAULT_DIRECTORY;
  }

  @Override
  public FileType getFileType() {
    return FileType.ALLELE_FUNCTION_REFERENCE;
  }

  String[] getDeleteStatements() {
    return sf_deleteStatements;
  }

  void processWorkbook(WorkbookWrapper workbook) throws NotFoundException, SQLException {
    int rowIdx = 0;

    RowWrapper row = null;
    String geneSymbol = null;
    for (; rowIdx <= workbook.currentSheet.getLastRowNum(); rowIdx++) {
      row = workbook.getRow(rowIdx);
      String geneLabel = row.getNullableText(0);
      if (geneLabel == null) continue;
      
      Matcher m = sf_geneLabelPattern.matcher(geneLabel);
      if (m.find()) {
        geneSymbol = m.group(1);
        break;
      }
    }
    
    if (geneSymbol == null) {
      throw new NotFoundException("Couldn't find gene symbol");
    }
    
    sf_logger.debug("This sheet is for {}, {}", geneSymbol, row.getNullableText(1));
    
    rowIdx += 2; // move down 2 rows and start reading;
    try (DbHarness dbHarness = new DbHarness(geneSymbol)) {
      for (; rowIdx <= workbook.currentSheet.getLastRowNum(); rowIdx++) {
        row = workbook.getRow(rowIdx);
        if (row.hasNoText(COL_IDX_ALLELE)) break;
        
        String alleleName = row.getNullableText(COL_IDX_ALLELE);
        String activityScore = row.getNullableText(COL_IDX_ACTIVITY);
        String functionStatus = row.getNullableText(COL_IDX_FUNCTION);
        String clinicalFunction = row.getNullableText(COL_IDX_CLINICAL_FUNCTION);
        String substrate = row.getNullableText(COL_IDX_CLINICAL_SUBSTRATE);
        String citationClump = row.getNullableText(COL_IDX_PMID);
        String strength = row.getNullableText(COL_IDX_STRENGTH);
        String findings = row.getNullableText(COL_IDX_FINDINGS);
        String comments = row.getNullableText(COL_IDX_COMMENTS);

        String[] citations = new String[0];
        if (StringUtils.isNotBlank(citationClump)) {
          citations = citationClump.split(";");
        }
        
        dbHarness.insert(
            alleleName,
            activityScore,
            functionStatus,
            clinicalFunction,
            substrate,
            citations,
            strength,
            findings,
            comments
        );
      }
    }
    addImportHistory(workbook.getFileName());
  }

  /**
   * Private class for handling DB interactions
   */
  static class DbHarness implements AutoCloseable {
    private Connection conn;
    private Map<String, Long> alleleNameMap = new HashMap<>();
    private PreparedStatement updateAlleleStmt;
    private PreparedStatement insertStmt;
    
    DbHarness(String gene) throws SQLException {
      this.conn = ConnectionFactory.newConnection();

      try (PreparedStatement pstmt = this.conn.prepareStatement("select name, id from allele where allele.geneSymbol=?")) {
        pstmt.setString(1, gene);
        try (ResultSet rs = pstmt.executeQuery()) {
          while (rs.next()) {
            this.alleleNameMap.put(rs.getString(1), rs.getLong(2));
          }
        }
      }
      
      updateAlleleStmt = this.conn.prepareStatement("update allele set functionalstatus=?, activityScore=?, clinicalFunctionalStatus=?, clinicalFunctionalSubstrate=? where id=?");
      insertStmt = this.conn.prepareStatement("insert into function_reference(alleleid, citations, strength, findings, comments) values (?, ?, ?, ?, ?)");
    }
    
    void insert(
        String allele,
        String activityScore,
        String alleleFunction,
        String clinicalFunction,
        String substrate,
        String[] pmids,
        String strength,
        String findings,
        String comments
    ) throws SQLException {
      if (!this.alleleNameMap.containsKey(allele)) {
        sf_logger.warn("No allele defined with name {}", allele);
        return;
      }

      this.insertStmt.clearParameters();
      this.insertStmt.setLong(1, this.alleleNameMap.get(allele));
      this.insertStmt.setArray(2, conn.createArrayOf("TEXT", pmids));
      setNullableText(this.insertStmt, 3, strength);
      setNullableText(this.insertStmt, 4, findings);
      setNullableText(this.insertStmt, 5, comments);
      this.insertStmt.executeUpdate();

      this.updateAlleleStmt.clearParameters();
      this.updateAlleleStmt.setString(1, normalizeFunction(alleleFunction));
      setNullableText(this.updateAlleleStmt, 2, activityScore);
      setNullableText(this.updateAlleleStmt, 3, clinicalFunction);
      setNullableText(this.updateAlleleStmt, 4, substrate);
      this.updateAlleleStmt.setLong(5, this.alleleNameMap.get(allele));
      this.updateAlleleStmt.executeUpdate();
    }
    
    private void setNullableText(PreparedStatement stmt, int idx, String value) throws SQLException {
      if (StringUtils.isNotBlank(value)) {
        stmt.setString(idx, value);
      } else {
        stmt.setNull(idx, Types.VARCHAR);
      }
    }

    @Override
    public void close() throws SQLException {
      if (this.insertStmt != null) {
        this.insertStmt.close();
      }
      if (this.conn != null) {
        this.conn.close();
      }
    }
    
    private String normalizeFunction(String fn) {
      if (fn == null) {
        return null;
      }
      
      return fn
          .replaceAll("Function", "function")
          .replaceAll("unctione", "unction");
    }
  }
}
