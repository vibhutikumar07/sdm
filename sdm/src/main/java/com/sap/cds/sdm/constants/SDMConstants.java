package com.sap.cds.sdm.constants;

public class SDMConstants {
  private SDMConstants() {
    // Doesn't do anything
  }

  public static final String REPOSITORY_ID = "da0d8a9f-34da-4d6b-b52d-5cac7dc5d139";
  public static final String BEARER_TOKEN = "Bearer ";

  public static final String TENANT_ID = "X-zid";
  public static final String DUPLICATE_FILES_ERROR =
      "The following files %s already present.Remove the file from draft or Rename the attachment to continue.";
  public static final String ERROR_RENAMING_ATTACHMENT = "Error renaming attachment";
  public static final String DUPLICATE_FILE_IN_DRAFT_ERROR_MESSAGE =
      "The file(s) %s have been added multiple times. Please rename and try again.";
  public static final String FILES_RENAME_WARNING_MESSAGE =
      "The following files could not be renamed as they already exist:\n%s\n";
  public static final String COULD_NOT_RENAME_THE_DOCUMENT = "Could not rename the document";
  public static final String Document_NOT_FOUND = "Document not found";
}
