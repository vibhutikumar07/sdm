package com.sap.cds.sdm.handler.applicationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.CdsData;
import com.sap.cds.CdsDataProcessor;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ModifyApplicationHandlerHelper;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ReadonlyDataContextEnhancer;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ThreadDataStorageReader;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.ModifyAttachmentEventFactory;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.feature.attachments.handler.draftservice.constants.DraftConstants;
import com.sap.cds.feature.attachments.utilities.LoggingMarker;
import com.sap.cds.reflect.CdsBaseType;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.OrderConstants;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMCreateEventHandler implements EventHandler {
    private static final Logger logger = LoggerFactory.getLogger(SDMCreateEventHandler.class);

    private final ModifyAttachmentEventFactory eventFactory;
    private final ThreadDataStorageReader storageReader;
    private final PersistenceService persistenceService;
    private final CdsDataProcessor processor = CdsDataProcessor.create();

    public SDMCreateEventHandler(ModifyAttachmentEventFactory eventFactory, ThreadDataStorageReader storageReader,PersistenceService persistenceService) {
        this.eventFactory = eventFactory;
        this.storageReader = storageReader;
        this.persistenceService =persistenceService;
    }

    @Before(event = CqnService.EVENT_CREATE)
    @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES)
    public void processBeforeForDraft(CdsCreateEventContext context, List<CdsData> data) {
        ReadonlyDataContextEnhancer.enhanceReadonlyDataInContext(context, data, storageReader.get());
    }

    @Before(event = CqnService.EVENT_CREATE)
    @HandlerOrder(HandlerOrder.BEFORE)
    public void processBefore(CdsCreateEventContext context, List<CdsData> data) throws IOException {
          doCreate(context, data);
    }

    private void doCreate(CdsCreateEventContext context, List<CdsData> data) throws IOException {
        AuthenticationInfo authInfo = context.getAuthenticationInfo();
        JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
        String jwtToken = jwtTokenInfo.getToken();
        String up__ID= getUP__ID(data);
String duplicateFiles = getDuplicateFileNames(data);
if(duplicateFiles !=null ){
    context.getMessages().error(String.format(SDMConstants.DUPLICATE_FILES_ERROR, duplicateFiles));
}
        List<String> failedIds = createDocument(data, jwtToken, context, up__ID);
        for (Map<String, Object> entity : data) {
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
            if (attachments != null) {
                Iterator<Map<String, Object>> iterator = attachments.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> attachment = iterator.next();
                    String checkId = (String) attachment.get("ID"); // Ensure appropriate cast to String
                    if (failedIds.contains(checkId)) {
                        iterator.remove();
                    }
                }
            }
        }

        if (ApplicationHandlerHelper.noContentFieldInData(context.getTarget(), data)) {
            return;
        }

        setKeysInData(context.getTarget(), data);
        ModifyApplicationHandlerHelper.handleAttachmentForEntities(context.getTarget(), data, new ArrayList<>(), eventFactory,
                context);
    }
    private String getDuplicateFileNames(List<CdsData> data) {
        List<String> fileNames = getFilesNamesFromInput(data);
        Set<String> uniqueFileNames = new HashSet<>();
        Set<String> duplicateFileNames = new HashSet<>();

        for (String fileName : fileNames) {
            if (!uniqueFileNames.add(fileName)) {
                duplicateFileNames.add(fileName);
            }
        }
        if(duplicateFileNames.size()>0){
            // Join the list of existing filenames with commas
            return String.join(",", duplicateFileNames);
        }
        else
        return  null;
    }
 
    private void setKeysInData(CdsEntity entity, List<CdsData> data) {
        processor.addGenerator((path, element, type) -> element.isKey() && element.getType().isSimpleType(CdsBaseType.UUID),
                (path, element, isNull) -> UUID.randomUUID().toString()).process(data, entity);
    }

    private List<String> createDocument(List<CdsData> data, String jwtToken,CdsCreateEventContext context,String up__ID) throws IOException {
        String repositoryId = SDMConstants.REPOSITORY_ID; //Getting repository ID
        SDMService sdmService = new SDMServiceImpl();
        List<String> failedIds = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();

        //Checking to see if repository is versioned
        String repocheck = sdmService.checkRepositoryType(repositoryId);
        if("Versioned".equals(repocheck)){
            context.getMessages().error("Upload not supported for versioned repositories");
        }

        List<CmisDocument> cmisDocuments =  new ArrayList<>();
        CdsModel model = context.getModel();
        Optional<CdsEntity> attachmentEntity =
                model.findEntity(context.getTarget().getQualifiedName() + ".attachments");


        List<Map<String, Object>> attachments = new ArrayList<>();
        List<String> newFileNames = new ArrayList<>();
        List<String> newFileIds = new ArrayList<>();
        for (Map<String, Object> entity : data) {
            attachments = (List<Map<String, Object>>) entity.get("attachments");
            if (attachments != null) {
                for (Map<String, Object> attachment : attachments) {
                    attachment.remove("DRAFT_READONLY_CONTEXT");
                    if (attachment.get("url") == null) {
                        newFileNames.add(attachment.get("fileName").toString());
                        newFileIds.add(attachment.get("ID").toString());
                    }
                }
            }
        }

        for (int i = 0; i < newFileNames.size(); i++) {
            for (int j = i + 1; j < newFileNames.size(); j++) {
                if (newFileNames.get(i).equals(newFileNames.get(j))) {
                    failedIds.add(newFileIds.get(j));
                    failedNames.add(newFileNames.get(j));
                }
            }
        }
        Boolean flag = false;
        if(failedIds.isEmpty()) {//Getting folder id to upload attachment in
            flag = true;
            String folderId = null;
            try {
                folderId = sdmService.getFolderId(jwtToken, attachmentEntity.get(), persistenceService, up__ID);
            } catch (Exception e) {
                context.getMessages().warn("Error in upload");
            }

            List<String> otherFailedDocuments = new ArrayList<>();
            List<String> duplicateDocuments = new ArrayList<>();
            List<String> incompleteDocuments = new ArrayList<>();
            List<String> virusDocuments = new ArrayList<>();

            if (attachments != null) {
                Map<String, Object> firstAttachment = attachments.get(0);
                String parentId = firstAttachment.get("up__ID").toString();
                for (Map<String, Object> attachment : attachments) {
                    CmisDocument cmisDocument = new CmisDocument();
                    cmisDocument.setFileName(attachment.get("fileName").toString());
                    cmisDocument.setAttachmentId(attachment.get("ID").toString());
                    InputStream contentStream = (InputStream) attachment.get("content");
                    cmisDocument.setContent(contentStream);
                    cmisDocument.setParentId(parentId);
                    cmisDocument.setRepositoryId(repositoryId);
                    cmisDocument.setFolderId(folderId);

                    if (cmisDocument.getContent() == null) {
                        cmisDocument.setStatus("Incomplete");
                        incompleteDocuments.add(cmisDocument.getFileName());
                        failedIds.add(cmisDocument.getAttachmentId());
                    } else {
                        JSONObject result = sdmService.createDocument(cmisDocument, jwtToken);
                        if (result.has("duplicate") && result.getBoolean("duplicate")) {
                            cmisDocument.setStatus("Duplicate");
                            String duplicateName = result.optString("failedDocument");
                            duplicateDocuments.add(duplicateName);
                            failedIds.add(result.optString("id"));
                        } else if (result.has("virus") && result.getBoolean("virus")) {
                            cmisDocument.setStatus("Virus");
                            String virusName = result.optString("failedDocument");
                            virusDocuments.add(virusName);
                            failedIds.add(result.optString("id"));
                        } else if (result.has("fail") && result.getBoolean("fail")) {
                            cmisDocument.setStatus("Other");
                            String fileName = result.optString("failedDocument");
                            otherFailedDocuments.add(fileName);
                            failedIds.add(result.optString("id"));
                        } else {
                            cmisDocument.setStatus("Success");
                            attachment.put("folderId", folderId);
                            attachment.put("repositoryId", repositoryId);
                            attachment.put("url", result.optString("url"));
                            cmisDocument.setObjectId(result.getString("url"));
                        }
                    }
                }

                StringBuilder error = new StringBuilder();
                if (!duplicateDocuments.isEmpty()) {
                    error.append("The following files already exist and cannot be uploaded:\n");
                    for (String duplicateDocument : duplicateDocuments) {
                        error.append("• ").append(duplicateDocument).append("\n");
                    }
                }
                if (!virusDocuments.isEmpty()) {
                    error.append("The following files contain potential malware and cannot be uploaded:\n");
                    for (String virusDocument : virusDocuments) {
                        error.append("• ").append(virusDocument).append("\n");
                    }
                }
                if (!otherFailedDocuments.isEmpty()) {
                    error.append("The following files cannot be uploaded:\n");
                    for (String otherDocument : otherFailedDocuments) {
                        error.append("• ").append(otherDocument).append("\n");
                    }
                }
                if (error.length() > 0) {
                    context.getMessages().warn(error.toString());
                }
            }
        }
        if(flag == false){
            StringBuilder error = new StringBuilder();
            error.append("The following files have been added multiple times. Please remove them and try again:\n");
            for (String failName : failedNames) {
                error.append("• ").append(failName).append("\n");
            }
            context.getMessages().error(error.toString());
        }
        return failedIds;
    }
    private String getUP__ID(List<CdsData> data) {
        for (Map<String, Object> entity : data) {

            // Handle attachments if present
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
            if (attachments != null) {
                for (Map<String, Object> attachment : attachments) {
                    return attachment.get("up__ID").toString();
                }

            }
        }
        return null;
    }

    private List<String> getFilesNamesFromInput(List<CdsData> data) {
        List<String> fileNames = new ArrayList<>();
        for (Map<String, Object> entity : data) {

            // Handle attachments if present
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
            if (attachments != null) {
                for (Map<String, Object> attachment : attachments) {
                    fileNames.add( attachment.get("fileName").toString());
                }

            }
        }
        return fileNames;
    }

}