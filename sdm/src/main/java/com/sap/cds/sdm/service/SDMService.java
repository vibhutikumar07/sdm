package com.sap.cds.sdm.service;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;

import java.io.IOException;

public interface SDMService {
    public String createDocument(MediaData mediaData) throws IOException;
    public void readDocument();
    public void deleteDocument();
}
