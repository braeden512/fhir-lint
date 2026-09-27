package com.braeden.fhirlint.controller;

import com.braeden.fhirlint.config.JacksonConfig;
import com.braeden.fhirlint.service.AsyncIngestionWorker;
import com.braeden.fhirlint.service.JobLifecycleManager;
import com.braeden.fhirlint.service.QualityCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QualityCheckController.class)
@Import({QualityCheckService.class, RestExceptionHandler.class, JacksonConfig.class})
class QualityCheckBoundaryValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobLifecycleManager jobLifecycleManager;

    @MockitoBean
    private AsyncIngestionWorker asyncIngestionWorker;

    @Test
    void shouldReturn400WhenPayloadIsMalformedJson() throws Exception {
        String malformedJson = "{\"invalidJson\": ";

        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Malformed JSON payload in request body."));

        verifyNoInteractions(jobLifecycleManager);
        verifyNoInteractions(asyncIngestionWorker);
    }

    @Test
    void shouldReturn400WhenPayloadIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Request body cannot be empty."));

        verifyNoInteractions(jobLifecycleManager);
    }

    @Test
    void shouldReturn400WhenMissingResourceType() throws Exception {
        String nonFhirJson = """
                {
                  "name": "Jane Doe",
                  "age": 30
                }
                """;

        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonFhirJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Payload must contain a valid FHIR 'resourceType' declaration."));

        verifyNoInteractions(jobLifecycleManager);
    }

    @Test
    void shouldReturn400WhenResourceTypeIsBlank() throws Exception {
        String blankTypeJson = """
                {
                  "resourceType": "   ",
                  "id": "pat-1"
                }
                """;

        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankTypeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Payload must contain a valid FHIR 'resourceType' declaration."));

        verifyNoInteractions(jobLifecycleManager);
    }

    @Test
    void shouldReturn400WhenResourceTypeIsNotString() throws Exception {
        String nonStringTypeJson = """
                {
                  "resourceType": 12345,
                  "id": "pat-1"
                }
                """;

        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonStringTypeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Payload must contain a valid FHIR 'resourceType' declaration."));

        verifyNoInteractions(jobLifecycleManager);
    }

    @Test
    void shouldReturn400WhenPayloadIsNotAnObject() throws Exception {
        String arrayJson = "[\"item1\", \"item2\"]";

        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(arrayJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Payload must be a JSON object."));

        verifyNoInteractions(jobLifecycleManager);
    }
}
