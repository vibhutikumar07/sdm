package com.sap.cds.sdm.handler.applicationservice;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.ModifyAttachmentEventFactory;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.*;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import com.sap.cds.CdsData;
import com.sap.cds.CdsDataProcessor.Validator;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ModifyApplicationHandlerHelper;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ReadonlyDataContextEnhancer;
import com.sap.cds.feature.attachments.handler.applicationservice.helper.ThreadDataStorageReader;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.feature.attachments.handler.common.AttachmentsReader;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.utilities.LoggingMarker;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.utils.OrderConstants;
import com.sap.cds.services.utils.model.CqnUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@ServiceName(value = "*", type = ApplicationService.class)
public class SDMUpdateEventHandler implements EventHandler {

    private static final Logger logger = LoggerFactory.getLogger(SDMUpdateEventHandler.class);
    private static final Marker marker = LoggingMarker.APPLICATION_UPDATE_HANDLER.getMarker();

    private final ModifyAttachmentEventFactory eventFactory;
    private final ThreadDataStorageReader storageReader;
    private final  AttachmentsReader attachmentsReader;
    private final PersistenceService persistenceService;

    public SDMUpdateEventHandler(ModifyAttachmentEventFactory eventFactory,AttachmentsReader attachmentsReader,
                                    ThreadDataStorageReader storageReader,PersistenceService persistenceService) {
        this.eventFactory = eventFactory;
        this.attachmentsReader = attachmentsReader;
        this.storageReader = storageReader;
        this.persistenceService = persistenceService;
    }

    @Before(event = CqnService.EVENT_UPDATE)
    @HandlerOrder(OrderConstants.Before.CHECK_CAPABILITIES)
    public void processBeforeForDraft(CdsUpdateEventContext context, List<CdsData> data) {
        ReadonlyDataContextEnhancer.enhanceReadonlyDataInContext(context, data, storageReader.get());
    }

    @Before(event = CqnService.EVENT_UPDATE)
    @HandlerOrder(HandlerOrder.LATE)
    public void processBefore(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
        doUpdate(context, data);
    }

    private void doUpdate(CdsUpdateEventContext context, List<CdsData> data) throws IOException {
        logger.info("In update event handler sdm"+data);
        System.out.println("In update event handler sdm"+data);
        AuthenticationInfo authInfo = context.getAuthenticationInfo();
        JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
        String jwtToken = jwtTokenInfo.getToken();
        logger.info("SDM token "+jwtToken);
        logger.info("PK "+context.getTarget());
        String up__ID=getUP__ID(data);
        List<CmisDocument> cmisDocuments = createDocument(data, jwtToken,context,up__ID);
        System.out.println("DONE");
        var target = context.getTarget();
        var noContentInData = ApplicationHandlerHelper.noContentFieldInData(target, data);
        var associationsAreUnchanged = associationsAreUnchanged(target, data);
        if (noContentInData && associationsAreUnchanged) {
            return;
        }

        logger.debug(marker, "Processing before update event for entity {}", target.getName());

        var select = getSelect(context.getCqn(), context.getTarget());
        var attachments = attachmentsReader.readAttachments(context.getModel(), target, select);

        var condensedAttachments = ApplicationHandlerHelper.condenseData(attachments, target);
        ModifyApplicationHandlerHelper.handleAttachmentForEntities(target, data, condensedAttachments, eventFactory, context);

        if (!associationsAreUnchanged) {
            deleteRemovedAttachments(attachments, data, target);
        }
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

    private boolean associationsAreUnchanged(CdsEntity entity, List<CdsData> data) {
        return entity.compositions().noneMatch(
                association -> data.stream().anyMatch(d -> d.containsKey(association.getName())));
    }

    private CqnFilterableStatement getSelect(CqnUpdate update, CdsEntity target) {
        return CqnUtils.toSelect(update, target);
    }

    private void deleteRemovedAttachments(List<CdsData> exitingDataList, List<CdsData> updatedDataList, CdsEntity entity) {
        var condensedUpdatedData = ApplicationHandlerHelper.condenseData(updatedDataList, entity);
        var filter = ApplicationHandlerHelper.buildFilterForMediaTypeEntity();
        Validator validator = (path, element, value) -> {
            var keys = ApplicationHandlerHelper.removeDraftKeys(path.target().keys());
            var entryExists = condensedUpdatedData.stream().anyMatch(
                    updatedData -> ApplicationHandlerHelper.areKeysInData(keys, updatedData));

        };
        ApplicationHandlerHelper.callValidator(entity, exitingDataList, filter, validator);
    }
    private List<CmisDocument>  createDocument(List<CdsData> data, String jwtToken,CdsUpdateEventContext context,String up__ID) throws IOException {
        String repositoryId = SDMConstants.REPOSITORY_ID;
        List<CmisDocument> cmisDocuments =  new ArrayList<>();
        CdsModel model = context.getModel();
        Optional<CdsEntity> attachmentEntity =
                model.findEntity(context.getTarget().getQualifiedName() + ".attachments");
        Result  result = DBQuery.getAttachmentsForUP__ID(attachmentEntity.get(),persistenceService,up__ID);
        System.out.println("Result  " + result);
        List<String> idsToRemove = new ArrayList<>();
        for (Row row : result.list()) {
            String id = row.get("ID").toString();
            idsToRemove.add(id);
        }
        SDMService sdmService = new SDMServiceImpl();
        String folderId = sdmService.getFolderId(jwtToken, attachmentEntity.get(), persistenceService, up__ID);

        for (Map<String, Object> entity : data) {

            // Handle attachments if present
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) entity.get("attachments");


            System.out.println("Attachments "+attachments);
            if (attachments != null) {
                for (Map<String, Object> attachment : attachments) {
                    if (idsToRemove.contains(attachment.get("ID").toString())) {
                        attachments.remove(attachment);
                    }
                    System.out.println("ATT  " + attachment);
                                CmisDocument cmisDocument = new CmisDocument();
                                cmisDocument.setFileName(attachment.get("fileName").toString());
                                InputStream contentStream = (InputStream) attachment.get("content");
                                cmisDocument.setContent(contentStream);
                                cmisDocument.setRepositoryId(repositoryId);


                                cmisDocument.setParentId(attachment.get("up__ID").toString());
                                System.out.println("COBTENT " + contentStream);

                                cmisDocument.setFolderId(folderId);
                                String objectId = sdmService.createDocument(cmisDocument, jwtToken);
                    attachment.put("folderId",folderId);
                    attachment.put("repositoryId",repositoryId);
                    attachment.put("url",objectId);
                                cmisDocument.setObjectId(objectId);
                                cmisDocuments.add(cmisDocument);

                }
            }

        }
        return cmisDocuments;
    }
}

