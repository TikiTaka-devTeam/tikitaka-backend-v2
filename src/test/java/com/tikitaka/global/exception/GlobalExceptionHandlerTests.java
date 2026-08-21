package com.tikitaka.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

class GlobalExceptionHandlerTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RequestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingRequiredParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void invalidParameterTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/request").param("number", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/request"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_METHOD_NOT_ALLOWED"));
    }

    @Test
    void unreadableJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/request").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void missingMultipartPartReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void invalidPathVariableTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/request/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/request")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("value"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unsupportedAcceptReturnsNotAcceptable() throws Exception {
        mockMvc.perform(get("/response").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void oversizedMultipartReturnsPayloadTooLarge() throws Exception {
        mockMvc.perform(multipart("/upload/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_SIZE_EXCEEDED"));
    }

    @RestController
    static class RequestController {
        @GetMapping("/request")
        int request(@RequestParam int number) {
            return number;
        }

        @PostMapping("/request")
        RequestBodyValue requestBody(@RequestBody RequestBodyValue body) {
            return body;
        }

        @GetMapping("/request/{number}")
        int pathRequest(@PathVariable int number) {
            return number;
        }

        @GetMapping(value = "/response", produces = MediaType.APPLICATION_JSON_VALUE)
        RequestBodyValue response() {
            return new RequestBodyValue("value");
        }

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        void upload(@RequestPart("file") MultipartFile file) {
        }

        @PostMapping(value = "/upload/too-large", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        void oversizedUpload() {
            throw new MaxUploadSizeExceededException(1);
        }
    }

    record RequestBodyValue(String value) {
    }
}
