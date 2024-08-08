package com.sap.cds.sdm.constants;

public class SDMConstants {
  private SDMConstants() {
    throw new IllegalStateException("Constants class");
  }

  public static final String REPOSITORY_ID = System.getenv("REPOSITORY_ID");
  public static final String TENANT_ID = "X-zid";
}
