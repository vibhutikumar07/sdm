package com.sap.cds.sdm.constants;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = "RASHMI-REPO";
  public static final String BEARER_TOKEN = "Bearer ";

  public static final String TENANT_ID = "X-zid";
  public static final String DUPLICATE_FILES_ERROR =
      "The following files %s already present.Remove the file from draft or Rename the attachment to continue.";
}
