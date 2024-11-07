package com.sap.cds.sdm.service;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

      handleDocumentCreationRequest(
          cmisDocument, client, requestBody, sdmUrl, accessToken, finalResponse);

    } catch (IOException e) {
      throw new ServiceException("Could not upload");
    }
    return new JSONObject(finalResponse);
  }

  private void handleDocumentCreationRequest(
      CmisDocument cmisDocument,
      OkHttpClient client,
      RequestBody requestBody,
      String sdmUrl,
      String accessToken,
      Map<String, String> finalResponse)
      throws IOException {
    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + accessToken)
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
      if (!objectId.isEmpty()) {
        finalResponse.put("url", objectId);
      }

    } catch (IOException e) {
      throw new ServiceException("Could not upload");
    }
  }

  @Override
  public int renameAttachments(
      String jwtToken, SDMCredentials sdmCredentials, String fileName, String objectId)
      throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    OkHttpClient client = new OkHttpClient();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + repositoryId + "/root";
    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("cmisaction", "update")
            .addFormDataPart("propertyId[0]", "cmis:name")
            .addFormDataPart("propertyValue[0]", fileName)
            .addFormDataPart("objectId", objectId)
            .build();

    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + accessToken)
            .post(requestBody)
            .build();

    try (Response response = client.newCall(request).execute()) {
      return response.code();
    } catch (IOException e) {
      throw new IOException(SDMConstants.COULD_NOT_RENAME_THE_DOCUMENT, e);
    }
  }

  @Override
  public String getObject(String jwtToken, String objectId, SDMCredentials sdmCredentials)
      throws IOException {
    OkHttpClient client = new OkHttpClient();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + SDMConstants.REPOSITORY_ID
            + "/root?cmisselector=object&objectId="
            + objectId
            + "&succinct=true";
    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + accessToken)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        return null;
      } else {
        String object = response.body().string();
        JSONObject jsonObject = new JSONObject(object);
        JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
        return succinctProperties.getString("cmis:name");
      }
    } catch (IOException e) {
      throw new IOException(SDMConstants.Document_NOT_FOUND, e);
    }
  }

  @Override
  public void readDocument(
      String objectId,
      String jwtToken,
      SDMCredentials sdmCredentials,
      AttachmentReadEventContext context)
      throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    OkHttpClient client = new OkHttpClient();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl =
        sdmCredentials.getUrl()
            + "browser/"
            + repositoryId
            + "/root?objectID="
            + objectId
            + "&cmisselector=content";
    Request request =
        new Request.Builder()
            .url(sdmUrl)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

    Response response = client.newCall(request).execute();
    if (!response.isSuccessful()) {
      response.close();
      throw new ServiceException("Unexpected code");
    }

    InputStream documentStream = response.body().byteStream();
    try {
      context.getData().setContent(documentStream);
    } catch (Exception e) {
      response.close();
      throw new ServiceException("Failed to set document stream in context");
    }
  }

  @Override
  public String getFolderId(
      String jwtToken, Result result, PersistenceService persistenceService, String upID)
      throws IOException {

    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream()
            .map(map -> (Map<String, Object>) map)
            .collect(Collectors.toList());

    String folderId = null;
    String repositoryId = null;
    for (Map<String, Object> attachment : resultList) {
      if (attachment.get("folderId") != null) {
        folderId = attachment.get("folderId").toString();
        repositoryId = attachment.get("repositoryId").toString();
      }
    }
    String repoId = SDMConstants.REPOSITORY_ID;
    // check if folderId exists for the repositoryId if not then make folderId null else continue
    if (!repoId.equalsIgnoreCase(repositoryId)) {
      folderId = null;
    }
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();

    if (folderId == null) {
      folderId = getFolderIdByPath(upID, jwtToken, SDMConstants.REPOSITORY_ID, sdmCredentials);
      if (folderId == null) {
        folderId = createFolder(upID, jwtToken, SDMConstants.REPOSITORY_ID, sdmCredentials);
        JSONObject jsonObject = new JSONObject(folderId);
        JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
        folderId = succinctProperties.getString("cmis:objectId");
      }
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
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + accessToken)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        return null;
      } else {
        return response.body().string();
      }
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
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + accessToken)
            .post(requestBody)
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new ServiceException("Could not upload");
      return response.body().string();
    } catch (IOException e) {
      throw new ServiceException("Could not upload");
    }
  }

  @Override
  public String checkRepositoryType(String repositoryId) throws IOException {
    String type = CacheConfig.getVersionedRepoCache().get(repositoryId);
    Boolean isVersioned;
    if (type == null) {
      SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
      String token = TokenHandler.getAccessToken(sdmCredentials);
      JSONObject repoInfo = getRepositoryInfo(token, sdmCredentials);
      isVersioned = isRepositoryVersioned(repoInfo, repositoryId);
    } else {
      isVersioned = "Versioned".equals(type);
    }

    if (Boolean.TRUE.equals(isVersioned)) {
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
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + token)
            .get()
            .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new ServiceException("Failed to get repository info");
      }
      String responseBody = response.body().string();
      return new JSONObject(responseBody);
    } catch (IOException e) {
      throw new ServiceException("Failed to get repository info");
    }
  }

  public Boolean isRepositoryVersioned(JSONObject repoInfo, String repositoryId)
      throws IOException {
    repoInfo = repoInfo.getJSONObject(repositoryId);
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
  public int deleteDocument(String cmisaction, String objectId, String userEmail, String subdomain)
      throws IOException {
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    OkHttpClient client = new OkHttpClient();
    String accessToken =
        TokenHandler.getDITokenUsingAuthorities(sdmCredentials, userEmail, subdomain);

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
            .addHeader("Authorization", SDMConstants.BEARER_TOKEN + accessToken)
            .post(requestBody)
            .build();

    try (Response response = client.newCall(request).execute()) {
      return response.code();
    } catch (IOException e) {
      throw new ServiceException("Could not delete the document");
    }
  }
}
