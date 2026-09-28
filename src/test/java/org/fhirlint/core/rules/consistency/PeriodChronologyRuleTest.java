package org.fhirlint.core.rules.consistency;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodChronologyRuleTest {

    private PeriodChronologyRule rule;

    @BeforeEach
    void setUp() {
        rule = new PeriodChronologyRule();
    }

    @Test
    void shouldFlagInvertedEncounterPeriod() {
        Encounter encounter = new Encounter();
        encounter.setId("enc-1");
        Period period = new Period();
        period.setStartElement(new DateTimeType("2023-01-15T10:00:00Z"));
        period.setEndElement(new DateTimeType("2023-01-10T10:00:00Z"));
        encounter.setPeriod(period);

        RuleContext context = new RuleContext(encounter, List.of(encounter), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("CONS-001");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.CONSISTENCY);
        assertThat(issue.path()).isEqualTo("Encounter.period.end");
        assertThat(issue.resourceId()).isEqualTo("enc-1");
    }

    @Test
    void shouldPassValidEncounterPeriod() {
        Encounter encounter = new Encounter();
        encounter.setId("enc-2");
        Period period = new Period();
        period.setStartElement(new DateTimeType("2023-01-10T10:00:00Z"));
        period.setEndElement(new DateTimeType("2023-01-15T10:00:00Z"));
        encounter.setPeriod(period);

        RuleContext context = new RuleContext(encounter, List.of(encounter), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldFlagInvertedCoveragePeriod() {
        Coverage coverage = new Coverage();
        coverage.setId("cov-1");
        Period period = new Period();
        period.setStartElement(new DateTimeType("2023-12-31T00:00:00Z"));
        period.setEndElement(new DateTimeType("2023-01-01T00:00:00Z"));
        coverage.setPeriod(period);

        RuleContext context = new RuleContext(coverage, List.of(coverage), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).ruleId()).isEqualTo("CONS-001");
    }
}
