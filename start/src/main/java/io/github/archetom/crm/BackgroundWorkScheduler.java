package io.github.archetom.crm;

import io.github.archetom.crm.application.automation.AutomationRunPump;
import io.github.archetom.crm.application.automation.NotificationReminderPump;
import io.github.archetom.crm.application.automation.NotificationDeliveryPump;
import io.github.archetom.crm.application.automation.OaAnnouncementPublicationPump;
import io.github.archetom.crm.application.automation.EnterpriseDataAdoptionRetentionWorker;
import io.github.archetom.crm.application.automation.BackgroundWorkLane;
import io.github.archetom.crm.application.automation.BackgroundWorkPump;
import io.github.archetom.crm.application.automation.BackgroundWorkObservability;
import io.github.archetom.crm.application.automation.BackgroundWorkPulseOutcome;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.OaAnnouncementPublicationProperties;
import io.github.archetom.crm.application.properties.EnterpriseDataAdoptionRetentionProperties;
import io.github.archetom.crm.application.service.attachment.worker.AttachmentWorker;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The sole composition-root wake-up surface. MySQL ledgers own recovery; these
 * bounded local lanes merely prevent one worker family from starving another.
 */
@Component
@EnableScheduling
@Slf4j
public class BackgroundWorkScheduler {
    private final SchedulingProperties properties;
    private final boolean attachmentWorkersEnabled;
    private final ThreadPoolExecutor automationLane;
    private final ThreadPoolExecutor attachmentLane;
    private final ThreadPoolExecutor notificationLane;
    private final ThreadPoolExecutor notificationDeliveryLane;
    private final ThreadPoolExecutor oaAnnouncementPublicationLane;
    private final ThreadPoolExecutor enterpriseDataAdoptionRetentionLane;
    private final BackgroundWorkPump automationPump;
    private final List<BackgroundWorkPump> attachmentPumps;
    private final List<BackgroundWorkPump> notificationPumps;
    private final List<BackgroundWorkPump> notificationDeliveryPumps;
    private final List<BackgroundWorkPump> oaAnnouncementPublicationPumps;
    private final List<BackgroundWorkPump> enterpriseDataAdoptionRetentionPumps;
    private final BackgroundWorkObservability observability;
    private final List<AtomicBoolean> automationInFlight;
    private final List<AtomicBoolean> attachmentInFlight;
    private final List<AtomicBoolean> notificationInFlight;
    private final List<AtomicBoolean> notificationDeliveryInFlight;
    private final List<AtomicBoolean> oaAnnouncementPublicationInFlight;
    private final List<AtomicBoolean> enterpriseDataAdoptionRetentionInFlight;
    private final NotificationReminderRuntimeStatus notificationRuntimeStatus;
    private final NotificationDeliveryRuntimeStatus notificationDeliveryRuntimeStatus;
    private final OaAnnouncementPublicationRuntimeStatus oaAnnouncementPublicationRuntimeStatus;

