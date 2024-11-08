package com.sap.cds.sdm.service;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import org.json.JSONObject;

public interface SDMService {
  public JSONObject createDocument(
      CmisDocument cmisDocument, String jwtToken, SDMCredentials sdmCredentials) throws IOException;

  public String createFolder(
      String parentId, String jwtToken, String repositoryId, SDMCredentials sdmCredentials)
      throws IOException;

  public String getFolderId(
      String jwtToken, Result result, PersistenceService persistenceService, String upID)
      throws IOException;

  public String getFolderIdByPath(
      String parentId, String jwtToken, String repositoryId, SDMCredentials sdmCredentials)
      throws IOException;

  public String checkRepositoryType(String repositoryId) throws IOException;

  public JSONObject getRepositoryInfo(String token, SDMCredentials sdmCredentials)
      throws IOException;

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId) throws IOException;

  public int deleteDocument(String cmisaction, String objectId, String userEmail, String subdomain)
      throws IOException;

  public void readDocument(
      String objectId,
      String jwtToken,
      SDMCredentials sdmCredentials,
      AttachmentReadEventContext context)
      throws IOException;

  public int renameAttachments(
      String jwtToken, SDMCredentials sdmCredentials, CmisDocument cmisDocument) throws IOException;

  public String getObject(String jwtToken, String objectId, SDMCredentials sdmCredentials)
      throws IOException;
}
