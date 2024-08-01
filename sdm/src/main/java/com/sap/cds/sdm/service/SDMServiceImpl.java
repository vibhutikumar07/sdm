package com.sap.cds.sdm.service;

import com.sap.cds.CdsData;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.util.CmisServiceUtil;

import java.io.IOException;
import java.util.List;

public class SDMServiceImpl implements  SDMService{
    @Override
    public String createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException {
        //get the token
        return  CmisServiceUtil.createDocument(cmisDocument,jwtToken);
        //call the create document


    }

    @Override
    public void readDocument() {

    }

    @Override
    public void deleteDocument() {

    }
}
