package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentMarkAsDeletedEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentReadEventContext;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentRestoreEventContext;

import java.io.IOException;

@ServiceName(value = "*", type = AttachmentService.class)
public class SDMAttachmentsServiceHandler implements EventHandler {
    private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsServiceHandler.class);

    @Before(event = AttachmentService.EVENT_CREATE_ATTACHMENT)
    public void createAttachment(AttachmentCreateEventContext context) throws IOException {
        System.out.println("In create attachment ");
        System.out.println("SDM Attachment Service handler called for creating attachment for entity name: "+
        context.getAttachmentIds());
        logger.info( "SDM Attachment Service handler called for creating attachment for entity name: {}",
                context.getAttachmentEntity().getQualifiedName());
        logger.info( "Other details: {}",
                context.getAttachmentIds());
        logger.info( "DATA: {}",
                context.getData());
        logger.info("Authentication info "+context.getAuthenticationInfo());
        AuthenticationInfo authInfo = context.getAuthenticationInfo();
        JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
        String jwtToken = jwtTokenInfo.getToken();
        logger.info("JWT TOKEN  "+jwtToken);
        logger.info("User info "+context.getUserInfo());
        SDMService sdmService = new SDMServiceImpl();
        sdmService.createDocument(context.getData());
        var contentId = (String) context.getAttachmentIds().get(Attachments.ID);

        context.setIsInternalStored(true);
        context.setContentId(contentId);
        context.setCompleted();
    }

    @On(event = AttachmentService.EVENT_MARK_ATTACHMENT_AS_DELETED)
    public void markAttachmentAsDeleted(AttachmentMarkAsDeletedEventContext context) {

    }

    @On(event = AttachmentService.EVENT_RESTORE_ATTACHMENT)
    public void restoreAttachment(AttachmentRestoreEventContext context) {

    }

    @On(event = AttachmentService.EVENT_READ_ATTACHMENT)
    public void readAttachment(AttachmentReadEventContext context) {
    }

}
