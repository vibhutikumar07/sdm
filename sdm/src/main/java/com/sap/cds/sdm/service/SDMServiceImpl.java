package com.sap.cds.sdm.service;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.services.persistence.PersistenceService;
import okhttp3.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.json.JSONObject;

public class SDMServiceImpl implements SDMService{

    @Override
    public String createDocument(CmisDocument cmisDocument, String jwtToken) {
        String failedDocument = "";
        String accessToken;

        OkHttpClient client = new OkHttpClient();
        SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
        try {
            accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

        try {
            byte[] fileContent = IOUtils.toByteArray(cmisDocument.getContent());
            RequestBody fileBody = RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("cmisaction", "createDocument")
                    .addFormDataPart("objectId", cmisDocument.getFolderId()) // Note: removed quotes from folderId
                    .addFormDataPart("propertyId[0]", "cmis:name")
                    .addFormDataPart("propertyValue[0]", cmisDocument.getFileName())
                    .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
                    .addFormDataPart("propertyValue[1]", "cmis:document")
                    .addFormDataPart("succinct", "true")
                    .addFormDataPart("filename", cmisDocument.getFileName(), fileBody)
                    .build();

            Request request = new Request.Builder()
                    .url(sdmUrl)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) { // Ensure resources are closed
                if (!response.isSuccessful()) {
                    failedDocument = cmisDocument.getFileName();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            failedDocument = cmisDocument.getFileName();
        }

        return failedDocument;
    }

    @Override
    public void readDocument() {

    }

    @Override
    public void deleteDocument() {

    }

    @Override
    public String getFolderId(String jwtToken, CdsEntity attachmentEntity, PersistenceService persistenceService, CmisDocument cmisDocument) throws IOException {
        String[] folderIds = {}; // getFolderIdForEntity
          Result result = DBQuery.getAttachmentsForUP__ID(attachmentEntity,persistenceService,cmisDocument.getParentId());

        String folderId = null;

        if (folderIds == null || folderIds.length == 0) {
            folderId = getFolderIdByPath(cmisDocument.getParentId(), jwtToken, cmisDocument.getRepositoryId());
            if (folderId == null) {
                folderId = createFolder(cmisDocument.getParentId(), jwtToken, cmisDocument.getRepositoryId());
            } else {
                folderId = folderIds[0];
            }
        }
        JSONObject jsonObject = new JSONObject(folderId);
        JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
        folderId = succinctProperties.getString("cmis:objectId");
        return folderId;
    }

    @Override
    public String getFolderIdByPath(String parentId, String jwtToken, String repositoryId) throws IOException {
        System.out.println("PARENTID : "+parentId+"\nREPO ID : "+repositoryId);
        OkHttpClient client = new OkHttpClient();
        SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
        String sdmUrl = sdmCredentials.getUrl()+"browser/"+repositoryId+"/root/"+parentId+"?cmisselector=object"; //get correct url and repoid
        Request request = new Request.Builder()
                .url(sdmUrl)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return "response"; //check what to actually return
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String createFolder(String parentId, String jwtToken, String repositoryId) throws IOException {
        OkHttpClient client = new OkHttpClient();
        SDMCredentials sdmCredentials =  TokenHandler.getSDMCredentials();
        String accessToken = TokenHandler.getDIToken(jwtToken,sdmCredentials);
        String sdmUrl = sdmCredentials.getUrl()+"browser/"+repositoryId+"/root";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("cmisaction", "createFolder")
                .addFormDataPart("propertyId[0]", "cmis:name")
                .addFormDataPart("propertyValue[0]",parentId) //How to get upid
                .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
                .addFormDataPart("propertyValue[1]", "cmis:folder")
                .addFormDataPart("succinct", "true")
                .build();

        Request request = new Request.Builder()
                .url(sdmUrl)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
