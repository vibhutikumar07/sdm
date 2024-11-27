package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.*;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AttachmentsSDMTest {
  private static String token;
  private static String entityID;
  private static String appUrl;
  private static String serviceName = "AdminService";
  private static String entityName = "Books";
  private static String srvpath = "AdminService";
  private static Api api;
  private static String attachmentID1 = "";
  private static String attachmentID2 = "";
  private static String attachmentID3 = "";

  @BeforeAll
  public static void setup() throws IOException {
    // Define your clientId and clientSecret
    Properties credentialsProperties = Credentials.getCredentials();
    String clientId = credentialsProperties.getProperty("clientID");
    String clientSecret = credentialsProperties.getProperty("clientSecret");
    appUrl = credentialsProperties.getProperty("appUrl");

    // Encode clientId:clientSecret to Base64
    String credentials = clientId + ":" + clientSecret;
    String basicAuth =
        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    OkHttpClient client = new OkHttpClient().newBuilder().build();
    MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");

    // Usually, a GET request should not have a body. For OAuth token requests with
    // client_credentials, it's a POST request.
    RequestBody body = RequestBody.create("", mediaType);

    Request request =
        new Request.Builder()
            .url(
                credentialsProperties.getProperty("authUrl")
                    + "/oauth/token?grant_type=client_credentials")
            .method("POST", body)
            .addHeader("Authorization", basicAuth)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build();

    Response response = client.newCall(request).execute();
    token = new ObjectMapper().readTree(response.body().string()).get("access_token").asText();
    token =
        "sample token";
    response.close();
    Map<String, String> config = new HashMap<>();
    config.put("Authorization", "Bearer " + token);
    api = new Api(config);
  }

  @Test
  @Order(1)
  public void testCreateEntityAndCheck() throws IOException {
    System.out.println("Test (1) : Create entity and check if it exists");
    Boolean testStatus = false;
    String response = api.createEntityDraft(appUrl, serviceName, entityName, srvpath);
    if (response != "Could not create entity") {
      entityID = response;
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, serviceName, entityName, entityID);
        if (response.equals("Entity exists")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not create entity");
    }
  }

  @Test
  @Order(2)
  public void testUpdateEmptyEntity() throws IOException {
    System.out.println("Test (2) : Update an existing entity");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      if (response == "Saved") {
        response = api.checkEntity(appUrl, serviceName, entityName, entityID);
        if (response.equals("Entity exists")) {
          testStatus = true;
        }
      }
    }
    if (!testStatus) {
      fail("Could not update entity");
    }
  }

  @Test
  @Order(3)
  public void testUploadSingleAttachmentPDF() throws IOException {
    System.out.println("Test (3) : Upload pdf");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID1 = createResponse.get(1);
        response =
            api.readAttachmentDraft(appUrl, serviceName, entityName, entityID, attachmentID1);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID1);

            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.pdf " + response);
    }
  }

  @Test
  @Order(4)
  public void testUploadSingleAttachmentTXT() throws IOException {
    System.out.println("Test (4) : Upload txt");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.txt").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/txt");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID2 = createResponse.get(1);
        response =
            api.readAttachmentDraft(appUrl, serviceName, entityName, entityID, attachmentID2);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID2);
            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not upload sample.txt");
    }
  }

  @Test
  @Order(5)
  public void testUploadSingleAttachmentEXE() throws IOException {
    System.out.println("Test (5) : Upload exe");
    Boolean testStatus = false;
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.exe").getFile());

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/exe");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        attachmentID3 = createResponse.get(1);
        response =
            api.readAttachmentDraft(appUrl, serviceName, entityName, entityID, attachmentID3);
        if (response.equals("OK")) {
          response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
          if (response.equals("Saved")) {
            response = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID3);
            if (response.equals("OK")) {
              testStatus = true;
            }
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not create sample.exe");
    }
  }

  @Test
  @Order(6)
  public void testUploadSingleAttachmentPDFDuplicate() throws IOException {
    System.out.println("Test (6) : Upload duplicate pdf");
    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("sample.pdf").getFile());
    Boolean testStatus = false;

    Map<String, Object> postData = new HashMap<>();
    postData.put("up__ID", entityID);
    postData.put("mimeType", "application/pdf");
    postData.put("createdAt", new Date().toString());
    postData.put("createdBy", "test@test.com");
    postData.put("modifiedBy", "test@test.com");

    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      List<String> createResponse =
          api.createAttachment(appUrl, serviceName, entityName, entityID, srvpath, postData, file);
      String check = createResponse.get(0);
      if (check.equals("Attachment created")) {
        testStatus = false;
      } else {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          String expectedJson =
              "{\"error\":{\"code\":\"500\",\"message\":\"sample.pdf already exists.\"}}";
          ObjectMapper objectMapper = new ObjectMapper();
          JsonNode actualJsonNode = objectMapper.readTree(check);
          JsonNode expectedJsonNode = objectMapper.readTree(expectedJson);
          if (expectedJsonNode.equals(actualJsonNode)) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Attachment was created");
    }
  }

  @Test
  @Order(7)
  public void testRenameSingleAttachment() throws IOException {
    System.out.println("Test (7) : Rename single attachment");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String name = "sample123.pdf";
    if (response == "Entity in draft mode") {
      response = api.renameAttachment(appUrl, serviceName, entityID, attachmentID1, name);
      if (response.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      } else {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was not renamed");
    }
  }

  @Test
  @Order(8)
  public void testRenameMultipleAttachments() throws IOException {
    System.out.println("Test (8) : Rename multiple attachments");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    String name1 = "sample1234.pdf";
    String name2 = "sample12345.pdf";
    if (response == "Entity in draft mode") {
      String response1 = api.renameAttachment(appUrl, serviceName, entityID, attachmentID2, name1);
      String response2 = api.renameAttachment(appUrl, serviceName, entityID, attachmentID3, name2);
      if (response1.equals("Renamed") && response2.equals("Renamed")) {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response.equals("Saved")) {
          testStatus = true;
        }
      } else {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
      }
    }
    if (!testStatus) {
      fail("Attachment was not renamed");
    }
  }

  @Test
  @Order(9)
  public void testDeleteSingleAttachment() throws IOException {
    System.out.println("Test (9) : Delete single attachment");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      response = api.deleteAttachment(appUrl, serviceName, entityID, attachmentID1);
      if (response == "Deleted") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response == "Saved") {
          response = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID1);
          if (response.equals("Could not read attachment")) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not delete attachment");
    }
  }

  @Test
  @Order(10)
  public void testDeleteMultipleAttachments() throws IOException {
    System.out.println("Test (10) : Delete multiple attachments");
    Boolean testStatus = false;
    String response = api.editEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
    if (response == "Entity in draft mode") {
      String response1 = api.deleteAttachment(appUrl, serviceName, entityID, attachmentID2);
      String response2 = api.deleteAttachment(appUrl, serviceName, entityID, attachmentID3);
      if (response1 == "Deleted" && response2 == "Deleted") {
        response = api.saveEntityDraft(appUrl, serviceName, entityName, srvpath, entityID);
        if (response == "Saved") {
          response1 = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID2);
          response2 = api.readAttachment(appUrl, serviceName, entityName, entityID, attachmentID3);
          if (response1.equals("Could not read attachment")
              && response2.equals("Could not read attachment")) {
            testStatus = true;
          }
        }
      }
    }
    if (!testStatus) {
      fail("Could not delete attachment");
    }
  }

  @Test
  @Order(11)
  public void testDeleteEntity() throws IOException {
    System.out.println("Test (11) : Delete entity");
    Boolean testStatus = false;
    String response = api.deleteEntity(appUrl, serviceName, entityName, entityID);
    if (response == "Entity Deleted") {
      testStatus = true;
    }
    if (!testStatus) {
      fail("Could not delete entity");
    }
  }
}
