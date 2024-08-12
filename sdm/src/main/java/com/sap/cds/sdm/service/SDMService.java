package com.sap.cds.sdm.service;

import com.sap.cds.CdsData;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;

import java.io.IOException;
import java.util.List;

public interface SDMService {
    public String createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException;
    public String createFolder(String parentId, String jwtToken, String repositoryId) throws IOException;
    public String getFolderId(String jwtToken, CdsEntity attachmentEntity, PersistenceService persistenceService, String up__ID) throws IOException;
    public String getFolderIdByPath(String parentId, String jwtToken, String repositoryId) throws IOException;
    public void readDocument();
    public void deleteDocument();
}
