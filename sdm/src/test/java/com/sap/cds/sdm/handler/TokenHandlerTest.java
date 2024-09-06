package com.sap.cds.sdm.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.applicationservice.SDMCreateEventHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.messages.Message;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.security.xsuaa.client.OAuth2ServiceException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.ehcache.Cache;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

public class TokenHandlerTest {
//    @Test
//    public void testGetTokenFields() {
//        JsonObject expected = new JsonObject();
//        expected.addProperty("sub", "1234567890");
//        expected.addProperty("name", "John Doe");
//        expected.addProperty("iat", 1516239022);
//        JsonObject result = TokenHandler.getTokenFields("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
//        assertEquals(expected, result);
//    }
//
//    @Test
//    public void testToBytes() {
//        String input = "Hello, World!";
//        byte[] expected = "Hello, World!".getBytes(StandardCharsets.UTF_8);
//        byte[] result = TokenHandler.toBytes(input);
//        assertArrayEquals(expected, result);
//    }
//
//    @Test
//    public void testToString() {
//        byte[] input = "Hello, World!".getBytes(StandardCharsets.UTF_8);
//        String expected = "Hello, World!";
//        String result = TokenHandler.toString(input);
//        assertEquals(expected, result);
//    }
//
//    @Test
//    public void testGetAccessTokenCache(){
//        SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
//        try (MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {
//            Cache<String, String> mockCache = Mockito.mock(Cache.class);
//            Mockito.when(mockCache.get("clientCredentialsToken")).thenReturn("cachedToken");
//            cacheConfigMockedStatic.when(CacheConfig::getClientCredentialsTokenCache).thenReturn(mockCache);
//            String cachedToken = TokenHandler.getAccessToken(mockSdmCredentials);
//            assertEquals(cachedToken, "cachedToken");
//        } catch (ProtocolException e) {
//            throw new RuntimeException(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
////    @Test
////    public void testGetAccessTokenWhenCacheIsEmpty() throws IOException, ProtocolException {
////        SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
////        Mockito.when(mockSdmCredentials.getClientId()).thenReturn("client_id");
////        Mockito.when(mockSdmCredentials.getClientSecret()).thenReturn("client_secret");
////        Mockito.when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("http://example.com");
////
////        try (MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {
////            Cache<String, String> mockCache = Mockito.mock(Cache.class);
////            Mockito.when(mockCache.get("clientCredentialsToken")).thenReturn(null); // Cache is empty
////            cacheConfigMockedStatic.when(CacheConfig::getClientCredentialsTokenCache).thenReturn(mockCache);
////
////            // Mock Base64 Encoder
////            Base64.Encoder encoder = Base64.getEncoder();
////            MockedStatic<Base64> mockedBase64 = Mockito.mockStatic(Base64.class);
////            mockedBase64.when(Base64::getEncoder).thenReturn(encoder);
////
////            HttpURLConnection mockConnection = Mockito.mock(HttpURLConnection.class);
////
////            try (MockedConstruction<URL> mockedUrl = Mockito.mockConstruction(URL.class, (mock, context) -> {
////                Mockito.when(mock.openConnection()).thenReturn(mockConnection);
////            })) {
////                Mockito.doNothing().when(mockConnection).setRequestMethod("POST");
////                Mockito.doNothing().when(mockConnection).setRequestProperty(Mockito.anyString(), Mockito.anyString());
////                Mockito.when(mockConnection.getOutputStream()).thenReturn(Mockito.mock(DataOutputStream.class));
////
////                // Mock the InputStream and BufferedReader correctly
////                InputStream mockInputStream = new ByteArrayInputStream("{\"access_token\":\"newGeneratedToken\"}".getBytes(StandardCharsets.UTF_8));
////                Mockito.when(mockConnection.getInputStream()).thenReturn(mockInputStream);
////
////                // Use doNothing() for void methods
////                Mockito.doNothing().when(mockCache).put(Mockito.eq("clientCredentialsToken"), Mockito.eq("newGeneratedToken"));
////
////                String resultToken = TokenHandler.getAccessToken(mockSdmCredentials);
////                assertEquals("newGeneratedToken", resultToken);
////            }
////        }
////    }
//
//    @Test
//    public void testGetDIToken() {
//        JsonObject expected = new JsonObject();
//        expected.addProperty("email", "john.doe@example.com"); // Correct the property name as expected in the method
//        expected.addProperty("exp", "1234567890"); // Correct the property name as expected in the method
//
//        SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
//        try (MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class)) {
//
//            Cache<String, String> mockCache = Mockito.mock(Cache.class);
//            Mockito.when(mockCache.get(any())).thenReturn("cachedToken"); // Cache is empty
//            cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
//            String result = TokenHandler.getDIToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwic3ViIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTY4MzQxODI4MCwiZXhwIjoxNjg1OTQ0MjgwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c", mockSdmCredentials);
//            assertEquals("cachedToken", result); // Adjust based on the expected result
//        } catch (OAuth2ServiceException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @Test
    public void testGetDITokenNoCache() throws IOException {

        // Mock the payload
        JsonObject mockPayload = new JsonObject();
        mockPayload.addProperty("email", "john.doe@example.com");
        mockPayload.addProperty("exp", "1234567890");
        mockPayload.addProperty("zid", "tenant-id-value");

        // Mock HTTP client and its response
        CloseableHttpClient mockHttpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = Mockito.mock(CloseableHttpResponse.class);
        HttpEntity mockEntity = Mockito.mock(HttpEntity.class);
        StatusLine mockStatusLine = Mockito.mock(StatusLine.class);

        // Set up the HTTP response
        Mockito.when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        Mockito.when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        Mockito.when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream("{\"access_token\": \"mockedToken\"}".getBytes()));
        Mockito.when(mockResponse.getEntity()).thenReturn(mockEntity);

        // Mock HTTP client behavior
        Mockito.when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);

        // Mock SDMCredentials
        SDMCredentials mockSdmCredentials = Mockito.mock(SDMCredentials.class);
        Mockito.when(mockSdmCredentials.getClientId()).thenReturn("mockClientId");
        Mockito.when(mockSdmCredentials.getBaseTokenUrl()).thenReturn("https://mock.url");

        try (MockedStatic<HttpClients> httpClientsMockedStatic = Mockito.mockStatic(HttpClients.class);
             MockedStatic<CacheConfig> cacheConfigMockedStatic = Mockito.mockStatic(CacheConfig.class);) {
            httpClientsMockedStatic.when(HttpClients::createDefault).thenReturn(mockHttpClient);
            Cache<String, String> mockCache = Mockito.mock(Cache.class);
            Mockito.when(mockCache.get(any())).thenReturn(null); // Cache is empty

            cacheConfigMockedStatic.when(CacheConfig::getUserTokenCache).thenReturn(mockCache);
            String result = TokenHandler.getDIToken(
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIiwiZXhwIjoiMTIzNDU2Nzg5MCIsInppZCI6InRlbmFudC1pZC12YWx1ZSJ9.qcb3jez9HAjWYC7A-BC51MNgSpbBDMHERNsRg64_LV0",
                    mockSdmCredentials
            );
            // Assert the result
            assertEquals("mockedToken", result); // Adjust based on the expected result
        } catch (OAuth2ServiceException e) {
            throw new RuntimeException(e);
        }
    }
}
