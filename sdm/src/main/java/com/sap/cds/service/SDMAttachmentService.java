/**************************************************************************
 * (C) 2019-2024 SAP SE or an SAP affiliate company. All rights reserved. *
 **************************************************************************/
package com.sap.cds.service;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.service.AttachmentModificationResult;
import com.sap.cds.feature.attachments.service.model.service.CreateAttachmentInput;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.auditlog.Attachment;
import com.sap.cds.services.handler.Handler;

import java.io.InputStream;
import java.time.Instant;


public class SDMAttachmentService implements AttachmentService {
    @Override
    public InputStream readAttachment(String s) {
        return null;
    }

    @Override
    public AttachmentModificationResult createAttachment(CreateAttachmentInput createAttachmentInput) {
        //first create
        return null;
    }

    @Override
    public void markAttachmentAsDeleted(String s) {

    }

    @Override
    public void restoreAttachment(Instant instant) {

    }

    @Override
    public void before(String[] events, String[] entities, int order, Handler handler) {

    }

    @Override
    public void on(String[] events, String[] entities, int order, Handler handler) {

    }

    @Override
    public void after(String[] events, String[] entities, int order, Handler handler) {

    }

    @Override
    public void emit(EventContext context) {

    }

    @Override
    public String getName() {
        return null;
    }
}
