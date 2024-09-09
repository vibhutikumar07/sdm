package com.sap.cds.sdm.service;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

public class SDMServiceImpl implements SDMService {

  @Override
  public JSONObject createDocument(
      CmisDocument cmisDocument, String jwtToken, SDMCredentials sdmCredentials)
      throws IOException {
    String accessToken;
    Map<String, String> finalResponse = new HashMap<>();

    OkHttpClient client = new OkHttpClient();
    accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);

    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    try {
      byte[] fileContent = IOUtils.toByteArray(cmisDocument.getContent());
      RequestBody fileBody =
          RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));

      RequestBody requestBody =
          new MultipartBody.Builder()
              .setType(MultipartBody.FORM)
              .addFormDataPart("cmisaction", "createDocument")
              .addFormDataPart(
                  "objectId", cmisDocument.getFolderId()) // Note: removed quotes from folderId
              .addFormDataPart("propertyId[0]", "cmis:name")
              .addFormDataPart("propertyValue[0]", cmisDocument.getFileName())
              .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
              .addFormDataPart("propertyValue[1]", "cmis:document")
              .addFormDataPart("succinct", "true")
              .addFormDataPart("filename", cmisDocument.getFileName(), fileBody)
              .build();
      System.out.println("BODY : " + requestBody);
      System.out.println("Folder : " + cmisDocument.getFolderId());

      Request request =
          new Request.Builder()
              .url(sdmUrl)
              .addHeader("Authorization", "Bearer " + accessToken)
              .post(requestBody)
              .build();

      try (Response response = client.newCall(request).execute()) {
        String status = "success";
        String name = cmisDocument.getFileName();
        String id = cmisDocument.getAttachmentId();
        String objectId = "";

        if (!response.isSuccessful()) {
          String responseBody = response.body().string();
          JSONObject jsonResponse = new JSONObject(responseBody);
          String message = jsonResponse.getString("message");

          if (response.code() == 409) {
            status = "duplicate";
          } else if ("Malware Service Exception: Virus found in the file!".equals(message)) {
            status = "virus";
          } else {
            System.out.println("Fail : " + response);
            status = "fail";
          }
        } else {
          String responseBody = response.body().string();
          JSONObject jsonResponse = new JSONObject(responseBody);
          JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
          status = "success";
          objectId = succinctProperties.getString("cmis:objectId");
        }

        // Construct the final response
        finalResponse.put("name", name);
        finalResponse.put("id", id);
        finalResponse.put("status", status);
        if (objectId != "") {
          finalResponse.put("url", objectId);
        }

      } catch (IOException e) {
        throw new IOException("Could not upload");
      }
    } catch (IOException e) {
      throw new IOException("Could not upload");
    }
    JSONObject result = new JSONObject(finalResponse);
    return result;
  }

  //    @Override
  //    public void readDocument() {
  //
  //    }
  //
  //    @Override
  //    public void deleteDocument() {
  //
  //    }

  @Override
  public String getFolderId(
      String jwtToken,
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      String up__ID)
      throws IOException {
    String result =
        DBQuery.getFolderIdForActiveEntity(attachmentEntity, persistenceService, up__ID);
    String folderId = null;
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();

    if (result == null) {
      System.out.println("Check1");
      folderId = getFolderIdByPath(up__ID, jwtToken, SDMConstants.REPOSITORY_ID, sdmCredentials);
      System.out.println("Check2");
      if (folderId == null) {
        folderId = createFolder(up__ID, jwtToken, SDMConstants.REPOSITORY_ID, sdmCredentials);
        JSONObject jsonObject = new JSONObject(folderId);
        JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
        folderId = succinctProperties.getString("cmis:objectId");
      }
    } else {
      folderId = result;
    }
    return folderId;
  }

  @Override
  public String getFolderIdByPath(
      String parentId, String jwtToken, String repositoryId, SDMCredentials sdmCredentials)
      throws IOException {
    OkHttpClient client = new OkHttpClient();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + repositoryId
            + "/root/"
            + parentId
            + "?cmisselector=object";
    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
      else {
        return response.body().string();
      }
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public String createFolder(
      String parentId, String jwtToken, String repositoryId, SDMCredentials sdmCredentials)
      throws IOException {
    OkHttpClient client = new OkHttpClient();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";
    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("cmisaction", "createFolder")
            .addFormDataPart("propertyId[0]", "cmis:name")
            .addFormDataPart("propertyValue[0]", parentId)
            .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
            .addFormDataPart("propertyValue[1]", "cmis:folder")
            .addFormDataPart("succinct", "true")
            .build();

    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + accessToken)
            .post(requestBody)
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Could not upload");
      return response.body().string();
    } catch (IOException e) {
      throw new IOException("Could not upload");
    }
  }

  @Override
  public String checkRepositoryType(String repositoryId) throws IOException {
    String type = CacheConfig.getVersionedRepoCache().get(repositoryId);
    System.out.println("Type " + type);
    Boolean isVersioned;
    if (type == null) {
      SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
      System.out.println("sdmCredentials " + sdmCredentials);
      String token = TokenHandler.getAccessToken(sdmCredentials);
      System.out.println("token " + token);
      JSONObject repoInfo = getRepositoryInfo(token, sdmCredentials);
      isVersioned = isRepositoryVersioned(repoInfo, repositoryId);
      System.out.println("isVersioned" + isVersioned);
    } else {
      isVersioned = "Versioned".equals(type);
    }

    if (isVersioned) {
      CacheConfig.getVersionedRepoCache().put(repositoryId, "Versioned");
      return "Versioned";
    } else {
      CacheConfig.getVersionedRepoCache().put(repositoryId, "Non Versioned");
      return "Non Versioned";
    }
  }

  public JSONObject getRepositoryInfo(String token, SDMCredentials sdmCredentials)
      throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    OkHttpClient client = new OkHttpClient();
    String getRepoInfoUrl =
        sdmCredentials.getUrl() + "browser/" + repositoryId + "?cmisselector=repositoryInfo";

    Request request =
        new Request.Builder()
            .url(getRepoInfoUrl)
            .addHeader("Authorization", "Bearer " + token)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      System.out.println("Response " + response);
      if (!response.isSuccessful()) {
        throw new IOException("Failed to get repository info");
      }
      String responseBody = response.body().string();
      System.out.println("responseBody " + responseBody);
      return new JSONObject(responseBody);
    } catch (IOException e) {
      throw new IOException("Failed to get repository info");
    }
  }

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId)
      throws IOException {
    repoInfo = repoInfo.getJSONObject(repositoryId);
    System.out.println("repoInfo " + repoInfo);
    JSONObject capabilities = repoInfo.getJSONObject("capabilities");
    String type = capabilities.getString("capabilityContentStreamUpdatability");

    if ("pwconly".equals(type)) {
      type = "Versioned";
    } else {
      type = "Non Versioned";
    }
    return "Versioned".equals(type);
  }

  @Override
  public int deleteDocument(String objectId, String jwtToken, String cmisaction)
      throws OAuth2ServiceException {
    OkHttpClient client = new OkHttpClient();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    // String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + SDMConstants.REPOSITORY_ID + "/root";

    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("cmisaction", cmisaction)
            .addFormDataPart("objectId", objectId)
            .build();

    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .post(requestBody)
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
      return response.code();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return 0;
  }

  @Override
  public JSONObject getChildren(String objectId, String jwtToken) {
    OkHttpClient client = new OkHttpClient();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + SDMConstants.REPOSITORY_ID
            + "/root/"
            + "?cmisselector=children&objectId="
            + objectId;

    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
      else {
        return new JSONObject(response.body().string());
      }
    } catch (IOException e) {
      return null;
    }
  }
}
