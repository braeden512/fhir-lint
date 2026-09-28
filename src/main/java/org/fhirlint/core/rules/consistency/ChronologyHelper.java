package org.fhirlint.core.rules.consistency;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Type;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * Utility helper for precision-aware date and timestamp comparisons across FHIR R4 temporal types.
 */
public final class ChronologyHelper {

    private ChronologyHelper() {}

    /**
     * Checks if period.end occurs chronologically strictly before period.start.
     *
     * @param period the Period to evaluate
     * @return true if end is strictly before start, false otherwise
     */
    public static boolean isPeriodInverted(Period period) {
        if (period == null || !period.hasStart() || !period.hasEnd()) {
            return false;
        }
        Date start = period.getStart();
        Date end = period.getEnd();
        if (start == null || end == null) {
            return false;
        }
        return end.before(start);
    }

    /**
     * Checks if a clinical event timestamp strictly predates a patient's birth date.
     * Truncates timestamps to calendar date if birth date is a DateType without time component.
     *
     * @param eventDate the clinical event date/time
     * @param birthDate the patient birth date
     * @return true if eventDate is strictly before birthDate
     */
    public static boolean isBeforeBirth(Date eventDate, Date birthDate) {
        if (eventDate == null || birthDate == null) {
            return false;
        }

        LocalDate eventLocalDate = toLocalDate(eventDate);
        LocalDate birthLocalDate = toLocalDate(birthDate);

        return eventLocalDate.isBefore(birthLocalDate);
    }

    /**
     * Checks if a clinical event timestamp strictly occurs after a patient's deceased date/time.
     *
     * @param eventDate the clinical event date/time
     * @param deceasedDate the patient deceased date/time
     * @return true if eventDate is strictly after deceasedDate
     */
    public static boolean isAfterDeceased(Date eventDate, Date deceasedDate) {
        if (eventDate == null || deceasedDate == null) {
            return false;
        }
        return eventDate.after(deceasedDate);
    }

    /**
     * Extracts a java.util.Date from a FHIR temporal Type (DateTimeType, DateType, Period, or InstantType).
     *
     * @param temporalType FHIR Type
     * @return Date or null if unextractable
     */
    public static Date extractDate(Type temporalType) {
        if (temporalType == null) {
            return null;
        }
        if (temporalType instanceof DateTimeType dateTimeType && dateTimeType.hasValue()) {
            return dateTimeType.getValue();
        }
        if (temporalType instanceof DateType dateType && dateType.hasValue()) {
            return dateType.getValue();
        }
        if (temporalType instanceof Period period && period.hasStart()) {
            return period.getStart();
        }
        return null;
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
