package com.sap.cds.sdm.handler.applicationservice;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.messages.Messages;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMCreateAttachmentsHandlerTest {

  @Mock private PersistenceService persistenceService;
  @Mock private SDMService sdmService;
  @Mock private CdsCreateEventContext context;
  @Mock private AuthenticationInfo authInfo;
  @Mock private JwtTokenAuthenticationInfo jwtTokenInfo;
  @Mock private SDMCredentials mockCredentials;
  @Mock private Messages messages;

  @InjectMocks @Spy private SDMCreateAttachmentsHandler handler; // Use Spy to allow partial mocking

  private MockedStatic<TokenHandler> tokenHandlerMockedStatic;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    // Set up static mocking for `TokenHandler.getSDMCredentials`
    tokenHandlerMockedStatic = mockStatic(TokenHandler.class);
    tokenHandlerMockedStatic.when(TokenHandler::getSDMCredentials).thenReturn(mockCredentials);
  }

  @AfterEach
  public void tearDown() {
    if (tokenHandlerMockedStatic != null) {
      tokenHandlerMockedStatic.close();
    }
  }

  @Test
  public void testProcessBefore() throws IOException {
    List<CdsData> data = new ArrayList<>();
    doNothing().when(handler).rename(any(CdsCreateEventContext.class), anyList());

    handler.processBefore(context, data);

    verify(handler, times(1)).rename(context, data);
  }

  @Test
  public void testProcessBeforeWithException() throws IOException {
    List<CdsData> data = new ArrayList<>();
    when(context.getMessages()).thenReturn(messages);
    doThrow(new IOException()).when(handler).rename(any(CdsCreateEventContext.class), anyList());

    handler.processBefore(context, data);

    verify(context.getMessages(), times(1)).error("Error renaming attachment");
  }

  @Test
  public void testRenameWithDuplicateFilenames() throws IOException {
    List<CdsData> data = new ArrayList<>();
    Set<String> duplicateFilenames = new HashSet<>(Arrays.asList("file1.txt", "file2.txt"));
    when(context.getMessages()).thenReturn(messages);
    when(handler.isFileNameDuplicateInDrafts(any(CdsCreateEventContext.class), anyList()))
        .thenReturn(duplicateFilenames);

    handler.rename(context, data);

    verify(context.getMessages(), times(1))
        .error(
            "The file(s) file1.txt, file2.txt have been added multiple times. Please rename and try again.");
  }

  @Test
  public void testRenameWithNoDuplicateFilenames() throws IOException {
    List<CdsData> data = new ArrayList<>();
    when(handler.isFileNameDuplicateInDrafts(any(CdsCreateEventContext.class), anyList()))
        .thenReturn(new HashSet<>());

    handler.rename(context, data);

    verify(messages, never()).error(anyString());
  }

  @Test
  public void testIsFileNameDuplicateInDrafts() {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "file1.txt");
    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "file1.txt");
    attachments.add(attachment1);
    attachments.add(attachment2);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments); // Correctly mock get method
    data.add(mockCdsData);

    Set<String> duplicateFilenames = handler.isFileNameDuplicateInDrafts(context, data);

    assertTrue(duplicateFilenames.contains("file1.txt"));
  }

  @Test
  public void testRenameWithNoAttachments() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    when(mockCdsData.get("attachments")).thenReturn(null);
    data.add(mockCdsData);

    handler.rename(context, data);

    verify(sdmService, never())
        .renameAttachments(anyString(), any(SDMCredentials.class), anyString(), anyString());
  }

  @Test
  public void testRenameWithSameFileNameInSDM() throws IOException {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment = new HashMap<>();
    attachment.put("fileName", "file1.txt");
    attachment.put("url", "objectId");
    attachments.add(attachment);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments);
    data.add(mockCdsData);

    when(context.getAuthenticationInfo()).thenReturn(authInfo);
    when(authInfo.as(JwtTokenAuthenticationInfo.class)).thenReturn(jwtTokenInfo);
    when(jwtTokenInfo.getToken()).thenReturn("jwtToken");
    // when(sdmService.getObject(anyString(), anyString(), any(SDMCredentials.class)))
    //     .thenReturn("file1.txt");

    handler.rename(context, data);

    verify(sdmService, never())
        .renameAttachments(anyString(), any(SDMCredentials.class), anyString(), anyString());
  }
}
