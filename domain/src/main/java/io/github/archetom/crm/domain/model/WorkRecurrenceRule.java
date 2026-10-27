package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.exception.WorkTaskDomainException;
import io.github.archetom.crm.domain.exception.WorkRecurrenceTimeRangeExceededException;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded local-calendar recurrence. It deliberately has no cron, RRULE,
 * caller offset, holiday, or arbitrary skip-list vocabulary.
 */
public record WorkRecurrenceRule(
        WorkRecurrenceFrequency frequency,
        int interval,
        Set<DayOfWeek> weekdays,
        Integer monthDay,
        boolean lastDayOfMonth,
        String zoneId,
        LocalDate startDate,
        LocalTime localTime,
        LocalDate endDate
) {
    public WorkRecurrenceRule {
        if (frequency == null || interval < 1 || zoneId == null || zoneId.isBlank()
                || startDate == null || localTime == null || !supported(startDate)
                || localTime.getSecond() != 0 || localTime.getNano() != 0
                || endDate != null && (!supported(endDate) || endDate.isBefore(startDate))) {
            throw new WorkTaskDomainException("work recurrence rule is invalid");
        }
        requireRegionZone(zoneId);
        weekdays = weekdays == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(weekdays));
        switch (frequency) {
            case DAILY -> require(interval <= 365 && weekdays.isEmpty() && monthDay == null && !lastDayOfMonth);
            case WEEKLY -> require(interval <= 52 && !weekdays.isEmpty() && weekdays.size() <= 7
                    && monthDay == null && !lastDayOfMonth);
            case MONTHLY -> require(interval <= 24 && weekdays.isEmpty()
                    && ((monthDay != null && monthDay >= 1 && monthDay <= 31 && !lastDayOfMonth)
                    || (monthDay == null && lastDayOfMonth)));
        }
    }

    public LocalDateTime firstNominal() {
        return switch (frequency) {
            case DAILY -> LocalDateTime.of(startDate, localTime);
            case WEEKLY -> firstWeeklyNominal();
            case MONTHLY -> firstMonthlyNominal();
        };
    }

    /** First nominal time at or after the supplied local wall-clock boundary. */
    public LocalDateTime nominalAtOrAfter(LocalDateTime boundary) {
        if (boundary == null) throw new WorkTaskDomainException("work recurrence boundary is required");
        LocalDateTime candidate = firstNominal();
        if (!candidate.isBefore(boundary)) return candidate;
        return switch (frequency) {
            case DAILY -> dailyAtOrAfter(boundary);
            case WEEKLY -> weeklyAtOrAfter(boundary);
            case MONTHLY -> monthlyAtOrAfter(boundary);
        };
    }

    public LocalDateTime nextNominal(LocalDateTime current) {
        if (current == null || current.isBefore(firstNominal())) {
            throw new WorkTaskDomainException("work recurrence current occurrence is invalid");
        }
        return nominalAtOrAfter(plusSeconds(current, 1));
    }

    /** Returns an occurrence only when both its local and resolved UTC values fit MySQL DATETIME. */
    public Optional<LocalDateTime> nominalAtOrAfterPersistable(LocalDateTime boundary) {
        if (boundary == null) throw new WorkTaskDomainException("work recurrence boundary is required");
        try {
            return persistable(nominalAtOrAfter(boundary));
        } catch (WorkRecurrenceTimeRangeExceededException exception) {
            return Optional.empty();
        }
    }

    /** A null-free, persistence-safe alternative for advancing a stored cursor. */
    public Optional<LocalDateTime> nextNominalPersistable(LocalDateTime current) {
        try {
            return persistable(nextNominal(current));
        } catch (WorkRecurrenceTimeRangeExceededException exception) {
            return Optional.empty();
        }
    }

    public boolean isWithinEnd(LocalDateTime nominal) {
        return nominal != null && supported(nominal.toLocalDate())
                && (endDate == null || !nominal.toLocalDate().isAfter(endDate));
    }

    /** True only for an in-range wall-clock occurrence selected by this exact rule. */
    public boolean isNominalOccurrence(LocalDateTime nominal) {
        return isWithinEnd(nominal) && nominalAtOrAfter(nominal).equals(nominal);
    }

    /** SHIFT_FORWARD for gaps and the earlier valid offset for overlaps. */
    public WorkRecurrenceResolution resolve(LocalDateTime nominal) {
        if (nominal == null || !isNominalOccurrence(nominal)) {
            throw new WorkTaskDomainException("work recurrence nominal occurrence is invalid");
        }
        ZoneRules rules = ZoneId.of(zoneId).getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(nominal);
        LocalDateTime resolved = nominal;
        ZoneOffset offset;
        if (offsets.size() == 1) {
            offset = offsets.getFirst();
        } else if (offsets.size() >= 2) {
            offset = offsets.stream().max(Comparator.comparingInt(ZoneOffset::getTotalSeconds)).orElseThrow();
        } else {
            ZoneOffsetTransition transition = rules.getTransition(nominal);
            if (transition == null) {
                throw new WorkTaskDomainException("work recurrence zone transition is unavailable");
            }
            resolved = transition.getDateTimeAfter();
            offset = transition.getOffsetAfter();
        }
        if (!supported(resolved.toLocalDate())) {
            throw new WorkRecurrenceTimeRangeExceededException(
                    "work recurrence resolved occurrence exceeds supported range");
        }
        LocalDateTime resolvedUtc = resolved.toInstant(offset).atOffset(ZoneOffset.UTC).toLocalDateTime();
        if (!supported(resolvedUtc.toLocalDate())) {
            throw new WorkRecurrenceTimeRangeExceededException(
                    "work recurrence resolved occurrence exceeds supported range");
        }
        return new WorkRecurrenceResolution(nominal, resolved, offset.getTotalSeconds(), resolvedUtc);
    }

    private LocalDateTime dailyAtOrAfter(LocalDateTime boundary) {
        long days = ChronoUnit.DAYS.between(startDate, boundary.toLocalDate());
        long periods = Math.max(0, Math.floorDiv(days, interval));
        LocalDate candidate = plusDays(startDate, periods * (long) interval);
        LocalDateTime result = LocalDateTime.of(candidate, localTime);
        if (result.isBefore(boundary)) result = plusDays(result, interval);
        return result;
    }

    private LocalDateTime weeklyAtOrAfter(LocalDateTime boundary) {
        LocalDate anchorWeek = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate boundaryWeek = boundary.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weeks = ChronoUnit.WEEKS.between(anchorWeek, boundaryWeek);
        long periods = Math.max(0, Math.floorDiv(weeks, interval));
        LocalDate week = plusWeeks(anchorWeek, periods * (long) interval);
        for (int guard = 0; guard < 100_000; guard++, week = plusWeeks(week, interval)) {
            for (DayOfWeek day : weekdays.stream().sorted().toList()) {
                LocalDateTime candidate = LocalDateTime.of(plusDays(week, day.getValue() - 1L), localTime);
                if (!candidate.isBefore(firstNominal()) && !candidate.isBefore(boundary)) return candidate;
            }
        }
        throw new WorkTaskDomainException("work recurrence exceeds supported range");
    }

    private LocalDateTime firstWeeklyNominal() {
        LocalDate week = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (DayOfWeek day : weekdays.stream().sorted().toList()) {
            LocalDateTime candidate = LocalDateTime.of(week.plusDays(day.getValue() - 1L), localTime);
            if (!candidate.isBefore(LocalDateTime.of(startDate, localTime))) return candidate;
        }
        DayOfWeek firstDay = weekdays.stream().min(Comparator.naturalOrder()).orElseThrow();
        return LocalDateTime.of(plusDays(plusWeeks(week, interval), firstDay.getValue() - 1L), localTime);
    }

    private LocalDateTime monthlyAtOrAfter(LocalDateTime boundary) {
        LocalDate firstMonth = startDate.withDayOfMonth(1);
        LocalDate boundaryMonth = boundary.toLocalDate().withDayOfMonth(1);
        long months = ChronoUnit.MONTHS.between(firstMonth, boundaryMonth);
        long periods = Math.max(0, Math.floorDiv(months, interval));
        LocalDate month = plusMonths(firstMonth, periods * (long) interval);
        for (int guard = 0; guard < 100_000; guard++, month = plusMonths(month, interval)) {
            LocalDate date = monthlyDate(month);
            LocalDateTime candidate = LocalDateTime.of(date, localTime);
            if (!candidate.isBefore(firstNominal()) && !candidate.isBefore(boundary)) return candidate;
        }
        throw new WorkTaskDomainException("work recurrence exceeds supported range");
    }

    private LocalDateTime firstMonthlyNominal() {
        LocalDate firstMonth = startDate.withDayOfMonth(1);
        LocalDateTime candidate = LocalDateTime.of(monthlyDate(firstMonth), localTime);
        return candidate.isBefore(LocalDateTime.of(startDate, localTime))
                ? LocalDateTime.of(monthlyDate(plusMonths(firstMonth, interval)), localTime)
                : candidate;
    }

    private LocalDate monthlyDate(LocalDate firstOfMonth) {
        int day = lastDayOfMonth ? firstOfMonth.lengthOfMonth() : Math.min(monthDay, firstOfMonth.lengthOfMonth());
        return firstOfMonth.withDayOfMonth(day);
    }

    private Optional<LocalDateTime> persistable(LocalDateTime nominal) {
        if (!isWithinEnd(nominal)) return Optional.empty();
        try {
            resolve(nominal);
            return Optional.of(nominal);
        } catch (WorkRecurrenceTimeRangeExceededException exception) {
            return Optional.empty();
        }
    }

    private static boolean supported(LocalDate value) {
        return value.getYear() >= 1000 && value.getYear() <= 9999;
    }

    private static void requireRegionZone(String value) {
        if (!ZoneId.getAvailableZoneIds().contains(value)) {
            throw new WorkTaskDomainException("work recurrence zone is invalid");
        }
        try {
            if (ZoneId.of(value) instanceof ZoneOffset) {
                throw new WorkTaskDomainException("work recurrence zone is invalid");
            }
        } catch (DateTimeException exception) {
            throw new WorkTaskDomainException("work recurrence zone is invalid");
        }
    }

    private static LocalDate plusDays(LocalDate value, long amount) {
        try { return value.plusDays(amount); }
        catch (DateTimeException | ArithmeticException exception) { throw timeRangeExceeded(); }
    }

    private static LocalDateTime plusDays(LocalDateTime value, long amount) {
        try { return value.plusDays(amount); }
        catch (DateTimeException | ArithmeticException exception) { throw timeRangeExceeded(); }
    }

    private static LocalDate plusWeeks(LocalDate value, long amount) {
        try { return value.plusWeeks(amount); }
        catch (DateTimeException | ArithmeticException exception) { throw timeRangeExceeded(); }
    }

    private static LocalDate plusMonths(LocalDate value, long amount) {
        try { return value.plusMonths(amount); }
        catch (DateTimeException | ArithmeticException exception) { throw timeRangeExceeded(); }
    }

    private static LocalDateTime plusSeconds(LocalDateTime value, long amount) {
        try { return value.plusSeconds(amount); }
        catch (DateTimeException | ArithmeticException exception) { throw timeRangeExceeded(); }
    }

    private static WorkRecurrenceTimeRangeExceededException timeRangeExceeded() {
        return new WorkRecurrenceTimeRangeExceededException("work recurrence calendar exceeds supported range");
    }

    private static void require(boolean condition) {
        if (!condition) throw new WorkTaskDomainException("work recurrence rule is invalid");
    }
}
