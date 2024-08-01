package com.sap.cds.sdm.util;
import com.sap.cds.CdsData;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;


import java.io.*;

import okhttp3.*;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.util.List;

public class CmisServiceUtil {


    public static String createDocument(CmisDocument cmisDocument, String jwtToken) throws IOException {
        OkHttpClient client = new OkHttpClient();
        SDMCredentials sdmCredentials =  TokenHandler.getSDMCredentials();
        String accessToken = TokenHandler.generateDITokenFromTokenExchange(jwtToken,sdmCredentials);
        String sdmUrl = sdmCredentials.getUrl()+"browser/b490f13f-f02e-4ef4-9fde-dc7e9dfda59a/root";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("cmisaction", "createDocument")
                .addFormDataPart("propertyId[0]", "cmis:name")
                .addFormDataPart("propertyValue[0]",cmisDocument.getFileName() )
                .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
                .addFormDataPart("propertyValue[1]", "cmis:document")
                .addFormDataPart("succinct", "true")
                .addFormDataPart("filename", cmisDocument.getFileName(),
                        RequestBody.create(IOUtils.toByteArray(cmisDocument.getContent()), MediaType.parse("application/octet-stream")))
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
