package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.notification.NotificationPreference;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NotificationPreferenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationPreferencePO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceStoreRepositoryImplTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 12, 0);

    @Test
    void legacyUpdatePreservesAConcurrentOaOptOut() {
        NotificationPreferenceMapper mapper = mock(NotificationPreferenceMapper.class);
        NotificationPreferencePO row = row(false);
        when(mapper.upsertEmailPreservingOa(1L, 2L, false, NOW)).thenReturn(1);
        when(mapper.selectOne(any())).thenReturn(row);

        NotificationPreference result = new NotificationPreferenceStoreRepositoryImpl(mapper)
                .update(TENANT, MEMBER, false, null, NOW);

        assertFalse(result.oaAnnouncementEmailEnabled());
        verify(mapper).upsertEmailPreservingOa(1L, 2L, false, NOW);
        verify(mapper, never()).upsert(anyLong(), anyLong(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void explicitOaChoiceUpdatesBothPreferencesAtomically() {
        NotificationPreferenceMapper mapper = mock(NotificationPreferenceMapper.class);
        NotificationPreferencePO row = row(true);
        when(mapper.upsert(1L, 2L, true, true, NOW)).thenReturn(1);
        when(mapper.selectOne(any())).thenReturn(row);

        NotificationPreference result = new NotificationPreferenceStoreRepositoryImpl(mapper)
                .update(TENANT, MEMBER, true, true, NOW);

        assertTrue(result.oaAnnouncementEmailEnabled());
        verify(mapper).upsert(1L, 2L, true, true, NOW);
        verify(mapper, never()).upsertEmailPreservingOa(anyLong(), anyLong(), anyBoolean(), any());
    }

    private static NotificationPreferencePO row(boolean oaEnabled) {
        NotificationPreferencePO row = new NotificationPreferencePO();
        row.setTenantId(TENANT.getValue());
        row.setRecipientActorId(MEMBER.getValue());
        row.setEmailEnabled(true);
        row.setOaAnnouncementEmailEnabled(oaEnabled);
        row.setUpdatedTime(NOW);
        return row;
    }
}
