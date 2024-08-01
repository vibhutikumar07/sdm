package com.sap.cds.sdm.service;

import com.sap.cds.CdsData;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.sdm.model.CmisDocument;

import java.io.IOException;
import java.util.List;

public interface SDMService {
    public String createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException;
    public void readDocument();
    public void deleteDocument();
}
