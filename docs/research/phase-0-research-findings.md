# Phase 0: Research & Technical Architecture Findings

## Executive Summary

This document captures the foundational research, technical feasibility assessments, and domain analysis conducted during **Phase 0** of the **FHIRLint** project. 

The primary mission of FHIRLint is to answer the question:
> **"Can my application safely and reliably use this healthcare data, and what problems should I fix first?"**

Standard FHIR validators answer whether a payload conforms syntactically to schema definitions and implementation guide invariants. However, in real-world healthcare integrations, data that passes standard FHIR schema validation frequently fails downstream due to broken dataset-internal references, temporal contradictions, duplicate identities, uninterpretable units, and clinical-state conflicts.

FHIRLint bridges this gap by layering an extensible data-quality and referential-analysis engine on top of standard FHIR validation tooling.

---

## 1. What Exactly Does HAPI FHIR Already Provide?

[HAPI FHIR](https://hapifhir.io/) is the open-source reference implementation of the HL7 FHIR specification for the Java ecosystem, maintained by Smile Digital Health and University Health Network. 

Understanding HAPI's capabilities allows us to avoid reinventing established standards (as mandated by Constitution Principle I).

### 1.1 Core Components Provided by HAPI FHIR

1. **Object Model & AST (`hapi-fhir-structures-r4`)**:
   - Strongly-typed Java classes for all FHIR R4 resources (`Patient`, `Observation`, `Encounter`, `Condition`, `Bundle`, etc.).
   - Full representation of FHIR datatypes (`CodeableConcept`, `Coding`, `Identifier`, `Period`, `Quantity`, `Reference`).
   - `FhirContext`: Thread-safe, heavyweight factory and entry point for FHIR operations, caching schema structures and context metadata.

2. **Parsers & Serializers (`hapi-fhir-base`)**:
   - `IParser` (`JsonParser`, `XmlParser`): High-performance serialization and deserialization between JSON/XML streams and Java object trees.
   - Built-in error handling: Lenient vs. strict parsing modes, defensive null handling, extension preservation.
   - `FhirTerser`: Lightweight navigation engine to query and manipulate elements in resource graphs via element paths without explicit casting.

3. **Validation Framework (`hapi-fhir-validation` & `org.hl7.fhir.r4.validation`)**:
   - `FhirValidator`: Orchestrator that executes a chain of `IValidatorModule` implementations.
   - `FhirInstanceValidator`: Wraps the official HL7 Core Java validator, executing StructureDefinition constraints, cardinality checks, data type checks, and FHIRPath invariants.
   - `ValidationSupportChain`: Modular validation support mechanism allowing composition of:
     - `DefaultProfileValidationSupport`: Base R4 schemas.
     - `NpmPackageValidationSupport`: Direct loading of FHIR packages (e.g., US Core `.tgz` files).
     - `InMemoryTerminologyServerValidationSupport`: Basic in-memory lookup for fixed ValueSets.
     - `SnapshotGeneratingValidationSupport`: Generates snapshot StructureDefinitions from differentials at runtime.
     - `CachingValidationSupport`: In-memory LRU cache of profile definitions and validation results to prevent expensive re-parsing.

4. **What HAPI FHIR Does NOT Provide (The FHIRLint Opportunity)**:
   - **Cross-resource referential integrity within arbitrary Bundles**: HAPI only validates reference strings against basic regex/URI patterns unless connected to a running live HAPI JPA FHIR storage server with a persistent database. It does not verify that `Observation.subject.reference = "Patient/pat-99"` exists within the submitted batch or bundle.
   - **Dataset-level cross-resource consistency**: HAPI does not detect chronological inversions (e.g., Encounter end before start, or medications authored before birth date), nor does it evaluate cross-resource status coherence (e.g., a final DiagnosticReport referencing an entered-in-error Observation).
   - **Entity deduplication**: HAPI has no concept of duplicate patient or duplicate observation detection across a dataset.
   - **Engineering data-quality scoring**: HAPI produces raw compiler-like error messages (`SingleValidationMessage`), but provides no weighted quality index, category scores, or actionable remediation suggestions tailored for developers.

---

## 2. How Does HAPI's R4 Validation Work?

### 2.1 The Validation Pipeline

HAPI's validation engine operates through a composite pipeline:

```
Raw JSON Payload
       │
       ▼
┌──────────────────────────────────────────────┐
│ FhirValidator (Orchestrator)                 │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │ FhirInstanceValidator                  │  │
│  │ (Wraps HL7 Core InstanceValidator)     │  │
│  │                                        │  │
│  │  1. Structural Schema Validation       │  │
│  │  2. StructureDefinition Matching       │  │
│  │  3. FHIRPath Invariant Evaluation      │  │
│  │  4. Element Slicing & Cardinality      │  │
│  │  5. ValueSet Binding & Terminology     │  │
│  └────────────────────────────────────────┘  │
│                      │                       │
│                      ▼                       │
│  ┌────────────────────────────────────────┐  │
│  │ ValidationSupportChain                 │  │
│  │  - CachingSupport                      │  │
│  │  - US Core NPM Package Support         │  │
│  │  - Default Base R4 Support             │  │
│  │  - InMemory Terminology Support        │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
       │
       ▼
ValidationResult (List<SingleValidationMessage>)
```

### 2.2 Mechanism Details
- **FHIRPath Invariant Evaluation**: Implementation guides define invariants such as `us-core-8: Patient.name.exists() or Patient.telecom.exists()`. HAPI's instance validator evaluates these expressions using the HL7 FHIRPath engine against each resource tree.
- **Resource-by-Resource Isolation**: HAPI evaluates each resource in isolation against its profile. When given a `Bundle`, it checks that the Bundle has valid `entry` arrays, and then iterates through each entry, running the validator against that individual resource.
- **Output Structure**: HAPI returns a list of `SingleValidationMessage` objects containing:
  - `getSeverity()`: `FATAL`, `ERROR`, `WARNING`, `INFORMATION`.
  - `getLocationString()`: FHIRPath location (e.g., `Bundle.entry[0].resource.ofType(Patient).name[0]`).
  - `getMessage()`: Detailed text (often 100+ character compiler messages).

---

## 3. What US Core Profiles Should FHIRLint Initially Support?

US Core (aligned with the USCDI standard mandated by ONC rules) defines the baseline profile requirements for U.S. healthcare interoperability.

For the MVP, FHIRLint will target the core clinical backbone resources comprising over 95% of standard clinical data exchanges:

| Resource Type | US Core Profile URL | Key US Core Constraints Verified |
| :--- | :--- | :--- |
| **Patient** | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient` | Required identifier, name, gender; US Core Race and Ethnicity extensions. |
| **Encounter** | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter` | Required status, class, type, subject (Patient reference). |
| **Condition** | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-condition` | Required clinicalStatus, verificationStatus, category, code, subject. |
| **Observation (Vital Signs)** | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-vital-signs` | Sliced category (`vital-signs`), required code, subject, effectiveDateTime, valueQuantity with UCUM units. |
| **Observation (Lab Results)**| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab` | Sliced category (`laboratory`), status, code, subject, effectiveDateTime, value[x] or dataAbsentReason. |
| **MedicationRequest** | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-medicationrequest` | Required status, intent, medication[x] (CodeableConcept or Reference), subject, authoredOn. |
| **DiagnosticReport** | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-note` / `...-lab` | Required status, category, code, subject, effectiveDateTime, and result (references to Observations). |

*Fast Follow Profiles (Phase 4+)*: `AllergyIntolerance`, `Procedure`, `Immunization`, `Practitioner`.

---

## 4. What Data-Quality Problems Are Not Caught by Standard FHIR Validation?

The primary value proposition of FHIRLint lies in catching data-quality issues that standard validators ignore:

```
┌───────────────────────────────────────────────────────────┐
│               Standard FHIR Validation                    │
│   ✓ Schema validity (JSON types, primitive formats)       │
│   ✓ Required fields per individual StructureDefinition     │
│   ✓ FHIRPath invariants on single resources               │
└─────────────────────────────┬─────────────────────────────┘
                              │ Standard validator passes these!
                              ▼
┌───────────────────────────────────────────────────────────┐
│               FHIRLint Data-Quality Layer                 │
│   ✗ Broken local references (Patient/999 not in bundle)   │
│   ✗ Dangling UUID references (urn:uuid:abc typo)          │
│   ✗ Target resource type mismatches (Encounter vs Doctor) │
│   ✗ Temporal reversals (Encounter end before start)       │
│   ✗ Chronological contradictions (Meds before birth)      │
│   ✗ Status conflicts (Final report citing deleted lab)    │
│   ✗ Uninterpretable clinical data (Missing UCUM system)   │
│   ✗ Entity duplication (Same SSN/DOB, different IDs)      │
│   ✗ Orphaned clinical data (Lab with no patient linkage)  │
└───────────────────────────────────────────────────────────┘
```

1. **Dangling Local References**: An Observation references `Patient/pat-100`, but no such Patient exists in the Bundle. Standard validators verify the string syntax (`[A-Za-z0-9\-\.]{1,64}`) and mark it valid.
2. **UUID Disconnections**: Synthea and EHR export bundles frequently use `urn:uuid:...` references. A slight typo or omission in a UUID reference breaks relational joins in downstream data warehouses.
3. **Target Type Discrepancies**: A field expecting a `Practitioner` reference points to an `Encounter` resource ID.
4. **Temporal Inversions**: 
   - `Encounter.period.end` occurs before `Encounter.period.start`.
   - `MedicationRequest.authoredOn` occurs 5 years prior to the patient's `birthDate`.
   - `Observation.effectiveDateTime` occurs after patient's `deceasedDateTime`.
5. **State/Status Conflicts**: A `DiagnosticReport` with status `final` references an `Observation` whose status is `entered-in-error` or `cancelled`.
6. **Missing Measurement Semantics**: An Observation has `valueQuantity.value = 140` but omits `system` or `unit`. A blood pressure of 140 without units or with non-UCUM strings cannot be ingested by clinical algorithms.
7. **Entity Duplication**: Two `Patient` resources in the same bundle have identical identifiers (e.g. SSN or MRN) or matching Full Name + DOB + Zip Code, but distinct FHIR IDs, causing duplicate records in downstream EHRs.

---

## 5. How Should References Be Indexed and Resolved?

FHIR Bundles express relationships in three primary forms:
1. **Relative References**: `"reference": "Patient/pat-001"`
2. **Full URN / UUID References**: `"reference": "urn:uuid:f47ac10b-58cc-4372-a567-0e02b2c3d479"`
3. **Absolute Canonical URLs**: `"reference": "http://hospital.org/fhir/r4/Patient/pat-001"`

### 5.1 Graph Indexing Architecture

FHIRLint builds an in-memory directed resource graph during ingestion:

```
                    Graph Indexer
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
    Node Index                       Edge Index
  ┌───────────────────────┐        ┌───────────────────────┐
  │ "Patient/pat-001"     │        │ Source: Obs/obs-10    │
  │ "urn:uuid:f47ac..."   │        │ Path: subject.ref     │
  │ "http://.../Pat/001"  │        │ Target: "Patient/999" │
  └───────────────────────┘        │ Expected: Patient     │
                                   └───────────────────────┘
                                          │
                                          ▼
                                   Resolution Phase
                                 (Checks existence & type)
```

1. **Node Registration**:
   - For every entry in `Bundle.entry`:
     - Register `entry.fullUrl` (e.g. `urn:uuid:...` or absolute URL).
     - Register canonical resource reference: `${resourceType}/${id}`.
     - Store a pointer to the parsed `IBaseResource` and metadata.
2. **Edge Extraction**:
   - Using HAPI's `FhirTerser` or a recursive AST visitor, extract all elements of type `Reference`.
   - Record: `sourceResourceId`, `sourceResourceType`, `targetReferenceString`, `fhirPath`, `expectedTargetType`.
3. **Resolution & Classification**:
   - Match `targetReferenceString` against the Node Index.
   - If **Found**: Verify that the target resource's type matches `expectedTargetType`. If mismatched, flag `REF-TYPE-MISMATCH`.
   - If **Not Found**:
     - If the reference begins with `urn:uuid:` or is a relative reference within a closed transaction/collection bundle: flag `REF-BROKEN-LOCAL` (ERROR).
     - If the reference is an external URL: flag `REF-EXTERNAL-UNRESOLVED` (INFO or WARNING depending on dataset settings).
4. **Inverted Index (Orphan Detection)**:
   - Identify nodes with 0 incoming and 0 outgoing edges (e.g., a disconnected `Observation` not linked to any Patient or Encounter).

---

## 6. What Terminology Checks Are Realistic for the MVP?

Full terminology validation against complete UMLS/SNOMED CT/LOINC distributions requires gigabytes of relational tables or external network calls to licensed terminology servers (such as VSAC or Ontoserver), introducing prohibitive operational complexity for an MVP.

### 6.1 Realistic MVP Strategy

FHIRLint adopts a **three-tier pluggable terminology model**:

1. **Syntax & System URI Conformance**:
   - Validate that system URIs are canonical (e.g., `http://loinc.org`, `http://snomed.info/sct`, `http://www.nlm.nih.gov/research/umls/rxnorm`, `http://unitsofmeasure.org`).
   - Detect common typos (e.g., `http://loinc.com`, `https://snomed.org`, `rxnorm.nlm.nih.gov`).
2. **In-Memory ValueSet Provider for US Core Required Codes**:
   - Static in-memory sets loaded from classpath JSON/YAML:
     - `AdministrativeGender` (male, female, other, unknown)
     - `EncounterStatus` & `EncounterClass`
     - `ConditionClinicalStatus` & `ConditionVerificationStatus`
     - `ObservationStatus` & `ObservationCategory` (vital-signs, laboratory, etc.)
     - US Core Race & Ethnicity OMB codes
3. **Curated Common Vocabulary Lookups**:
   - Vital sign LOINC codes: 8867-4 (Heart rate), 8480-6 (Systolic BP), 8462-4 (Diastolic BP), 8310-5 (Body temp), 2708-6 (Oxygen saturation), 2339-0 (Glucose).
   - Valid UCUM units for clinical vitals: `mm[Hg]`, `beats/min`, `cel`, `[degF]`, `kg`, `g`, `cm`, `mg/dL`, `%`.
4. **Pluggable Architecture**:
   - Encapsulate in `TerminologyService` interface. Future phases can swap in an HTTP client calling a remote FHIR Terminology Server (`ValueSet/$validate-code`) without changing rule code.

---

## 7. What Does a Useful Quality Score Actually Look Like?

A useful quality score must not be an opaque black box. It must be **deterministic**, **explainable**, and **actionable**.

### 7.1 Score Structure

```json
{
  "overallScore": 82,
  "grade": "ACCEPTABLE",
  "summary": {
    "totalResources": 15,
    "errorCount": 2,
    "warningCount": 5,
    "infoCount": 3
  },
  "categories": {
    "structural": { "score": 100, "weight": 0.20, "errors": 0, "warnings": 0 },
    "profileConformance": { "score": 90, "weight": 0.20, "errors": 0, "warnings": 2 },
    "referentialIntegrity": { "score": 75, "weight": 0.25, "errors": 2, "warnings": 1 },
    "consistency": { "score": 85, "weight": 0.15, "errors": 0, "warnings": 1 },
    "terminology": { "score": 80, "weight": 0.10, "errors": 0, "warnings": 1 },
    "completeness": { "score": 70, "weight": 0.10, "errors": 0, "warnings": 0 }
  }
}
```

### 7.2 Deterministic Scoring Algorithm

1. **Category Score Calculation**:
   $$\text{RawPenalty} = (\text{ErrorCount} \times 15) + (\text{WarningCount} \times 3)$$
   $$\text{NormalizedDefectRatio} = \frac{\text{RawPenalty}}{\max(\text{TotalResources}, 1) \times 15}$$
   $$\text{CategoryScore} = \text{round}\Big(\max\big(0, 100 \times (1 - \min(1.0, \text{NormalizedDefectRatio}))\big)\Big)$$
   *Note: An explicit floor of 0 and ceiling of 100 is maintained.*

2. **Overall Score Calculation**:
   $$\text{OverallScore} = \text{round}\left( \sum_{c \in \text{Categories}} (\text{CategoryScore}_c \times \text{Weight}_c) \right)$$
   Standard weights:
   - Referential Integrity: 25%
   - Structural Validity: 20%
   - Profile Conformance: 20%
   - Cross-Resource Consistency: 15%
   - Terminology: 10%
   - Completeness: 10%

3. **Grade Bands**:
   - `90 - 100`: **EXCELLENT** (Production-ready)
   - `75 - 89`: **ACCEPTABLE** (Minor warnings; safe for non-critical ingest)
   - `50 - 74`: **DEGRADED** (Significant reference or consistency flaws)
   - `< 50`: **CRITICAL** (Corrupted references or invalid schema structure)

---

## 8. What Should the Initial Synthetic "Messy Bundle" Contain?

To provide a compelling baseline test suite and demonstration asset, we design a synthetic dataset titled `messy-bundle.json` containing 12 interconnected resources with deliberate, realistic defects:

1. **Patient `pat-001`**: Base patient (valid).
2. **Patient `pat-002`**: Near-duplicate of `pat-001` (same SSN, same birth date, same address, but different resource ID).
3. **Encounter `enc-001`**: Valid encounter for `pat-001`.
4. **Encounter `enc-002`**: **Temporal inversion** (`period.end` is `2026-05-01T10:00:00Z`, but `period.start` is `2026-05-01T14:00:00Z`).
5. **Observation `obs-001`**: Valid vital sign linked to `pat-001` and `enc-001`.
6. **Observation `obs-002`**: **Broken reference** (`subject.reference = "Patient/pat-999-nonexistent"`).
7. **Observation `obs-003`**: **Missing UCUM units** (`valueQuantity.value = 145`, but missing `system` and `unit`).
8. **Observation `obs-004`**: **Entered-in-error lab result**.
9. **Observation `obs-005`**: **Invalid terminology code system** (`system = "http://loinc-fake.org"`).
10. **Condition `cond-001`**: Valid condition linked to `pat-001`.
11. **MedicationRequest `med-001`**: **Chronological contradiction** (`authoredOn = 1970-01-01`, but `pat-001` was born in 1985).
12. **DiagnosticReport `diag-001`**: **Cross-resource state conflict** (`status = "final"`, but includes `obs-004` which is `status = "entered-in-error"`).

---

## 9. Are There Technical Constraints Around Asynchronous Processing?

1. **Memory Footprint of HAPI Data Models**:
   - A 50MB FHIR JSON Bundle parsed into HAPI's `Bundle` object model can easily expand to 400MB–800MB of JVM heap objects due to rich AST nodes, String objects, and metadata maps.
   - **Constraint**: Large payload ingestion must enforce a strict maximum size limit for MVP (e.g. 10MB or 5,000 resources) and stream validation where applicable.
2. **Privacy & Transient Storage (Constitution Principle V)**:
   - Raw FHIR payloads must NEVER be persisted in PostgreSQL.
   - During async execution, the payload resides in transient memory or an ephemeral disk buffer that is strictly purged upon job completion or termination.
3. **Worker Thread Isolation**:
   - Long-running validation tasks must run on a dedicated Spring `ThreadPoolTaskExecutor` with bounded queues.
   - If the task queue is saturated, the API must fail fast with `429 Too Many Requests` rather than crashing the JVM with an `OutOfMemoryError`.
4. **Job Lifecycle & Timeouts**:
   - Status states: `QUEUED` $\to$ `PROCESSING` $\to$ `COMPLETED` | `FAILED` | `TIMEOUT`.
   - A watchdog/timeout policy (e.g., 60 seconds per check) prevents catastrophic backtracking in complex FHIRPath invariants or regex evaluation from tying up threads indefinitely.

---

## 10. What Should PostgreSQL Actually Persist?

In strict compliance with **Constitution Principle V** (Privacy-Conscious Healthcare Software), FHIRLint operates as a **stateless inspector**. It does NOT store protected patient data.

### 10.1 Database Entity Schema

```
┌────────────────────────────────────────────────────────┐
│ quality_check_jobs                                     │
├────────────────────────────────────────────────────────┤
│ id (UUID, PK)                                          │
│ status (VARCHAR: QUEUED, PROCESSING, COMPLETED, FAILED)│
│ profile_requested (VARCHAR: FHIR_R4, US_CORE)          │
│ total_resources (INT)                                  │
│ error_count (INT)                                      │
│ warning_count (INT)                                    │
│ info_count (INT)                                       │
│ overall_score (INT)                                    │
│ category_scores (JSONB)                                │
│ error_message (TEXT)                                   │
│ created_at (TIMESTAMPTZ)                               │
│ started_at (TIMESTAMPTZ)                               │
│ completed_at (TIMESTAMPTZ)                             │
└──────────────────────────┬─────────────────────────────┘
                           │ 1
                           │
                           │ N
┌──────────────────────────▼─────────────────────────────┐
│ quality_issues                                         │
├────────────────────────────────────────────────────────┤
│ id (UUID, PK)                                          │
│ job_id (UUID, FK)                                      │
│ rule_id (VARCHAR: REF-001, TEMP-002, etc.)             │
│ severity (VARCHAR: ERROR, WARNING, INFO)               │
│ category (VARCHAR: REFERENTIAL, STRUCTURAL, etc.)      │
│ resource_type (VARCHAR: Observation, Patient, etc.)    │
│ resource_id (VARCHAR: obs-002, pat-001, etc.)          │
│ fhir_path (VARCHAR: Observation.subject.reference)     │
│ message (TEXT)                                         │
│ suggestion (TEXT)                                      │
└────────────────────────────────────────────────────────┘
```

### 10.2 Strict Boundary: What Is NOT Stored
- NO patient names, identifiers, SSNs, phone numbers, or dates of birth.
- NO clinical narrative or note texts (`Narrative`, `Annotation`).
- NO raw JSON payloads of the submitted resources or Bundles.
- NO clinical values from Observations or Conditions.

---

## Summary of Architectural Baseline

With Phase 0 complete:
1. HAPI FHIR provides the foundation for parsing, model representation, and baseline R4/US Core structure checking.
2. FHIRLint provides the custom value layer: in-memory graph resolution, temporal consistency checks, pluggable terminology validation, deduplication, and deterministic engineering scoring.
3. Persistence is strictly restricted to non-PHI job metadata and categorized issue reports.
