package com.sap.cds.sdm.service;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.sdm.util.CmisServiceUtil;

import java.io.IOException;

public class SDMServiceImpl implements  SDMService{
    @Override
    public String createDocument(MediaData mediaData) throws IOException {
        //get the token
       CmisServiceUtil.createDocument(mediaData);
        //call the create document

        return null;
    }

    @Override
    public void readDocument() {

    }

    @Override
    public void deleteDocument() {

    }
}
