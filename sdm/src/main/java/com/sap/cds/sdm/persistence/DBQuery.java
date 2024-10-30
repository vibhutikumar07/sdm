package com.sap.cds.sdm.persistence;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBQuery {
  private DBQuery() {
    // Doesn't do anything
  }

  public static Result getAttachmentsForUPID(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String upID) {
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("fileName", "ID", "IsActiveEntity", "folderId", "repositoryId")
            .where(doc -> doc.get("up__ID").eq(upID));
    return persistenceService.run(q);
  }

  public static void addAttachmentToDraft(
      CdsEntity attachmentEntity,
      PersistenceService persistenceService,
      CmisDocument cmisDocument) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("url", cmisDocument.getObjectId());
    updatedFields.put("repositoryId", repositoryId);
    updatedFields.put("folderId", cmisDocument.getFolderId());
    updatedFields.put("status", "Clean");

    CqnUpdate updateQuery =
        Update.entity(attachmentEntity)
            .data(updatedFields)
            .where(doc -> doc.get("ID").eq(cmisDocument.getAttachmentId()));
    persistenceService.run(updateQuery);
  }

  public static String getFolderIdForActiveEntity(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String upID) {
    String res = null;
    CqnSelect query =
        Select.from(attachmentEntity)
            .columns("folderId")
            .where(doc -> doc.get("up__ID").eq(upID).and(doc.get("IsActiveEntity").eq(true)));
    Result result = persistenceService.run(query);

    for (Map<String, Object> row : result.listOf(Map.class)) {
      Object folderIdObj = row.get("folderId");
      if (folderIdObj != null) {
        res = folderIdObj.toString();
        break; // Exit the loop after finding the first non-null folderId
      }
    }
    return res;
  }

  public static List<CmisDocument> getAttachmentsForFolder(
      CdsEntity attachmentEntity, PersistenceService persistenceService, String folderId) {
    List<CmisDocument> cmisDocuments = new ArrayList<>();
    CqnSelect q =
        Select.from(attachmentEntity)
            .columns("fileName", "IsActiveEntity", "ID", "folderId", "repositoryId", "url")
            .where(doc -> doc.get("folderId").eq(folderId));
    Result result = persistenceService.run(q);
    for (Row row : result.list()) {
      CmisDocument cmisDocument = new CmisDocument();
      cmisDocument.setFolderId(row.get("folderId").toString());
      cmisDocument.setRepositoryId(row.get("repositoryId").toString());
      cmisDocument.setFileName(row.get("fileName").toString());
      cmisDocument.setAttachmentId(row.get("ID").toString());
      cmisDocument.setObjectId(row.get("url").toString());
      cmisDocuments.add(cmisDocument);
    }
    return cmisDocuments;
  }
}
