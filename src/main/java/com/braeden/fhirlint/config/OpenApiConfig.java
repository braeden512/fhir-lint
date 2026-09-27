package com.braeden.fhirlint.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fhirLintOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FHIRLint Quality Checks API")
                        .description("Developer-focused API for healthcare data quality analysis. " +
                                "Phase 1 provides FHIR dataset ingestion, boundary pre-flight validation, " +
                                "and asynchronous job lifecycle tracking.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development server")
                ));
    }
}
