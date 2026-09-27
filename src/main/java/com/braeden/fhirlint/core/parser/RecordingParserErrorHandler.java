package com.braeden.fhirlint.core.parser;

import ca.uhn.fhir.parser.IParserErrorHandler;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser error handler that records unknown elements, unknown attributes,
 * and invalid values without aborting ingestion.
 */
public class RecordingParserErrorHandler extends LenientErrorHandler {

    private final List<SingleValidationMessage> messages = new ArrayList<>();

    public RecordingParserErrorHandler() {
        setErrorOnInvalidValue(false);
    }

    @Override
    public void unknownElement(IParserErrorHandler.IParseLocation theLocation, String theElementName) {
        SingleValidationMessage m = new SingleValidationMessage();
        m.setSeverity(ResultSeverityEnum.ERROR);
        m.setLocationString(theLocation != null ? theLocation.toString() : theElementName);
        m.setMessage("Unknown element '" + theElementName + "'");
        messages.add(m);
    }

    @Override
    public void unknownAttribute(IParserErrorHandler.IParseLocation theLocation, String theAttributeName) {
        SingleValidationMessage m = new SingleValidationMessage();
        m.setSeverity(ResultSeverityEnum.ERROR);
        m.setLocationString(theLocation != null ? theLocation.toString() : theAttributeName);
        m.setMessage("Unknown attribute '" + theAttributeName + "'");
        messages.add(m);
    }

    @Override
    public void invalidValue(IParserErrorHandler.IParseLocation theLocation, String theValue, String theError) {
        SingleValidationMessage m = new SingleValidationMessage();
        m.setSeverity(ResultSeverityEnum.ERROR);
        m.setLocationString(theLocation != null ? theLocation.toString() : "");
        m.setMessage("Invalid attribute value \"" + theValue + "\": " + theError);
        messages.add(m);
    }

    public List<SingleValidationMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}