    public BackgroundWorkScheduler(SchedulingProperties properties,
                                   @Value("${atom.files.workers.enabled:false}") boolean attachmentWorkersEnabled,
                                   AutomationRunPump automationPump,
                                   List<AttachmentWorker> attachmentWorkers,
                                   NotificationReminderPump notificationReminderPump,
                                   NotificationReminderProperties notificationProperties,
                                   NotificationDeliveryPump notificationDeliveryPump,
                                   NotificationDeliveryProperties notificationDeliveryProperties,
                                   OaAnnouncementPublicationPump oaAnnouncementPublicationPump,
                                   OaAnnouncementPublicationProperties oaAnnouncementPublicationProperties,
                                   EnterpriseDataAdoptionRetentionWorker enterpriseDataAdoptionRetentionWorker,
                                   EnterpriseDataAdoptionRetentionProperties enterpriseDataAdoptionRetentionProperties,
                                   BackgroundWorkObservability observability,
                                   NotificationReminderRuntimeStatus notificationRuntimeStatus,
                                   NotificationDeliveryRuntimeStatus notificationDeliveryRuntimeStatus,
                                   OaAnnouncementPublicationRuntimeStatus oaAnnouncementPublicationRuntimeStatus) {
        this.properties = properties;
        this.attachmentWorkersEnabled = attachmentWorkersEnabled;
        this.automationPump = automationPump;
        this.observability = observability;
        automationLane = lane(configuredConcurrency(properties.getAutomationLaneConcurrency()), "automation-run-pump");
        attachmentLane = lane(configuredConcurrency(properties.getAttachmentLaneConcurrency()), "attachment-work-pump");
        notificationLane = lane(configuredConcurrency(notificationProperties.getPumpConcurrency()), "notification-reminder-pump");
        notificationDeliveryLane = lane(configuredConcurrency(notificationDeliveryProperties.getPumpConcurrency()),
                "notification-delivery-pump");
        oaAnnouncementPublicationLane = lane(configuredConcurrency(
                oaAnnouncementPublicationProperties.getPumpConcurrency()),
                "oa-announcement-publication-pump");
        enterpriseDataAdoptionRetentionLane = lane(configuredConcurrency(
                enterpriseDataAdoptionRetentionProperties.getConcurrency()),
                "enterprise-data-adoption-retention-pump");
        attachmentPumps = attachmentWorkers.stream().<BackgroundWorkPump>map(worker -> worker::runOnce).toList();
        notificationPumps = java.util.stream.IntStream.range(0,
                configuredConcurrency(notificationProperties.getPumpConcurrency()))
                .<BackgroundWorkPump>mapToObj(ignored -> notificationReminderPump).toList();
        notificationDeliveryPumps = java.util.stream.IntStream.range(0,
                configuredConcurrency(notificationDeliveryProperties.getPumpConcurrency()))
                .<BackgroundWorkPump>mapToObj(ignored -> notificationDeliveryPump).toList();
        oaAnnouncementPublicationPumps = java.util.stream.IntStream.range(0,
                configuredConcurrency(oaAnnouncementPublicationProperties.getPumpConcurrency()))
                .<BackgroundWorkPump>mapToObj(ignored -> oaAnnouncementPublicationPump).toList();
        enterpriseDataAdoptionRetentionPumps = java.util.stream.IntStream.range(0,
                configuredConcurrency(enterpriseDataAdoptionRetentionProperties.getConcurrency()))
                .<BackgroundWorkPump>mapToObj(ignored -> enterpriseDataAdoptionRetentionWorker).toList();
        automationInFlight = java.util.stream.IntStream
                .range(0, configuredConcurrency(properties.getAutomationLaneConcurrency()))
                .mapToObj(ignored -> new AtomicBoolean()).toList();
        attachmentInFlight = attachmentPumps.stream().map(ignored -> new AtomicBoolean()).toList();
        notificationInFlight = notificationPumps.stream().map(ignored -> new AtomicBoolean()).toList();
        notificationDeliveryInFlight = notificationDeliveryPumps.stream().map(ignored -> new AtomicBoolean()).toList();
        oaAnnouncementPublicationInFlight = oaAnnouncementPublicationPumps.stream()
                .map(ignored -> new AtomicBoolean()).toList();
        enterpriseDataAdoptionRetentionInFlight = enterpriseDataAdoptionRetentionPumps.stream()
                .map(ignored -> new AtomicBoolean()).toList();
        this.notificationProperties = notificationProperties;
        this.notificationDeliveryProperties = notificationDeliveryProperties;
        this.oaAnnouncementPublicationProperties = oaAnnouncementPublicationProperties;
        this.enterpriseDataAdoptionRetentionProperties = enterpriseDataAdoptionRetentionProperties;
        this.notificationRuntimeStatus = notificationRuntimeStatus;
        this.notificationDeliveryRuntimeStatus = notificationDeliveryRuntimeStatus;
        this.oaAnnouncementPublicationRuntimeStatus = oaAnnouncementPublicationRuntimeStatus;
    }

    private final NotificationReminderProperties notificationProperties;
    private final NotificationDeliveryProperties notificationDeliveryProperties;
    private final OaAnnouncementPublicationProperties oaAnnouncementPublicationProperties;
    private final EnterpriseDataAdoptionRetentionProperties enterpriseDataAdoptionRetentionProperties;

    @Scheduled(fixedDelayString = "${atom.scheduling.pulse-delay:PT5S}")
    public void pulse() {
        if (!properties.isPulsesEnabled()) return;
        var policy = properties.policy();
        if (policy.enabled() && policy.valid()) {
            automationInFlight.forEach(inFlight ->
                    submit(automationLane, inFlight, automationPump, BackgroundWorkLane.AUTOMATION));
        }
        if (attachmentWorkersEnabled) {
            for (int index = 0; index < attachmentPumps.size(); index++) {
                submit(attachmentLane, attachmentInFlight.get(index), attachmentPumps.get(index),
                        BackgroundWorkLane.ATTACHMENT);
            }
        }
        if (notificationProperties.isEnabled() && notificationProperties.valid()) {
            for (int index = 0; index < notificationPumps.size(); index++) {
                submit(notificationLane, notificationInFlight.get(index), notificationPumps.get(index),
                        BackgroundWorkLane.NOTIFICATION);
            }
        }
        // The delivery pump maintains deadline and retention records even while
        // provider dispatch is disabled or misconfigured; only provider I/O is gated.
        for (int index = 0; index < notificationDeliveryPumps.size(); index++) {
            submit(notificationDeliveryLane, notificationDeliveryInFlight.get(index), notificationDeliveryPumps.get(index),
                    BackgroundWorkLane.NOTIFICATION_DELIVERY);
        }
        if (oaAnnouncementPublicationProperties.isEnabled()
                && oaAnnouncementPublicationProperties.valid()) {
            for (int index = 0; index < oaAnnouncementPublicationPumps.size(); index++) {
                submit(oaAnnouncementPublicationLane, oaAnnouncementPublicationInFlight.get(index),
                        oaAnnouncementPublicationPumps.get(index),
                        BackgroundWorkLane.OA_ANNOUNCEMENT_PUBLICATION);
            }
        }
        if (enterpriseDataAdoptionRetentionProperties.isEnabled()
                && enterpriseDataAdoptionRetentionProperties.valid()) {
            for (int index = 0; index < enterpriseDataAdoptionRetentionPumps.size(); index++) {
                submit(enterpriseDataAdoptionRetentionLane,
                        enterpriseDataAdoptionRetentionInFlight.get(index),
                        enterpriseDataAdoptionRetentionPumps.get(index),
                        BackgroundWorkLane.ENTERPRISE_DATA_ADOPTION_RETENTION);
            }
        }
        observability.recordLaneState(BackgroundWorkLane.AUTOMATION,
                automationLane.getActiveCount(), automationLane.getQueue().size());
        observability.recordLaneState(BackgroundWorkLane.ATTACHMENT,
                attachmentLane.getActiveCount(), attachmentLane.getQueue().size());
        observability.recordLaneState(BackgroundWorkLane.NOTIFICATION,
                notificationLane.getActiveCount(), notificationLane.getQueue().size());
        observability.recordLaneState(BackgroundWorkLane.NOTIFICATION_DELIVERY,
                notificationDeliveryLane.getActiveCount(), notificationDeliveryLane.getQueue().size());
        observability.recordLaneState(BackgroundWorkLane.OA_ANNOUNCEMENT_PUBLICATION,
                oaAnnouncementPublicationLane.getActiveCount(), oaAnnouncementPublicationLane.getQueue().size());
        observability.recordLaneState(BackgroundWorkLane.ENTERPRISE_DATA_ADOPTION_RETENTION,
                enterpriseDataAdoptionRetentionLane.getActiveCount(),
                enterpriseDataAdoptionRetentionLane.getQueue().size());
    }

