package com.sap.cds.sdm.service.handler;

import static com.sap.cds.sdm.persistence.DBQuery.addAttachmentToDraft;
import static com.sap.cds.sdm.persistence.DBQuery.deleteAttachmentFromDraft;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = AttachmentService.class)
public class SDMAttachmentsServiceHandler implements EventHandler {
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsServiceHandler.class);
  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMAttachmentsServiceHandler(
      PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
  }

  @On(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
  public void createAttachment(AttachmentCreateEventContext context) throws IOException {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    System.out.println("Repo Id " + repositoryId);
    String repocheck = sdmService.checkRepositoryType(repositoryId);
    CmisDocument cmisDocument = new CmisDocument();
    if ("Versioned".equals(repocheck)) {
      context.getMessages().error("Upload not supported for versioned repositories");
    } else {
      Map<String, Object> attachmentIds = context.getAttachmentIds();
      String up__ID = (String) attachmentIds.get("up__ID");
      CdsModel model = context.getModel();
      Optional<CdsEntity> attachmentDraftEntity =
          model.findEntity(context.getAttachmentEntity() + "_drafts");
      Result result =
          DBQuery.getAttachmentsForUP__ID(attachmentDraftEntity.get(), persistenceService, up__ID);
      System.out.println("Result DB : " + result);

      String contentId = context.getContentId();
      CdsEntity attachmentEntity = context.getAttachmentEntity();
      MediaData data = context.getData();
      Boolean isInternalStored = context.getIsInternalStored();

      System.out.println("Content ID: " + contentId);
      System.out.println("Attachment IDs: " + attachmentIds);
      System.out.println("Attachment Entity: " + attachmentEntity);
      System.out.println("Media Data: " + data);
      System.out.println("Is Internal Stored: " + isInternalStored);

      String filename = (String) data.get("fileName");
      String fileid = (String) attachmentIds.get("ID");

      Boolean duplicate = duplicateCheck(filename, fileid, result);
      if (duplicate) {
        System.out.println("Duplicate error");
        deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
        context
            .getMessages()
            .warn("This attachment already exists. Please remove it and try again");
      } else {
        AuthenticationInfo authInfo = context.getAuthenticationInfo();
        JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
        String jwtToken = jwtTokenInfo.getToken();
        System.out.println("TOKEN " + jwtToken);
        String folderId =
            sdmService.getFolderId(
                jwtToken, attachmentDraftEntity.get(), persistenceService, up__ID);
        cmisDocument.setFileName(filename);
        cmisDocument.setAttachmentId(fileid);
        InputStream contentStream = (InputStream) data.get("content");
        cmisDocument.setContent(contentStream);
        cmisDocument.setParentId((String) attachmentIds.get("up__ID"));
        cmisDocument.setRepositoryId(repositoryId);
        cmisDocument.setFolderId(folderId);
        SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
        JSONObject createResult = sdmService.createDocument(cmisDocument, jwtToken, sdmCredentials);
        System.out.println("Result : " + createResult);

        StringBuilder error = new StringBuilder();
        if (createResult.get("status") == "duplicate") {
          deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
          error.append("The following files already exist and cannot be uploaded:\n");
          error.append("• ").append(createResult.get("name")).append("\n");
          System.out.println(error);
        } else if (createResult.get("status") == "virus") {
          deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
          error.append("The following files contain potential malware and cannot be uploaded:\n");
          error.append("• ").append(createResult.get("name")).append("\n");
        } else if (createResult.get("status") == "fail") {
          deleteAttachmentFromDraft(attachmentDraftEntity.get(), persistenceService, fileid);
          error.append("The following files cannot be uploaded:\n");
          error.append("• ").append(createResult.get("name")).append("\n");
        } else {
          System.out.println("URL : " + createResult.get("url"));
          cmisDocument.setObjectId(createResult.get("url").toString());
        }
        System.out.println("Status : " + createResult.get("status"));
        addAttachmentToDraft(attachmentDraftEntity.get(), persistenceService, cmisDocument);
      }
    }
    var contentId = (String) context.getAttachmentIds().get(Attachments.ID);
    context.getData().setStatus("Clean");
    context.setContentId(
        cmisDocument.getObjectId()
            + ":"
            + cmisDocument.getFolderId()
            + ":"
            + context.getUserInfo().getName());
    context.getData().setContent(null);
    context.setCompleted();
  }

  @Before(event = AttachmentService.EVENT_MARK_ATTACHMENT_AS_DELETED)
  public void markAttachmentAsDeleted(AttachmentMarkAsDeletedEventContext context)
      throws IOException {
    System.out.println("Delete called..." + context.getContentId());
    String[] Ids = context.getContentId().split(":");
    String objectId = Ids[0];
    String folderId = Ids[1];
    String useremail = Ids[2];
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    System.out.println("sdmCredentials " + sdmCredentials);
    // String token = TokenHandler.getAccessTokenForUser(sdmCredentials, useremail);
    String token =
        "eyJhbGciOiJSUzI1NiIsImprdSI6Imh0dHBzOi8vc2RtZ29vZ2xld29ya3NwYWNlLmF1dGhlbnRpY2F0aW9uLmV1MTIuaGFuYS5vbmRlbWFuZC5jb20vdG9rZW5fa2V5cyIsImtpZCI6ImRlZmF1bHQtand0LWtleS00OTI0NjkyNTMiLCJ0eXAiOiJKV1QiLCJqaWQiOiAiQUphOEZzRzB6aU9PbWlCM0JKN01jb0x3eW5LY2VCaVpOQ2pobmMwR3hFbz0ifQ.eyJqdGkiOiJkNWE3ZjJiMzJkZTc0YTA0YTU3YjU3ZTJhZmI0YmMyZCIsImF6X2F0dHIiOnsiWC1FY21Vc2VyRW5jIjoicmFzaG1pLmFuZ2FkaUBzYXAuY29tIiwiWC1FY21BZGRQcmluY2lwYWxzIjoiUmFzaG1pIn0sImV4dF9hdHRyIjp7ImVuaGFuY2VyIjoiWFNVQUEiLCJzdWJhY2NvdW50aWQiOiJiMmI3MDFiZS0wOGUzLTRiMzktOGUyOS03OWIyMWRkNzE2NjQiLCJ6ZG4iOiJzZG1nb29nbGV3b3Jrc3BhY2UiLCJzZXJ2aWNlaW5zdGFuY2VpZCI6ImEwMzgyNmUzLWIxOWMtNDYwMi1iODgyLTQ4NmY3NmY3ZWQyZCJ9LCJzdWIiOiJzYi1hMDM4MjZlMy1iMTljLTQ2MDItYjg4Mi00ODZmNzZmN2VkMmQhYjUyNTZ8c2RtLWRpLURvY3VtZW50TWFuYWdlbWVudC1zZG1faW50ZWdyYXRpb24hYjI0NyIsImF1dGhvcml0aWVzIjpbInNkbS1kaS1Eb2N1bWVudE1hbmFnZW1lbnQtc2RtX2ludGVncmF0aW9uIWIyNDcuc2RtbWlncmF0aW9uYWRtaW4iLCJzZG0tZGktRG9jdW1lbnRNYW5hZ2VtZW50LXNkbV9pbnRlZ3JhdGlvbiFiMjQ3LnNkbWFkbWluIiwic2RtLWRpLURvY3VtZW50TWFuYWdlbWVudC1zZG1faW50ZWdyYXRpb24hYjI0Ny5zZG11c2VyIiwidWFhLnJlc291cmNlIiwic2RtLWRpLURvY3VtZW50TWFuYWdlbWVudC1zZG1faW50ZWdyYXRpb24hYjI0Ny5zZG1idXNpbmVzc2FkbWluIl0sInNjb3BlIjpbInNkbS1kaS1Eb2N1bWVudE1hbmFnZW1lbnQtc2RtX2ludGVncmF0aW9uIWIyNDcuc2RtdXNlciIsInVhYS5yZXNvdXJjZSIsInNkbS1kaS1Eb2N1bWVudE1hbmFnZW1lbnQtc2RtX2ludGVncmF0aW9uIWIyNDcuc2RtYnVzaW5lc3NhZG1pbiIsInNkbS1kaS1Eb2N1bWVudE1hbmFnZW1lbnQtc2RtX2ludGVncmF0aW9uIWIyNDcuc2RtbWlncmF0aW9uYWRtaW4iLCJzZG0tZGktRG9jdW1lbnRNYW5hZ2VtZW50LXNkbV9pbnRlZ3JhdGlvbiFiMjQ3LnNkbWFkbWluIl0sImNsaWVudF9pZCI6InNiLWEwMzgyNmUzLWIxOWMtNDYwMi1iODgyLTQ4NmY3NmY3ZWQyZCFiNTI1NnxzZG0tZGktRG9jdW1lbnRNYW5hZ2VtZW50LXNkbV9pbnRlZ3JhdGlvbiFiMjQ3IiwiY2lkIjoic2ItYTAzODI2ZTMtYjE5Yy00NjAyLWI4ODItNDg2Zjc2ZjdlZDJkIWI1MjU2fHNkbS1kaS1Eb2N1bWVudE1hbmFnZW1lbnQtc2RtX2ludGVncmF0aW9uIWIyNDciLCJhenAiOiJzYi1hMDM4MjZlMy1iMTljLTQ2MDItYjg4Mi00ODZmNzZmN2VkMmQhYjUyNTZ8c2RtLWRpLURvY3VtZW50TWFuYWdlbWVudC1zZG1faW50ZWdyYXRpb24hYjI0NyIsImdyYW50X3R5cGUiOiJjbGllbnRfY3JlZGVudGlhbHMiLCJyZXZfc2lnIjoiZjVmZDg1NWIiLCJpYXQiOjE3MjU2NDExNzUsImV4cCI6MTcyNTY4NDM3NSwiaXNzIjoiaHR0cHM6Ly9zZG1nb29nbGV3b3Jrc3BhY2UuYXV0aGVudGljYXRpb24uZXUxMi5oYW5hLm9uZGVtYW5kLmNvbS9vYXV0aC90b2tlbiIsInppZCI6IjczNGJmZmJlLTkwY2QtNDEzMC04MjY1LTQ1Y2FlZWJkMTkzYyIsImF1ZCI6WyJ1YWEiLCJzYi1hMDM4MjZlMy1iMTljLTQ2MDItYjg4Mi00ODZmNzZmN2VkMmQhYjUyNTZ8c2RtLWRpLURvY3VtZW50TWFuYWdlbWVudC1zZG1faW50ZWdyYXRpb24hYjI0NyIsInNkbS1kaS1Eb2N1bWVudE1hbmFnZW1lbnQtc2RtX2ludGVncmF0aW9uIWIyNDciXX0.YIPKPxNBZ4lsbU0E3JixznDOXipyLnbj8sNu_GB6nTO7TWhqbAVjf9_rIP-_6MiQ9_B10_6NXkifC7dJ5B4BVUxHu_VUzYLXH-_3Hz8B5Epcph4SPuTSH2-ajE7dPBCtxP862MGjmg9TKH-Ed40RiXNDhmuHG0OL3R3bJsDJsf6ofs7BoADS48nW_Vtzrhmw_sUYe2ud4lSg1NSXdEgnDowRLbRtA_t1nRYfhN5Nv2uJNf_OXgsGMjGPHfnJPkNqMleTRZVB2NSDxIM6HiuoOngB0a293w04QQ1qOHuyp9b5pnh5eLpfZfiQWxhdCjCLSMNIRMDG1fFfgEhyTgyFRA";
    int response = sdmService.deleteDocument(objectId, token, "delete");
    System.out.println("Response from Delete " + response);
    Stream<CdsEntity> entityStream = context.getModel().entities();

    // entityStream.forEach(
    //     entity -> {
    //       System.out.println("Entity: " + entity.getName());
    //       if (entity.getName().contains("attachments")) {
    //         Optional<CdsEntity> attachmentDraftEntity =
    //             context.getModel().findEntity(entity.getQualifiedName());
    //         Result result =
    //             DBQuery.isFolderExisting(attachmentDraftEntity.get(), persistenceService,
    // folderId);
    //         if (result.list().size() == 0) {
    //           try {
    //             sdmService.deleteDocument(folderId, token, "deleteTree");
    //           } catch (OAuth2ServiceException e) {
    //             // TODO Auto-generated catch block
    //             e.printStackTrace();
    //           }
    //         }
    //       }

    //       // Perform any additional processing you need with the entity
    //     });
    // check if all attachments within folder are deleted if yes then delete the folder
    JSONObject getChildrenResponse = sdmService.getChildren(folderId, token);
    System.out.println("Response from getChildrenResponse " + getChildrenResponse.get("objects"));
    JSONArray docCount = (JSONArray) getChildrenResponse.get("objects");
    if (docCount.length() == 0) sdmService.deleteDocument(folderId, token, "deleteTree");
  }

  @On(event = AttachmentService.EVENT_RESTORE_ATTACHMENT)
  public void restoreAttachment(AttachmentRestoreEventContext context) {}

  @On(event = AttachmentService.EVENT_READ_ATTACHMENT)
  public void readAttachment(AttachmentReadEventContext context) {
    System.out.println("In read method " + context.getContentId());
  }

  public boolean duplicateCheck(String filename, String fileid, Result result) {

    List<Map<String, Object>> resultList =
        result.listOf(Map.class).stream()
            .map(map -> (Map<String, Object>) map)
            .collect(Collectors.toList());

    Map<String, Object> duplicate = null;
    for (Map<String, Object> attachment : resultList) {
      String resultFileName = (String) attachment.get("fileName");
      String resultId = (String) attachment.get("ID");
      if (filename.equals(resultFileName) && !fileid.equals(resultId)) {
        duplicate = attachment;
        break;
      }
    }

    if (duplicate != null) {
      return true;
    } else {
      return false;
    }
  }
}
