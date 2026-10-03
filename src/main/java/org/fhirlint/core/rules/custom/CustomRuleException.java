package org.fhirlint.core.rules.custom;

/**
 * Exception thrown when a custom rule definition contains syntax or parsing errors.
 */
public class CustomRuleException extends RuntimeException {

    public CustomRuleException(String message) {
        super(message);
    }

    public CustomRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
