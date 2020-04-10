package org.cpicpgx.util;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.cpicpgx.db.FileHistoryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Utility class that helps write files to the network file store, Amazon S3.
 * 
 * This class assumes you have <a href="https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html">AWS credentials setup on your system</a>.
 * This usually entails setting up a <code>~/.aws</code> directory with configuration and credential files but can be 
 * handled in other ways if necessary.
 *
 * @author Ryan Whaley
 */
public class FileStoreClient implements AutoCloseable {
  private static final Logger sf_logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  private static final String S3_PUBLIC_BUCKET      = "files.cpicpgx.org";
  private static final String S3_GENERIC_KEY_PREFIX = "data/";
  private static final String S3_GENE_KEY_PREFIX    = "data/gene/";
  private static final String S3_DRUG_KEY_PREFIX    = "data/drug/";
  private static final String S3_URL_FORMAT         = "http://" + S3_PUBLIC_BUCKET + "/%s%s";
  
  private AmazonS3 s3;
  private FileHistoryWriter fileHistoryWriter;
  
  public FileStoreClient() {
    s3 = AmazonS3ClientBuilder.defaultClient();
    try {
      fileHistoryWriter = new FileHistoryWriter(null);
    } catch (SQLException e) {
      sf_logger.error("Could not start DB connection", e);
    }
  }
  
  public void putGeneArtifact(Path geneFilePath) {
    String fileName = geneFilePath.getFileName().toString();
    putFile(S3_GENE_KEY_PREFIX, fileName, geneFilePath.toFile());
    try {
      fileHistoryWriter.writeUpload(fileName, String.format(S3_URL_FORMAT, S3_GENE_KEY_PREFIX, fileName));
    } catch (SQLException e) {
      sf_logger.error("Error updating file record in DB " + fileName, e);
    }
  }
  
  public void putDrugArtifact(Path drugFilePath) {
    String fileName = drugFilePath.getFileName().toString();
    putFile(S3_DRUG_KEY_PREFIX, fileName, drugFilePath.toFile());
    try {
      fileHistoryWriter.writeUpload(fileName, String.format(S3_URL_FORMAT, S3_DRUG_KEY_PREFIX, fileName));
    } catch (SQLException e) {
      sf_logger.error("Error updating file record in DB " + fileName, e);
    }
  }
  
  public void putArtifact(Path filePath) {
    String fileName = filePath.getFileName().toString();
    putFile(S3_GENERIC_KEY_PREFIX, fileName, filePath.toFile());
    try {
      fileHistoryWriter.writeUpload(fileName, String.format(S3_URL_FORMAT, S3_GENERIC_KEY_PREFIX, fileName));
    } catch (SQLException e) {
      sf_logger.error("Error updating file record in DB " + fileName, e);
    }
  }
  
  private void putFile(String prefix, String fileName, File geneFile) {
    s3.putObject(S3_PUBLIC_BUCKET, prefix + fileName, geneFile);
    sf_logger.info("Uploaded {}", String.format("s3:///%s/%s%s", S3_PUBLIC_BUCKET, prefix, fileName));
  }
  
  @Override
  public void close() {
    if (s3 != null) {
      s3.shutdown();
    }
    if (fileHistoryWriter != null) {
      try {
        fileHistoryWriter.close();
      } catch (Exception e) {
        sf_logger.error("Could not close DB connection", e);
      }
    }
  }
}
