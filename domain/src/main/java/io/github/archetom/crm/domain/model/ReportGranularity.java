package io.github.archetom.crm.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Calendar periods used by fixed reports; weeks always start on ISO Monday. */
public enum ReportGranularity {
    DAY {
        @Override
        public LocalDate periodStart(LocalDate date) {
            return date;
        }

        @Override
        public LocalDate next(LocalDate periodStart) {
            return periodStart.plusDays(1);
        }
    },
    WEEK {
        @Override
        public LocalDate periodStart(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        @Override
        public LocalDate next(LocalDate periodStart) {
            return periodStart.plusWeeks(1);
        }
    },
    MONTH {
        @Override
        public LocalDate periodStart(LocalDate date) {
            return date.withDayOfMonth(1);
        }

        @Override
        public LocalDate next(LocalDate periodStart) {
            return periodStart.plusMonths(1);
        }
    };

    public abstract LocalDate periodStart(LocalDate date);

    public abstract LocalDate next(LocalDate periodStart);

    public static ReportGranularity parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("report granularity is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("report granularity is invalid", exception);
        }
    }
}
