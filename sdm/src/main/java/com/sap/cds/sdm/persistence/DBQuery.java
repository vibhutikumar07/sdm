package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;

public class DBQuery {
    public static Result getAttachmentsForUP__ID(CdsEntity attachmentEntity, PersistenceService persistenceService, String up__ID){
        CqnSelect q = Select.from(attachmentEntity).columns("fileName", "ID","IsActiveEntity","folderId","repositoryId").where(doc -> doc.get("up__ID").eq(up__ID));
     return persistenceService.run(q);
    }
}
