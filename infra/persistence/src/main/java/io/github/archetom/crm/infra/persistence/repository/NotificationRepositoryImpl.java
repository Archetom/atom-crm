package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.Notification;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.NotificationKind;
import io.github.archetom.crm.domain.model.NotificationQuery;
import io.github.archetom.crm.domain.model.NotificationReadResult;
import io.github.archetom.crm.domain.model.NotificationStatus;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.repository.NotificationRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.domain.valueobject.WorkTaskReminderOccurrenceId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NotificationMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** SQL inbox predicates apply active membership and current Work participation before slicing. */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {
    private final NotificationMapper mapper;

    @Override
    public Notification save(TenantId tenantId, Notification notification) {
        if (tenantId == null || notification == null || notification.getTenantId() == null
                || !tenantId.sameValueAs(notification.getTenantId())) {
            throw new IllegalArgumentException("notification tenant is invalid");
        }
        NotificationPO row = po(notification);
        try {
            if (mapper.insert(row) != 1) throw new IllegalStateException("notification insert failed");
            notification.onPersisted(new NotificationId(row.getId()), row.getCreatedTime(), row.getUpdatedTime());
            return notification;
        } catch (DuplicateKeyException exception) {
            NotificationPO existing = mapper.selectOne(new QueryWrapper<NotificationPO>()
                    .eq("tenant_id", tenantId.getValue())
                    .eq("reminder_occurrence_id", notification.getReminderOccurrenceId().getValue())
                    .eq("recipient_actor_id", notification.getRecipientMemberId().getValue()));
            if (existing == null) throw exception;
            return domain(existing);
        }
    }

    @Override
    public int withdrawStaleForTask(TenantId tenantId, WorkTask task, LocalDateTime now) {
        if (tenantId == null || task == null || task.getId() == null || now == null) {
            throw new IllegalArgumentException("notification task withdrawal is invalid");
        }
        LambdaUpdateWrapper<NotificationPO> update = new LambdaUpdateWrapper<NotificationPO>()
                .eq(NotificationPO::getTenantId, tenantId.getValue())
                .eq(NotificationPO::getWorkTaskId, task.getId().getValue())
                .eq(NotificationPO::getStatus, NotificationStatus.UNREAD.name())
                .set(NotificationPO::getStatus, NotificationStatus.WITHDRAWN.name())
                .set(NotificationPO::getReadTime, null)
                .set(NotificationPO::getUpdatedTime, now);
        if (task.getStatus().isOpen()) {
            update.ne(NotificationPO::getTaskNotificationProjectionVersion,
                    task.getNotificationProjectionVersion());
        }
        return mapper.update(null, update);
    }

    @Override
    public void purgeExpired(LocalDateTime beforeExclusive, int limit) {
        if (beforeExclusive == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("notification retention request is invalid");
        }
        mapper.deleteExpired(beforeExclusive, limit);
    }

    @Override
    public PageResult<Notification> findInboxPage(TenantId tenantId, NotificationQuery query) {
        if (tenantId == null || query == null) throw new IllegalArgumentException("notification inbox query is invalid");
        QueryWrapper<NotificationPO> filter = visible(tenantId, query.recipientMemberId());
        if (query.unreadOnly()) filter.eq("status", NotificationStatus.UNREAD.name());
        filter.orderByDesc("created_time").orderByDesc("id");
        IPage<NotificationPO> page = mapper.selectPage(new Page<>(query.page(), query.size()), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                page.getRecords().stream().map(this::domain).toList());
    }

    @Override
    public Optional<NotificationReadResult> markReadIfVisible(
            TenantId tenantId, NotificationId notificationId, MemberId recipientMemberId) {
        if (tenantId == null || notificationId == null || recipientMemberId == null) return Optional.empty();
        NotificationPO visible = mapper.selectOne(visible(tenantId, recipientMemberId)
                .eq("id", notificationId.getValue()).last("FOR UPDATE"));
        if (visible == null) return Optional.empty();
        boolean unread = visible.getStatus().equals(NotificationStatus.UNREAD.name());
        int changed = unread
                ? mapper.markReadIfUnread(tenantId.getValue(), notificationId.getValue(), recipientMemberId.getValue())
                : 0;
        return Optional.ofNullable(mapper.selectOne(visible(tenantId, recipientMemberId)
                .eq("id", notificationId.getValue()).last("FOR UPDATE")))
                .map(row -> new NotificationReadResult(domain(row), changed == 1));
    }

    @Override
    public Optional<Notification> findByIdForDeliveryAdmission(TenantId tenantId, NotificationId notificationId) {
        if (tenantId == null || notificationId == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<NotificationPO>()
                .eq("tenant_id", tenantId.getValue()).eq("id", notificationId.getValue()).last("FOR UPDATE")))
                .map(this::domain);
    }

    private QueryWrapper<NotificationPO> visible(TenantId tenantId, MemberId recipient) {
        return new QueryWrapper<NotificationPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("recipient_actor_id", recipient.getValue())
                .ne("status", NotificationStatus.WITHDRAWN.name())
                .apply("EXISTS (SELECT 1 FROM organization_membership recipient_membership"
                                + " WHERE recipient_membership.tenant_id = crm_notification.tenant_id"
                                + " AND recipient_membership.actor_id = crm_notification.recipient_actor_id"
                                + " AND recipient_membership.status = 'ACTIVE')")
                .apply("EXISTS (SELECT 1 FROM crm_work_task visible_task"
                                + " WHERE visible_task.tenant_id = crm_notification.tenant_id"
                                + " AND visible_task.id = crm_notification.work_task_id"
                                + " AND visible_task.status <> 'DELETED'"
                                + " AND (visible_task.created_by_actor_id = {0}"
                                + " OR visible_task.current_assignee_actor_id = {0}"
                                + " OR EXISTS (SELECT 1 FROM crm_work_task_follower visible_follower"
                                + " WHERE visible_follower.tenant_id = visible_task.tenant_id"
                                + " AND visible_follower.work_task_id = visible_task.id"
                                + " AND visible_follower.follower_actor_id = {0})))", recipient.getValue());
    }

    private NotificationPO po(Notification value) {
        NotificationPO row = new NotificationPO();
        row.setId(value.getId() == null ? null : value.getId().getValue());
        row.setTenantId(value.getTenantId().getValue());
        row.setRecipientActorId(value.getRecipientMemberId().getValue());
        row.setKind(value.getKind().name());
        row.setReminderOccurrenceId(value.getReminderOccurrenceId().getValue());
        row.setWorkTaskId(value.getTaskId().getValue());
        row.setTaskNotificationProjectionVersion(value.getTaskNotificationProjectionVersion());
        row.setTitle(value.getTitle()); row.setPriority(value.getPriority().name());
        row.setDueTime(value.getDueTime()); row.setStatus(value.getStatus().name());
        row.setReadTime(value.getReadTime()); row.setCreatedTime(value.getCreatedTime());
        row.setUpdatedTime(value.getUpdatedTime());
        return row;
    }

    private Notification domain(NotificationPO row) {
        return Notification.reconstitute(new Notification.Snapshot(new NotificationId(row.getId()),
                new TenantId(row.getTenantId()), new MemberId(row.getRecipientActorId()),
                NotificationKind.valueOf(row.getKind()), new WorkTaskReminderOccurrenceId(row.getReminderOccurrenceId()),
                new WorkTaskId(row.getWorkTaskId()), row.getTaskNotificationProjectionVersion(), row.getTitle(),
                WorkTaskPriority.valueOf(row.getPriority()), row.getDueTime(), NotificationStatus.valueOf(row.getStatus()),
                row.getReadTime(), row.getCreatedTime(), row.getUpdatedTime()));
    }
}