    private void submit(ThreadPoolExecutor lane, AtomicBoolean inFlight, BackgroundWorkPump pump,
                        BackgroundWorkLane laneName) {
        if (!inFlight.compareAndSet(false, true)) {
            observability.recordPulse(laneName, BackgroundWorkPulseOutcome.SKIPPED_IN_FLIGHT);
            return;
        }
        try {
            observability.recordPulse(laneName, BackgroundWorkPulseOutcome.SUBMITTED);
            lane.execute(() -> {
                long startedNanos = System.nanoTime();
                try {
                    pump.runOnce();
                    observability.recordPulse(laneName, BackgroundWorkPulseOutcome.COMPLETED);
                    observability.recordLaneSuccess(laneName, LocalDateTime.now(ZoneOffset.UTC));
                    if (laneName == BackgroundWorkLane.NOTIFICATION) {
                        notificationRuntimeStatus.recordSuccess();
                    }
                    if (laneName == BackgroundWorkLane.NOTIFICATION_DELIVERY) {
                        notificationDeliveryRuntimeStatus.recordSuccess();
                    }
                    if (laneName == BackgroundWorkLane.OA_ANNOUNCEMENT_PUBLICATION) {
                        oaAnnouncementPublicationRuntimeStatus.recordSuccess();
                    }
                } catch (RuntimeException exception) {
                    // Durable owners retain all details; this surface never logs job or business identity.
                    log.warn("Background work pump invocation failed: lane={}", laneName);
                    observability.recordPulse(laneName, BackgroundWorkPulseOutcome.FAILED);
                    if (laneName == BackgroundWorkLane.NOTIFICATION) {
                        notificationRuntimeStatus.recordFailure();
                    }
                    if (laneName == BackgroundWorkLane.NOTIFICATION_DELIVERY) {
                        notificationDeliveryRuntimeStatus.recordFailure();
                    }
                    if (laneName == BackgroundWorkLane.OA_ANNOUNCEMENT_PUBLICATION) {
                        oaAnnouncementPublicationRuntimeStatus.recordFailure();
                    }
                } finally {
                    observability.recordPulseDuration(laneName,
                            Duration.ofNanos(System.nanoTime() - startedNanos));
                    inFlight.set(false);
                    observability.recordLaneState(laneName, lane.getActiveCount(), lane.getQueue().size());
                }
            });
        } catch (RuntimeException exception) {
            inFlight.set(false);
            log.warn("Background work pulse was rejected: lane={}", laneName);
            observability.recordPulse(laneName, BackgroundWorkPulseOutcome.REJECTED);
            observability.recordLaneState(laneName, lane.getActiveCount(), lane.getQueue().size());
        }
    }

    private static ThreadPoolExecutor lane(int concurrency, String threadName) {
        return new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency), task -> Thread.ofPlatform().daemon().name(threadName).unstarted(task),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static int configuredConcurrency(int requested) {
        return requested >= 1 && requested <= SchedulingProperties.MAX_LANE_CONCURRENCY ? requested : 1;
    }

    @PreDestroy
    void shutdown() {
        automationLane.shutdown();
        attachmentLane.shutdown();
        notificationLane.shutdown();
        notificationDeliveryLane.shutdown();
        oaAnnouncementPublicationLane.shutdown();
        enterpriseDataAdoptionRetentionLane.shutdown();
    }
}
