package com.sap.cds.sdm.util;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cloud.environment.servicebinding.api.DefaultServiceBindingAccessor;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;

import static java.util.Objects.requireNonNull;
import static org.json.HTTP.CRLF;
import okhttp3.*;
import org.apache.commons.io.IOUtils;

import java.io.File;
public class CmisServiceUtil {
    private static Map<String, Object> uaaCredentials;
    private static Map<String, Object> uaa;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static byte[] toBytes(String str) {
        return requireNonNull(str).getBytes(StandardCharsets.UTF_8);
    }

    public static String toString(byte[] bytes) {
        return new String(requireNonNull(bytes), StandardCharsets.UTF_8);
    }

    private static SDMCredentials getSDMCredentials() {
        List<ServiceBinding> allServiceBindings = DefaultServiceBindingAccessor.getInstance().getServiceBindings();
        System.out.println("allServiceBindings" + allServiceBindings);
        // filter for a specific binding
        ServiceBinding sdmBinding = allServiceBindings.stream()
                .filter(binding -> "sdm".equalsIgnoreCase(binding.getServiceName().orElse(null)))
                .findFirst()
                .get();
        SDMCredentials sdmCredentials = new SDMCredentials();
        System.out.println("SDM Cred " + sdmCredentials);
        uaaCredentials = sdmBinding.getCredentials();
        System.out.println("UAA Cred " + uaaCredentials);
        uaa = (Map<String, Object>) uaaCredentials.get("uaa");

        sdmCredentials.setBaseTokenUrl(uaa.get("url").toString());
        sdmCredentials.setUrl(sdmBinding.getCredentials().get("uri").toString());
        sdmCredentials.setClientId(uaa.get("clientid").toString());
        sdmCredentials.setClientSecret(uaa.get("clientsecret").toString());
        return sdmCredentials;

    }

    private static String getAccessToken(SDMCredentials sdmCredentials) throws IOException, ProtocolException {
        System.out.println("Credentials " + sdmCredentials.getBaseTokenUrl() + ":" + sdmCredentials.getClientId());
        String userCredentials = sdmCredentials.getClientId() + ":" + sdmCredentials.getClientSecret();
        String authHeaderValue = "Basic " + Base64.encodeBase64String(toBytes(userCredentials));
        String bodyParams = "grant_type=client_credentials";
        byte[] postData = toBytes(bodyParams);
        String authurl = sdmCredentials.getBaseTokenUrl() + "/oauth/token";
        URL url = new URL(authurl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", authHeaderValue);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", "" + postData.length);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
            os.write(postData);
        }
        String resp;
        try (DataInputStream is = new DataInputStream(conn.getInputStream());
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            resp = br.lines().collect(Collectors.joining("\n"));
        }
        System.out.println("Response  " + resp);
        conn.disconnect();
        System.out.println("Token " + mapper.readValue(resp, JsonNode.class).get("access_token").asText());
        return mapper.readValue(resp, JsonNode.class).get("access_token").asText();
    }

    public static void createDocument(MediaData mediaData) throws IOException {
        OkHttpClient client = new OkHttpClient();
        SDMCredentials sdmCredentials = getSDMCredentials();
        String accessToken = getAccessToken(sdmCredentials);
        String sdmUrl = sdmCredentials.getUrl()+"browser/"+ SDMConstants.REPOSITORY_ID +"/root";

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("cmisaction", "createDocument")
                .addFormDataPart("propertyId[0]", "cmis:name")
                .addFormDataPart("propertyValue[0]",mediaData.getFileName() )
                .addFormDataPart("propertyId[1]", "cmis:objectTypeId")
                .addFormDataPart("propertyValue[1]", "cmis:document")
                .addFormDataPart("succinct", "true")
                .addFormDataPart("filename", mediaData.getFileName(),
                        RequestBody.create(IOUtils.toByteArray(mediaData.getContent()), MediaType.parse("application/octet-stream")))
                .build();

        Request request = new Request.Builder()
                .url(sdmUrl)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            System.out.println(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
