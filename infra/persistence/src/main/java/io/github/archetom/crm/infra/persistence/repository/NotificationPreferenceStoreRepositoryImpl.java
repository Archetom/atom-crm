package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.application.port.out.notification.NotificationPreference;
import io.github.archetom.crm.application.port.out.notification.NotificationPreferenceStore;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NotificationPreferenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/** Missing rows deliberately mean external EMAIL is OFF. */
@Repository
@RequiredArgsConstructor
public class NotificationPreferenceStoreRepositoryImpl implements NotificationPreferenceStore {
    private final NotificationPreferenceMapper mapper;

    @Override
    public NotificationPreference findOrDefault(TenantId tenantId, MemberId memberId) {
        require(tenantId, memberId);
        NotificationPreferencePO row = row(tenantId, memberId);
        if (row == null) return new NotificationPreference(tenantId, memberId, false, false, null);
        return value(row);
    }

    @Override
    public NotificationPreference update(TenantId tenantId, MemberId memberId, boolean emailEnabled,
                                         Boolean oaAnnouncementEmailEnabled, LocalDateTime updatedTime) {
        require(tenantId, memberId);
        int changed = oaAnnouncementEmailEnabled == null
                ? mapper.upsertEmailPreservingOa(tenantId.getValue(), memberId.getValue(), emailEnabled, updatedTime)
                : mapper.upsert(tenantId.getValue(), memberId.getValue(), emailEnabled,
                oaAnnouncementEmailEnabled, updatedTime);
        if (changed != 1) {
            throw new IllegalStateException("notification preference upsert failed");
        }
        NotificationPreferencePO row = row(tenantId, memberId);
        if (row == null) throw new IllegalStateException("notification preference disappeared");
        return value(row);
    }

    @Override
    public boolean emailEnabled(TenantId tenantId, MemberId memberId) {
        NotificationPreferencePO row = row(tenantId, memberId);
        return row != null && Boolean.TRUE.equals(row.getEmailEnabled());
    }

    @Override
    public boolean oaAnnouncementEmailEnabled(TenantId tenantId, MemberId memberId) {
        NotificationPreferencePO row = row(tenantId, memberId);
        return row != null && Boolean.TRUE.equals(row.getOaAnnouncementEmailEnabled());
    }

    @Override
    public Set<MemberId> oaAnnouncementEmailEnabled(TenantId tenantId, Collection<MemberId> memberIds) {
        if (tenantId == null || memberIds == null || memberIds.isEmpty()) return Set.of();
        java.util.List<Long> ids = memberIds.stream().filter(java.util.Objects::nonNull)
                .map(MemberId::getValue).distinct().toList();
        if (ids.isEmpty()) return Set.of();
        return mapper.selectList(new LambdaQueryWrapper<NotificationPreferencePO>()
                        .eq(NotificationPreferencePO::getTenantId, tenantId.getValue())
                        .eq(NotificationPreferencePO::getOaAnnouncementEmailEnabled, true)
                        .in(NotificationPreferencePO::getRecipientActorId, ids))
                .stream().map(value -> new MemberId(value.getRecipientActorId()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private NotificationPreferencePO row(TenantId tenantId, MemberId memberId) {
        require(tenantId, memberId);
        return mapper.selectOne(new LambdaQueryWrapper<NotificationPreferencePO>()
                .eq(NotificationPreferencePO::getTenantId, tenantId.getValue())
                .eq(NotificationPreferencePO::getRecipientActorId, memberId.getValue()));
    }

    private static NotificationPreference value(NotificationPreferencePO row) {
        return new NotificationPreference(new TenantId(row.getTenantId()), new MemberId(row.getRecipientActorId()),
                Boolean.TRUE.equals(row.getEmailEnabled()),
                Boolean.TRUE.equals(row.getOaAnnouncementEmailEnabled()), row.getUpdatedTime());
    }

    private static void require(TenantId tenantId, MemberId memberId) {
        if (tenantId == null || memberId == null) throw new IllegalArgumentException("notification preference scope is invalid");
    }
}
