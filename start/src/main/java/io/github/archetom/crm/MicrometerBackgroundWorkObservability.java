package io.github.archetom.crm;

import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationRunMetric;
import io.github.archetom.crm.application.automation.AutomationRunStatus;
import io.github.archetom.crm.application.automation.AutomationScheduleStatus;
import io.github.archetom.crm.application.automation.AutomationSchedulingSnapshot;
import io.github.archetom.crm.application.automation.BackgroundWorkLane;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.BackgroundWorkPulseOutcome;
import io.github.archetom.crm.application.automation.NotificationMetric;
import io.github.archetom.crm.application.automation.NotificationDeliveryMetric;
import io.github.archetom.crm.application.automation.WorkTaskReminderMetric;
import io.github.archetom.crm.application.automation.OaAnnouncementPublicationMetric;
import io.github.archetom.crm.application.automation.WorkRecurrenceOutcome;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Prometheus-safe B26 lane/kind observation with no business-identity labels. */
@Component
@RequiredArgsConstructor
public class MicrometerBackgroundWorkObservability implements BackgroundWorkObservability {
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<GaugeKey, AtomicLong> gauges = new ConcurrentHashMap<>();

    @Override
    public void recordPulse(BackgroundWorkLane lane, BackgroundWorkPulseOutcome outcome) {
        meterRegistry.counter("atom.scheduling.pulses", "lane", label(lane), "outcome", label(outcome))
                .increment();
    }

    @Override
    public void recordPulseDuration(BackgroundWorkLane lane, Duration duration) {
        Timer.builder("atom.scheduling.pulse.duration").tag("lane", label(lane))
                .register(meterRegistry).record(duration);
    }

    @Override
    public void recordLaneState(BackgroundWorkLane lane, int active, int queued) {
        gauge("atom.scheduling.lane.active", "lane", label(lane)).set(Math.max(0, active));
        gauge("atom.scheduling.lane.queued", "lane", label(lane)).set(Math.max(0, queued));
    }

    @Override
    public void recordLaneSuccess(BackgroundWorkLane lane, LocalDateTime utcTime) {
        gauge("atom.scheduling.lane.last.success.utc", "lane", label(lane)).set(epochSecond(utcTime));
    }

    @Override
    public void recordWorkTaskReminderOutcome(WorkTaskReminderMetric outcome, long count) {
        if (outcome == null || count < 1) return;
        meterRegistry.counter("atom.work.task.reminders", "outcome", label(outcome)).increment(count);
    }

    @Override
    public void recordOaAnnouncementPublicationOutcome(
            OaAnnouncementPublicationMetric outcome, long count) {
        if (outcome == null || count < 1) return;
        meterRegistry.counter("atom.oa.announcements.publication", "outcome", label(outcome))
                .increment(count);
    }

    @Override
    public void recordNotificationOutcome(NotificationMetric outcome, long count) {
        if (outcome == null || count < 1) return;
        meterRegistry.counter("atom.notifications", "outcome", label(outcome)).increment(count);
    }

    @Override
    public void recordNotificationDeliveryOutcome(NotificationDeliveryMetric outcome, long count) {
        if (outcome == null || count < 1) return;
        meterRegistry.counter("atom.notifications.delivery", "channel", "email", "outcome", label(outcome))
                .increment(count);
    }

    @Override
    public void recordAutomationTransition(AutomationKind kind, AutomationRunMetric outcome, long count) {
        if (count < 1) return;
        meterRegistry.counter("atom.scheduling.automation.runs", "kind", label(kind), "outcome", label(outcome))
                .increment(count);
        if (outcome == AutomationRunMetric.MATERIALIZED) {
            meterRegistry.counter("atom.scheduling.automation.materialized", "kind", label(kind)).increment(count);
        }
    }

    @Override
    public void recordAutomationDueLag(AutomationKind kind, Duration duration) {
        DistributionSummary.builder("atom.scheduling.automation.due.lag")
                .baseUnit("seconds").tag("kind", label(kind)).register(meterRegistry)
                .record(Math.max(0D, duration.toSeconds() + duration.getNano() / 1_000_000_000D));
    }

    @Override
    public void recordAutomationAffected(AutomationKind kind, int affectedCount) {
        DistributionSummary.builder("atom.scheduling.automation.affected")
                .tag("kind", label(kind)).register(meterRegistry).record(Math.max(0, affectedCount));
    }

    @Override
    public void recordWorkRecurrenceOutcome(WorkRecurrenceOutcome outcome, long count) {
        if (outcome == null || count < 1) return;
        meterRegistry.counter("atom.work.recurrence.outcomes", "outcome", label(outcome)).increment(count);
    }

    @Override
    public void recordAutomationSuccess(AutomationKind kind, LocalDateTime utcTime) {
        gauge("atom.scheduling.automation.last.success.utc", "kind", label(kind)).set(epochSecond(utcTime));
    }

    @Override
    public void recordAutomationSnapshot(AutomationSchedulingSnapshot snapshot) {
        for (AutomationKind kind : AutomationKind.values()) {
            for (AutomationRunStatus status : AutomationRunStatus.values()) {
                long count = snapshot.runStates().stream()
                        .filter(state -> state.kind() == kind && state.status() == status)
                        .mapToLong(state -> state.count()).sum();
                gauge("atom.scheduling.automation.run.state", "kind", label(kind), "status", label(status))
                        .set(count);
            }
            for (AutomationScheduleStatus status : AutomationScheduleStatus.values()) {
                long count = snapshot.scheduleStates().stream()
                        .filter(state -> state.kind() == kind && state.status() == status)
                        .mapToLong(state -> state.count()).sum();
                gauge("atom.scheduling.automation.schedule.state", "kind", label(kind), "status", label(status))
                        .set(count);
            }
            recordAutomationSuccess(kind, snapshot.latestSuccessUtc().get(kind));
        }
    }

    private AtomicLong gauge(String name, String... tags) {
        GaugeKey key = new GaugeKey(name, tags);
        return gauges.computeIfAbsent(key, ignored -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(name, value, AtomicLong::get).tags(tags).register(meterRegistry);
            return value;
        });
    }

    private static long epochSecond(LocalDateTime utcTime) {
        return utcTime == null ? 0L : utcTime.toEpochSecond(ZoneOffset.UTC);
    }

    private static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private record GaugeKey(String name, java.util.List<String> tags) {
        private GaugeKey(String name, String... tags) {
            this(name, Arrays.asList(tags.clone()));
        }
    }
}
