namespace sap.attachments;

using {
    cuid,
    managed
} from '@sap/cds/common';


aspect MediaData           @(_is_media_data) {
    content   : LargeBinary; // stored only for db-based services
    mimeType  : String;
    fileName  : String;
    contentId : String     @readonly; // id of attachment in external storage, if database storage is used, same as id
}

aspect Attachments : cuid, managed, MediaData {
    note : String;
}
