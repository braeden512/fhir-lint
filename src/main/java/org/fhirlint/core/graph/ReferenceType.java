package org.fhirlint.core.graph;

/**
 * Classifies the format and resolution scheme of a FHIR Reference element.
 */
public enum ReferenceType {
    /**
     * Standard relative reference in the form Type/id (e.g. "Patient/pat-123").
     */
    RELATIVE,

    /**
     * UUID URN scheme (e.g. "urn:uuid:7b420ee9-4c8d-4f15-99d8-111111111111").
     */
    URN_UUID,

    /**
     * OID URN scheme (e.g. "urn:oid:2.16.840.1.113883.4.1").
     */
    URN_OID,

    /**
     * Contained resource fragment reference (e.g. "#contained-id").
     */
    CONTAINED_FRAGMENT,

    /**
     * Absolute HTTP/HTTPS URI outside the local bundle perimeter.
     */
    ABSOLUTE_EXTERNAL,

    /**
     * Unqualified logical ID without resource type prefix (e.g. "pat-123").
     */
    BARE_ID,

    /**
     * Empty, whitespace, null, or unparseable reference string.
     */
    MALFORMED
}
