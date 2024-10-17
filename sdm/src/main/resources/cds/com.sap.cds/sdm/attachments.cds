namespace sap.attachments;

using {sap.attachments.Attachments} from `com.sap.cds/cds-feature-attachments`;
extend aspect Attachments with {
    folderId : String  @readonly;
    repositoryId : String  @readonly;
    url : String  @readonly;
}
annotate Attachments with @UI: {
    HeaderInfo: {
        $Type         : 'UI.HeaderInfoType',
        TypeName      : '{i18n>attachment}',
        TypeNamePlural: '{i18n>attachments}',
    },
    LineItem  : [
        {Value: fileName, @HTML5.CssDefaults: {width: '20%'}},
         {Value: content, @HTML5.CssDefaults: {width: '20%'}},
          {Value: createdAt, @HTML5.CssDefaults: {width: '20%'}},
          {Value: createdBy, @HTML5.CssDefaults: {width: '15%'}},
          {Value: note, @HTML5.CssDefaults: {width: '25%'}}
    ]
} {
    note       @(title: '{i18n>attachment_note}');
   modifiedAt @(odata.etag);
   content
       @Core.ContentDisposition: { Filename: fileName, Type: 'inline' }
}
annotate Attachments with @Common: {SideEffects #ContentChanged: {
    SourceProperties: [content],
    TargetProperties: ['status']
}}{};
