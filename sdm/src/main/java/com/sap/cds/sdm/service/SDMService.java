package com.sap.cds.sdm.service;

import com.sap.cds.sdm.model.CmisDocument;
import java.io.IOException;

public interface SDMService {
  public String createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException;

  public void readDocument();

  public void deleteDocument();

  public String createFolder();
}
