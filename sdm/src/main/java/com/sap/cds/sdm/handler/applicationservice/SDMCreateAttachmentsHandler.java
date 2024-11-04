package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
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
import java.util.Set;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMCreateAttachmentsHandler implements EventHandler {

  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMCreateAttachmentsHandler(PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY)
  public void processBefore(CdsCreateEventContext context, List<CdsData> data) {
    try {
      rename(context, data);
    } catch (IOException e) {
      context.getMessages().error("Error renaming attachment");
    }
  }

  public void rename(CdsCreateEventContext context, List<CdsData> data) throws IOException {
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
            AuthenticationInfo authInfo = context.getAuthenticationInfo();
            JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
            String jwtToken = jwtTokenInfo.getToken();
            SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
            String fileNameInSDM = sdmService.getObject(jwtToken, objectId, sdmCredentials);

            if (fileNameInSDM == null || !fileNameInSDM.equals(filenameInRequest)) {
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
      CdsCreateEventContext context, List<CdsData> data) {
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
