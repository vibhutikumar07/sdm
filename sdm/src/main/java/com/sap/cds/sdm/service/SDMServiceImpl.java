package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import java.io.IOException;
import okhttp3.*;
import org.apache.commons.io.IOUtils;

public class SDMServiceImpl implements SDMService {
  @Override
  public String createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException {
    OkHttpClient client = new OkHttpClient();
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
    String sdmUrl = sdmCredentials.getUrl() + "browser/" + SDMConstants.REPOSITORY_ID + "/root";

    RequestBody requestBody =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("cmisaction", "createDocument")
            .addFormDataPart("propertyId[0]", "cmis:name")
            .addFormDataPart("propertyValue[0]", cmisDocument.getFileName())
            .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
            .addFormDataPart("propertyValue[1]", "cmis:document")
            .addFormDataPart("succinct", "true")
            .addFormDataPart(
                "filename",
                cmisDocument.getFileName(),
                RequestBody.create(
                    IOUtils.toByteArray(cmisDocument.getContent()),
                    MediaType.parse("application/octet-stream")))
            .build();

    Request request =
        new Request.Builder()
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

  @Override
  public void readDocument() {}

  @Override
  public void deleteDocument() {}

  @Override
  public String createFolder() {
    return null;
  }
}
