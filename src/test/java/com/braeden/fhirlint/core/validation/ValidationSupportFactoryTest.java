package com.braeden.fhirlint.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationSupportFactoryTest {

    @Test
    void testClassesExist() throws IOException {
        FhirContext ctx = FhirContext.forR4Cached();
        NpmPackageValidationSupport npmSupport = new NpmPackageValidationSupport(ctx);
        npmSupport.loadPackageFromClasspath("profiles/us-core/package.tgz");
        
        org.hl7.fhir.r4.model.StructureDefinition sd = (org.hl7.fhir.r4.model.StructureDefinition) 
            npmSupport.fetchStructureDefinition("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient");
        assertThat(sd).isNotNull();
        assertThat(sd.getName()).isEqualTo("USCorePatientProfile");
    }
}
