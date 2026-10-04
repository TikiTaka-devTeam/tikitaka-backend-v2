package com.tikitaka.document.controller;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.tikitaka.document.dto.DocumentNoteType;
import com.tikitaka.document.service.DocumentService;
import com.tikitaka.global.exception.GlobalExceptionHandler;
import com.tikitaka.global.security.CurrentUserResolver;

class DocumentDownloadControllerTests {
    private final DocumentService service = mock(DocumentService.class);
    private final CurrentUserResolver resolver = mock(CurrentUserResolver.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DocumentController(service, resolver))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void omittedOptionUsesNoneAndAllIsAccepted() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/documents/{id}/download", id)).andExpect(status().isOk());
        verify(service).downloadDocument(eq(id), eq(DocumentNoteType.NONE), any());
        mvc.perform(get("/api/v1/documents/{id}/download", id).param("note_type", "ALL"))
                .andExpect(status().isOk());
        verify(service).downloadDocument(eq(id), eq(DocumentNoteType.ALL), any());
    }

    @Test
    void unsupportedOptionReturnsBadRequestWithoutCallingService() throws Exception {
        mvc.perform(get("/api/v1/documents/{id}/download", UUID.randomUUID()).param("note_type", "OTHER"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
