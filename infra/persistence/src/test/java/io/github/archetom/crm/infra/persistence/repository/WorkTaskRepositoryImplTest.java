package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.archetom.crm.domain.model.WorkTaskWorklistRecord;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.infra.persistence.converter.WorkTaskPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskFollowerMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorkTaskMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskPO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkTaskRepositoryImplTest {

    @Test
    void worklistQuerySelectsOnlyProjectionColumnsAndRequiresActiveAssignee() {
        WorkTaskMapper mapper = mock(WorkTaskMapper.class);
        AtomicReference<QueryWrapper<WorkTaskPO>> captured = new AtomicReference<>();
        LocalDateTime dueTime = LocalDateTime.of(2026, 7, 18, 9, 0);
        WorkTaskPO row = new WorkTaskPO();
        row.setId(31L);
        row.setWorklistProjectionVersion(4L);
        row.setDueTime(dueTime);
        row.setTitle("Projected task");
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return List.of(row);
        });
        WorkTaskRepositoryImpl repository = new WorkTaskRepositoryImpl(mapper,
                mock(WorkTaskFollowerMapper.class), mock(WorkTaskPOConverter.class));

        List<WorkTaskWorklistRecord> result = repository.findDueForAssignee(
                new TenantId(9L), new MemberId(7L), dueTime.toLocalDate().atStartOfDay(),
                dueTime.toLocalDate().plusDays(1).atStartOfDay(), null, null, 101);

        assertEquals(List.of(new WorkTaskWorklistRecord(
                new WorkTaskId(31L), 4L, dueTime, "Projected task")), result);
        Set<String> selected = Arrays.stream(captured.get().getSqlSelect().split(","))
                .map(String::trim).collect(Collectors.toSet());
        assertEquals(Set.of("id", "worklist_projection_version", "due_time", "title"), selected);
        assertTrue(captured.get().getCustomSqlSegment()
                .contains("current_assignee.status = 'ACTIVE'"));
    }
}
