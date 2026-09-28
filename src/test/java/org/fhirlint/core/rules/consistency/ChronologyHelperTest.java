package org.fhirlint.core.rules.consistency;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ChronologyHelperTest {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Test
    void shouldDetectInvertedPeriod() throws Exception {
        Period validPeriod = new Period();
        validPeriod.setStart(dateFormat.parse("2023-01-01"));
        validPeriod.setEnd(dateFormat.parse("2023-01-05"));
        assertThat(ChronologyHelper.isPeriodInverted(validPeriod)).isFalse();

        Period equalPeriod = new Period();
        equalPeriod.setStart(dateFormat.parse("2023-01-01"));
        equalPeriod.setEnd(dateFormat.parse("2023-01-01"));
        assertThat(ChronologyHelper.isPeriodInverted(equalPeriod)).isFalse();

        Period invertedPeriod = new Period();
        invertedPeriod.setStart(dateFormat.parse("2023-01-05"));
        invertedPeriod.setEnd(dateFormat.parse("2023-01-01"));
        assertThat(ChronologyHelper.isPeriodInverted(invertedPeriod)).isTrue();

        Period openPeriod = new Period();
        openPeriod.setStart(dateFormat.parse("2023-01-01"));
        assertThat(ChronologyHelper.isPeriodInverted(openPeriod)).isFalse();
    }

    @Test
    void shouldDetectEventsPriorToBirth() throws Exception {
        Date birthDate = dateFormat.parse("1980-05-15");
        Date eventBefore = dateFormat.parse("1975-04-12");
        Date eventSameDay = dateFormat.parse("1980-05-15");
        Date eventAfter = dateFormat.parse("1985-06-20");

        assertThat(ChronologyHelper.isBeforeBirth(eventBefore, birthDate)).isTrue();
        assertThat(ChronologyHelper.isBeforeBirth(eventSameDay, birthDate)).isFalse();
        assertThat(ChronologyHelper.isBeforeBirth(eventAfter, birthDate)).isFalse();
    }

    @Test
    void shouldDetectEventsAfterDeceased() throws Exception {
        SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date deceasedDate = dtFormat.parse("2020-03-01T12:00:00");
        Date eventBefore = dtFormat.parse("2020-03-01T11:00:00");
        Date eventAfter = dtFormat.parse("2020-03-01T14:00:00");

        assertThat(ChronologyHelper.isAfterDeceased(eventBefore, deceasedDate)).isFalse();
        assertThat(ChronologyHelper.isAfterDeceased(eventAfter, deceasedDate)).isTrue();
    }

    @Test
    void shouldExtractDateFromFhirTypes() throws Exception {
        Date expected = dateFormat.parse("2023-01-01");

        DateType dateType = new DateType("2023-01-01");
        DateTimeType dateTimeType = new DateTimeType("2023-01-01T00:00:00Z");
        Period period = new Period().setStart(expected);

        assertThat(ChronologyHelper.extractDate(dateType)).isNotNull();
        assertThat(ChronologyHelper.extractDate(dateTimeType)).isNotNull();
        assertThat(ChronologyHelper.extractDate(period)).isEqualTo(expected);
        assertThat(ChronologyHelper.extractDate(null)).isNull();
    }
}
