package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.archetom.crm.domain.model.NotificationStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.NotificationId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NotificationMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationPO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRepositoryImplTest {

    @Test
    void markReadLocksTheVisibleNotificationExclusivelyBeforeTheConditionalUpdate() {
        NotificationMapper mapper = org.mockito.Mockito.mock(NotificationMapper.class);
        NotificationPO unread = row(NotificationStatus.UNREAD.name(), null);
        NotificationPO read = row(NotificationStatus.READ.name(), LocalDateTime.of(2026, 7, 22, 10, 1));
        when(mapper.selectOne(any())).thenReturn(unread, read);
        when(mapper.markReadIfUnread(1L, 9L, 2L)).thenReturn(1);

        new NotificationRepositoryImpl(mapper).markReadIfVisible(
                new TenantId(1L), new NotificationId(9L), new MemberId(2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<NotificationPO>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper, times(2)).selectOne(query.capture());
        assertTrue(query.getAllValues().stream()
                .allMatch(value -> value.getSqlSegment().contains("FOR UPDATE")));
        verify(mapper).markReadIfUnread(eq(1L), eq(9L), eq(2L));
    }

    private static NotificationPO row(String status, LocalDateTime readTime) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        NotificationPO value = new NotificationPO();
        value.setId(9L); value.setTenantId(1L); value.setRecipientActorId(2L);
        value.setKind("WORK_TASK_REMINDER"); value.setReminderOccurrenceId(3L); value.setWorkTaskId(4L);
        value.setTaskNotificationProjectionVersion(0L); value.setTitle("Task"); value.setPriority("NORMAL");
        value.setDueTime(now); value.setStatus(status); value.setReadTime(readTime);
        value.setCreatedTime(now); value.setUpdatedTime(readTime == null ? now : readTime);
        return value;
    }
}
