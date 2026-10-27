package io.github.archetom.crm.application.automation;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.notification.NotificationChannel;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapter;
import io.github.archetom.crm.application.port.out.notification.NotificationChannelAdapterRegistry;
import io.github.archetom.crm.application.port.out.notification.NotificationDelivery;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryClaim;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryOutcomeCounts;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStatus;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliveryStore;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceAdmission;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceAdmissionRegistry;
import io.github.archetom.crm.application.port.out.notification.NotificationDeliverySourceKind;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchCommand;
import io.github.archetom.crm.application.port.out.notification.NotificationDispatchResult;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.application.port.out.notification.NotificationReadinessSnapshot;
import io.github.archetom.crm.application.properties.NotificationDeliveryProperties;
import io.github.archetom.crm.application.properties.NotificationReminderProperties;
import io.github.archetom.crm.application.properties.SchedulingProperties;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.OperationLogPolicy;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * B27c's independent transactional-outbox pump. Provider I/O always happens
 * outside the command transaction and only after task-first admission freezes
 * the exact delivery attempt.
 */
@Component
public class NotificationDeliveryPump implements BackgroundWorkPump, DisposableBean {
    private static final int RETENTION_BATCH = 100;

    private final NotificationDeliveryStore deliveryStore;
    private final NotificationChannelAdapterRegistry adapterRegistry;
    private final NotificationDeliveryProperties properties;
    private final NotificationReminderProperties reminderProperties;
    private final CommandServiceTemplate commandTemplate;
    private final BackgroundWorkObservability observability;
    private final NotificationDeliverySourceAdmissionRegistry sourceAdmissions;
    private final UUID leaseOwner = UUID.randomUUID();
    private final ExecutorService dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore dispatchCapacity;

