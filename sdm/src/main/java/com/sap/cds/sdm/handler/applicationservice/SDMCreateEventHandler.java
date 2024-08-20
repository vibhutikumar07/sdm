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
    @HandlerOrder(HandlerOrder.LATE)
    public void processBefore(CdsCreateEventContext context, List<CdsData> data) throws IOException {
        doCreate(context, data);
    }

    private void doCreate(CdsCreateEventContext context, List<CdsData> data) throws IOException {
        AuthenticationInfo authInfo = context.getAuthenticationInfo();
        JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
        String jwtToken = jwtTokenInfo.getToken();
        String up__ID= getUP__ID(data);
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

    private void setKeysInData(CdsEntity entity, List<CdsData> data) {
        processor.addGenerator((path, element, type) -> element.isKey() && element.getType().isSimpleType(CdsBaseType.UUID),
                (path, element, isNull) -> UUID.randomUUID().toString()).process(data, entity);
    }

    private List<String> createDocument(List<CdsData> data, String jwtToken,CdsCreateEventContext context,String up__ID) throws IOException {
        String repositoryId = SDMConstants.REPOSITORY_ID; //Getting repository ID

        SDMService sdmService = new SDMServiceImpl();

        //Checking to see if repository is versioned
        String repocheck = sdmService.checkRepositoryType(repositoryId);
        if("Versioned".equals(repocheck)){
            context.getMessages().error("Upload not supported for versioned repositories");
        }

        List<CmisDocument> cmisDocuments =  new ArrayList<>();
        CdsModel model = context.getModel();
        Optional<CdsEntity> attachmentEntity =
                model.findEntity(context.getTarget().getQualifiedName() + ".attachments");

        //Checking to see if duplicate files exist in attachments
        // Result duplicateFiles = getDuplicateFiles(attachmentEntity, persistenceService);
        // List<Row> rows = duplicateFiles.listOf(Row.class);
        // if (!rows.isEmpty()) {
        //     StringBuilder sb = new StringBuilder("The following files could not be uploaded as they already exist:\n");
        //     for (Row row : rows) {
        //         String fileName = row.getString("fileName");
        //         sb.append("• ").append(fileName).append("\n");
        //     }
        //     context.getMessages().warn(sb.toString());
        // }

        //Getting folder id to upload attachment in
        String folderId = sdmService.getFolderId(jwtToken,attachmentEntity.get(),persistenceService,up__ID);

        List<String> duplicateDocuments = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        for (Map<String, Object> entity : data) {
            // Handle attachments if present
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");
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

                    JSONObject result = sdmService.createDocument(cmisDocument, jwtToken);
                    if (result.has("duplicate") && result.getBoolean("duplicate")) {
                        cmisDocument.setStatus("Duplicate");
                        String duplicateName = result.optString("failedDocument");
                        duplicateDocuments.add(duplicateName);
                        failedIds.add(result.optString("id"));
                    }
                    else{
                        cmisDocument.setStatus("Success");
                        attachment.put("folderId",folderId);
                        attachment.put("repositoryId",repositoryId);
                        attachment.put("url",result.optString("url"));
                        cmisDocument.setObjectId(result.getString("url"));
                    }
                }
                if (!duplicateDocuments.isEmpty()) {
                    StringBuilder sb = new StringBuilder("The following files could not be uploaded as they already exist:\n");
                    for (String duplicateDocument : duplicateDocuments) {
                        sb.append("• ").append(duplicateDocument).append("\n");
                    }
                    context.getMessages().warn(sb.toString());
                }
            }
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

}