package com.sap.cds.sdm.utilities;

import com.sap.cds.CdsData;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SDMUtils {
  public static Set<String> isFileNameDuplicateInDrafts(List<CdsData> data) {
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