    public NotificationDeliveryPump(NotificationDeliveryStore deliveryStore,
                                    NotificationPreferenceStore preferenceStore,
                                    NotificationRepository notificationRepository,
                                    WorkTaskRepository taskRepository,
                                    MemberProfileRepository profileRepository,
                                    TenantAuthorizationEvaluator authorizationEvaluator,
                                    NotificationChannelAdapterRegistry adapterRegistry,
                                    NotificationDeliveryProperties properties,
                                    NotificationReminderProperties reminderProperties,
                                    CommandServiceTemplate commandTemplate,
                                    BackgroundWorkObservability observability) {
        this(deliveryStore, preferenceStore, notificationRepository, taskRepository, profileRepository,
                authorizationEvaluator, adapterRegistry, properties, reminderProperties, commandTemplate,
                observability, legacySourceAdmissions(deliveryStore, preferenceStore, notificationRepository,
                        taskRepository, profileRepository, authorizationEvaluator, properties));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationDeliveryPump(NotificationDeliveryStore deliveryStore,
                                    NotificationPreferenceStore preferenceStore,
                                    NotificationRepository notificationRepository,
                                    WorkTaskRepository taskRepository,
                                    MemberProfileRepository profileRepository,
                                    TenantAuthorizationEvaluator authorizationEvaluator,
                                    NotificationChannelAdapterRegistry adapterRegistry,
                                    NotificationDeliveryProperties properties,
                                    NotificationReminderProperties reminderProperties,
                                    CommandServiceTemplate commandTemplate,
                                    BackgroundWorkObservability observability,
                                    NotificationDeliverySourceAdmissionRegistry sourceAdmissions) {
        this.deliveryStore = deliveryStore;
        this.adapterRegistry = adapterRegistry;
        this.properties = properties;
        this.reminderProperties = reminderProperties;
        this.commandTemplate = commandTemplate;
        this.observability = observability;
        this.sourceAdmissions = sourceAdmissions;
        dispatchCapacity = new Semaphore(Math.clamp(properties.getPumpConcurrency(), 1,
                SchedulingProperties.MAX_LANE_CONCURRENCY));
    }

    /** Compatibility constructor support for focused Work-only unit fixtures. Production injects the full registry. */
    private static NotificationDeliverySourceAdmissionRegistry legacySourceAdmissions(
            NotificationDeliveryStore deliveryStore, NotificationPreferenceStore preferenceStore,
            NotificationRepository notificationRepository, WorkTaskRepository taskRepository,
            MemberProfileRepository profileRepository, TenantAuthorizationEvaluator authorizationEvaluator,
            NotificationDeliveryProperties properties) {
        NotificationDeliveryAdmissionSupport support = new NotificationDeliveryAdmissionSupport(deliveryStore,
                preferenceStore, profileRepository, properties);
        NotificationDeliverySourceAdmission work = new WorkTaskNotificationDeliverySourceAdmission(support,
                notificationRepository, taskRepository, authorizationEvaluator);
        NotificationDeliverySourceAdmission oaUnavailable = new NotificationDeliverySourceAdmission() {
            @Override public NotificationDeliverySourceKind sourceKind() {
                return NotificationDeliverySourceKind.OA_ANNOUNCEMENT_RECIPIENT;
            }
            @Override public NotificationDeliveryAdmission admit(NotificationDelivery claim,
                                                                   NotificationChannelAdapter adapter,
                                                                   UUID leaseOwner, LocalDateTime now) {
                return NotificationDeliveryAdmission.none(claim);
            }
        };
        return new NotificationDeliverySourceAdmissionRegistry(java.util.List.of(work, oaUnavailable));
    }

    @Override
    public void runOnce() {
        maintainDeliveryLedger();
        if (!properties.dispatchEnabled(reminderProperties.getNotificationRetention())) return;
        NotificationChannelAdapter adapter = adapterRegistry.require(NotificationChannel.EMAIL);
        List<String> readyTemplates = readyTemplates(adapter, deliveryStore.currentUtc());
        if (readyTemplates.isEmpty()) return;
        NotificationDeliveryClaim claim = requireSuccess(commandTemplate.execute(
                UseCaseOperation.NOTIFICATION_DELIVERY_EFFECT, new ServiceOperation<>() {
                    @Override public NotificationDeliveryClaim execute() {
                        LocalDateTime now = deliveryStore.currentUtc();
                        return deliveryStore.claimDue(readyTemplates, leaseOwner, now, properties.getLeaseDuration(),
                                properties.getMaxAttempts());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND), "claim");
        record(NotificationDeliveryMetric.DEAD, claim.deadCount());
        record(NotificationDeliveryMetric.AMBIGUOUS, claim.ambiguousCount());
        if (claim.delivery().isEmpty()) return;

        NotificationDelivery delivery = claim.delivery().get();
        record(NotificationDeliveryMetric.CLAIMED, 1);
        if (!dispatchCapacity.tryAcquire()) {
            recoverNoDispatchFailure(delivery);
            throw new IllegalStateException("notification delivery dispatch capacity is exhausted");
        }
        NotificationDeliveryAdmission admission;
        try {
            admission = requireSuccess(commandTemplate.execute(UseCaseOperation.NOTIFICATION_DELIVERY_EFFECT,
                    new ServiceOperation<>() {
                        @Override public NotificationDeliveryAdmission execute() {
                            return admit(delivery, adapter, deliveryStore.currentUtc());
                        }
                    }, OperationLogPolicy.SAFE_BACKGROUND), "admission");
        } catch (RuntimeException exception) {
            dispatchCapacity.release();
            recoverNoDispatchFailure(delivery);
            throw exception;
        }
        admission.terminalStatus().ifPresent(this::record);
        if (admission.command().isEmpty()) {
            dispatchCapacity.release();
            if (admission.recoverNoDispatch()) recoverNoDispatchFailure(delivery);
            return;
        }

        Optional<Duration> dispatchBudget = dispatchBudget(admission.delivery());
        if (dispatchBudget.isEmpty()) {
            dispatchCapacity.release();
            recoverNoDispatchFailure(delivery);
            return;
        }
        DispatchAttempt attempt = dispatch(adapter, admission.command().get(), dispatchBudget.get());
        NotificationDispatchResult dispatchResult = attempt.result();
        Optional<NotificationDeliveryStatus> settled = requireSuccess(commandTemplate.execute(
                UseCaseOperation.NOTIFICATION_DELIVERY_EFFECT, new ServiceOperation<>() {
                    @Override public Optional<NotificationDeliveryStatus> execute() {
                        return deliveryStore.settleDispatch(delivery.tenantId(), delivery.id(), leaseOwner,
                                delivery.leaseFence(), dispatchResult.outcome(), deliveryStore.currentUtc(),
                                properties.getMaxAttempts(), properties.getRetryBackoff());
                    }
        }, OperationLogPolicy.SAFE_BACKGROUND), "settlement");
        if (settled.isEmpty()) {
            throw new IllegalStateException("notification delivery settlement lost its lease");
        }
        record(settled.get());
        if (attempt.timedOut()) throw new IllegalStateException("notification delivery dispatch timed out");
    }

    private NotificationDeliveryAdmission admit(NotificationDelivery claim, NotificationChannelAdapter adapter,
                                                LocalDateTime now) {
        return sourceAdmissions.require(claim.sourceKind()).admit(claim, adapter, leaseOwner, now);
    }

    private void recoverNoDispatchFailure(NotificationDelivery delivery) {
        Optional<NotificationDeliveryStatus> recovered = requireSuccess(commandTemplate.execute(
                UseCaseOperation.NOTIFICATION_DELIVERY_EFFECT, new ServiceOperation<>() {
                    @Override public Optional<NotificationDeliveryStatus> execute() {
                        return deliveryStore.recoverNoDispatchFailure(delivery.tenantId(), delivery.id(), leaseOwner,
                                delivery.leaseFence(), deliveryStore.currentUtc(), properties.getMaxAttempts(),
                                properties.getRetryBackoff());
                    }
                }, OperationLogPolicy.SAFE_BACKGROUND), "admission recovery");
        recovered.ifPresent(this::record);
    }

    private void maintainDeliveryLedger() {
        NotificationDeliveryOutcomeCounts outcomes = requireSuccess(commandTemplate.execute(
                UseCaseOperation.NOTIFICATION_DELIVERY_EFFECT, new ServiceOperation<>() {
            @Override public NotificationDeliveryOutcomeCounts execute() {
                LocalDateTime now = deliveryStore.currentUtc();
                NotificationDeliveryOutcomeCounts expired = deliveryStore.expireOverdue(now, RETENTION_BATCH);
                deliveryStore.purgeTerminal(now.minus(properties.safeTerminalRetention(
                        reminderProperties.getNotificationRetention())), RETENTION_BATCH);
                return expired;
            }
        }, OperationLogPolicy.SAFE_BACKGROUND), "delivery maintenance");
        record(outcomes);
    }

    private List<String> readyTemplates(NotificationChannelAdapter adapter, LocalDateTime now) {
        return io.github.archetom.crm.application.port.out.notification.NotificationDeliveryTemplate
                .emailTemplateRevisions().stream()
                .filter(templateRevision -> readyForTemplate(adapter, templateRevision, now))
                .toList();
    }

    private boolean readyForTemplate(NotificationChannelAdapter adapter, String templateRevision, LocalDateTime now) {
        NotificationReadinessSnapshot readiness = adapter.readiness(templateRevision);
        if (readiness == null) readiness = adapter.readiness();
        return readiness != null && readiness.readyAt(NotificationChannel.EMAIL, templateRevision,
                properties.getAdapterContract(), properties.getAdapterBuild(), properties.getBindingRevision(), now);
    }

    private Optional<Duration> dispatchBudget(NotificationDelivery delivery) {
        if (delivery == null || delivery.leaseUntil() == null) return Optional.empty();
        LocalDateTime expiresAt = delivery.leaseUntil().isBefore(delivery.deadlineAt())
                ? delivery.leaseUntil() : delivery.deadlineAt();
        Duration remaining = Duration.between(deliveryStore.currentUtc(), expiresAt);
        if (remaining.compareTo(properties.getSettlementReserve()) <= 0) return Optional.empty();
        Duration available = remaining.minus(properties.getSettlementReserve());
        return Optional.of(available.compareTo(properties.getDispatchDeadline()) < 0 ? available
                : properties.getDispatchDeadline());
    }

    private DispatchAttempt dispatch(NotificationChannelAdapter adapter, NotificationDispatchCommand command, Duration deadline) {
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) dispatchCapacity.release();
        };
        Future<NotificationDispatchResult> future;
        try {
            future = dispatchExecutor.submit(() -> {
                started.set(true);
                try {
                    return adapter.dispatch(command, deadline);
                } finally {
                    release.run();
                }
            });
        } catch (RuntimeException exception) {
            release.run();
            return new DispatchAttempt(NotificationDispatchResult.ambiguous(), false);
        }
        try {
            NotificationDispatchResult result = future.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
            return new DispatchAttempt(result == null ? NotificationDispatchResult.ambiguous() : result, false);
        } catch (TimeoutException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            future.cancel(true);
            if (!started.get()) release.run();
            return new DispatchAttempt(NotificationDispatchResult.ambiguous(), true);
        } catch (ExecutionException | CancellationException exception) {
            return new DispatchAttempt(NotificationDispatchResult.ambiguous(), false);
        }
    }

    @Override
    public void destroy() {
        // The adapter contract requires interruption support, so shutdown also
        // cancels any still-running bounded provider attempt.
        dispatchExecutor.shutdownNow();
    }

    private void record(NotificationDeliveryStatus status) {
        switch (status) {
            case ACCEPTED -> record(NotificationDeliveryMetric.ACCEPTED, 1);
            case REJECTED -> record(NotificationDeliveryMetric.REJECTED, 1);
            case SUPPRESSED -> record(NotificationDeliveryMetric.SUPPRESSED, 1);
            case AMBIGUOUS -> record(NotificationDeliveryMetric.AMBIGUOUS, 1);
            case DEAD -> record(NotificationDeliveryMetric.DEAD, 1);
            case RETRY_WAIT -> record(NotificationDeliveryMetric.RETRY_WAIT, 1);
            default -> { }
        }
    }

    private void record(NotificationDeliveryMetric outcome, long count) {
        if (count > 0) observability.recordNotificationDeliveryOutcome(outcome, count);
    }

    private void record(NotificationDeliveryOutcomeCounts outcomes) {
        if (outcomes == null) return;
        record(NotificationDeliveryMetric.SUPPRESSED, outcomes.suppressed());
        record(NotificationDeliveryMetric.AMBIGUOUS, outcomes.ambiguous());
        record(NotificationDeliveryMetric.DEAD, outcomes.dead());
    }

    private record DispatchAttempt(NotificationDispatchResult result, boolean timedOut) { }

    private static <T> T requireSuccess(Result<T> result, String stage) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("notification delivery " + stage + " failed");
        }
        return result.getData();
    }
}
