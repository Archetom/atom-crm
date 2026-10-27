package io.github.archetom.crm.domain.model;

/** Permission-filtered counts for cumulative customer contact-reminder thresholds. */
public record CustomerContactReminderSummary(
        long over7Days,
        long over15Days,
        long over30Days,
        long over3Months,
        long over6Months,
        long overdue) {

    public CustomerContactReminderSummary {
        if (over7Days < 0 || over15Days < 0 || over30Days < 0
                || over3Months < 0 || over6Months < 0 || overdue < 0) {
            throw new IllegalArgumentException("customer contact reminder counts must not be negative");
        }
    }
}
