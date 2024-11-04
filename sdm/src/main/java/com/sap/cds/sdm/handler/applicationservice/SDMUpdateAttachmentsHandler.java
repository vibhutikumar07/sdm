package com.sap.cds.sdm.handler.applicationservice;

import static com.sap.cds.sdm.persistence.DBQuery.*;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMUpdateAttachmentsHandler implements EventHandler {

  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMUpdateAttachmentsHandler(PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
  }

  // @Before(event = CqnService.EVENT_UPDATE)
  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void processBefore(CdsUpdateEventContext context, List<CdsData> data) {
    try {
      rename(context, data);
    } catch (IOException e) {
      context.getMessages().error("Error renaming attachment");
    }
  }

  public void rename(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
    Set<String> duplicateFilenames = isFileNameDuplicateInDrafts(context, data);
    if (!duplicateFilenames.isEmpty()) {
      context
          .getMessages()
          .error(
              "The file(s) "
                  + String.join(", ", duplicateFilenames)
                  + " have been added multiple times. Please rename and try again.");
    } else {
      List<String> duplicateFileNameList = new ArrayList<>();
      Optional<CdsEntity> attachmentEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + ".attachments");

      for (Map<String, Object> entity : data) {
        List<Map<String, Object>> attachments =
            (List<Map<String, Object>>) entity.get("attachments");
        if (attachments != null) {
          Iterator<Map<String, Object>> iterator = attachments.iterator();
          while (iterator.hasNext()) {
            Map<String, Object> attachment = iterator.next();
            String id = (String) attachment.get("ID"); // Ensure appropriate cast to String
            String filenameInRequest = (String) attachment.get("fileName");
            String objectId = (String) attachment.get("url");

            Result result =
                DBQuery.getAttachmentForID(attachmentEntity.get(), persistenceService, id);
            List<Map<String, Object>> resultList =
                result.listOf(Map.class).stream()
                    .map(map -> (Map<String, Object>) map)
                    .collect(Collectors.toList());
            String fileNameInSDM = null;
            AuthenticationInfo authInfo = context.getAuthenticationInfo();
            JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
            String jwtToken = jwtTokenInfo.getToken();
            SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
            if (resultList.isEmpty()) {
              fileNameInSDM = sdmService.getObject(jwtToken, objectId, sdmCredentials);
            } else {
              for (Map<String, Object> entry : resultList) {
                if (entry.get("fileName") != null) {
                  fileNameInSDM = entry.get("fileName").toString();
                }
              }
            }
            if (fileNameInSDM != null && !fileNameInSDM.equals(filenameInRequest)) {
              int responseCode =
                  sdmService.renameAttachments(
                      jwtToken, sdmCredentials, filenameInRequest, objectId);
              if (responseCode == 409) {
                duplicateFileNameList.add(filenameInRequest);
                attachment.replace("fileName", fileNameInSDM);
              }
            }
          }
        }
      }
      if (!duplicateFileNameList.isEmpty()) {
        context
            .getMessages()
            .warn(
                "The following files could not be renamed as they already exist:\n"
                    + String.join(", ", duplicateFileNameList)
                    + "\n");
      }
    }
  }

  public Set<String> isFileNameDuplicateInDrafts(
      CdsUpdateEventContext context, List<CdsData> data) {
    Set<String> uniqueFilenames = new HashSet<>();
    Set<String> duplicateFilenames = new HashSet<>();
    for (Map<String, Object> entity : data) {
      List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
      if (attachments != null) {
        Iterator<Map<String, Object>> iterator = attachments.iterator();
        while (iterator.hasNext()) {
          Map<String, Object> attachment = iterator.next();
          String filenameInRequest = (String) attachment.get("fileName");
          if (!uniqueFilenames.add(filenameInRequest)) {
            duplicateFilenames.add(filenameInRequest);
          }
        }
      }
    }
    return duplicateFilenames;
  }
}
