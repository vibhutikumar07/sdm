package com.sap.cds.sdm.service;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.Row;
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
    public JSONObject createDocument(CmisDocument cmisDocument, String jwtToken) {
        String failedDocument = "";
        String accessToken;
        JSONObject result = new JSONObject();

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
                else{
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
                    result.put("success", succinctProperties.getString("cmis:objectId"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            failedDocument = cmisDocument.getFileName();
            result.put("fail", failedDocument);
        }

        return result;
    }

    @Override
    public void readDocument() {

    }

    @Override
    public void deleteDocument() {

    }

    @Override
    public String getFolderId(String jwtToken, CdsEntity attachmentEntity, PersistenceService persistenceService,String up__ID) throws IOException {
        String[] folderIds = {}; // getFolderIdForEntity
        Result result = DBQuery.getAttachmentsForUP__ID(attachmentEntity,persistenceService,up__ID);
        List<Row> rows = result.list();
        String folderId = null;
        folderId = getFolderIdByPath(up__ID, jwtToken, SDMConstants.REPOSITORY_ID);
        System.out.println("Value123 : "+folderId);

        if (rows.size() ==0) {
            folderId = getFolderIdByPath(up__ID, jwtToken, SDMConstants.REPOSITORY_ID);
            if (folderId == null) {
                folderId = createFolder(up__ID, jwtToken, SDMConstants.REPOSITORY_ID);
                JSONObject jsonObject = new JSONObject(folderId);
                JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
                folderId = succinctProperties.getString("cmis:objectId");
            } else {
                folderId = folderIds[0];
            }
        }else{
            folderId = rows.get(0).get("folderId").toString();
        }

        return folderId;
    }

    @Override
    public String getFolderIdByPath(String parentId, String jwtToken, String repositoryId) throws IOException {
        OkHttpClient client = new OkHttpClient();
        SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
        String sdmUrl = sdmCredentials.getUrl()+"browser/"+repositoryId+"/root/"+parentId+"?cmisselector=object";
        System.out.println(jwtToken);
        Request request = new Request.Builder()
                .url(sdmUrl)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            else {
                return response.body().string();
            }
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
                .addFormDataPart("propertyValue[0]",parentId)
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
