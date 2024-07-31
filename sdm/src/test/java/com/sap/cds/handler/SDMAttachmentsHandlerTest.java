package com.sap.cds.handler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class SDMAttachmentsHandlerTest {

    private SDMAttachmentsHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new SDMAttachmentsHandler();
    }

    @Test
    void testPerformAction() {
        String result = handler.performAction();
        assertThat(result).isEqualTo("Action Performed");
    }
}