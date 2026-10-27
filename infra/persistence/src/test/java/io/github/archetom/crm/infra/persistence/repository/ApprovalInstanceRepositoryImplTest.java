package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.model.ApprovalActionType;
import io.github.archetom.crm.domain.model.ApprovalInstanceQuery;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.ApprovalQueryBucket;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalTaskStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ResolvedApprovalStep;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ApprovalInstancePOConverter;
import io.github.archetom.crm.infra.persistence.converter.ApprovalTaskPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalInstanceMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalManagerMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalTaskMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalWatcherMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceReferencePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalSubjectStatusPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalInstanceRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);

    private MapperCalls instanceCalls;
    private MapperCalls taskCalls;
    private MapperCalls managerCalls;
    private MapperCalls watcherCalls;
    private ApprovalInstanceRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        instanceCalls = new MapperCalls();
        taskCalls = new MapperCalls();
        managerCalls = new MapperCalls();
        watcherCalls = new MapperCalls();
        repository = new ApprovalInstanceRepositoryImpl(
                proxy(ApprovalInstanceMapper.class, instanceCalls),
                proxy(ApprovalTaskMapper.class, taskCalls),
                proxy(ApprovalManagerMapper.class, managerCalls),
                proxy(ApprovalWatcherMapper.class, watcherCalls),
                new ApprovalInstancePOConverter(new ObjectMapper()),
                new ApprovalTaskPOConverter());
    }

    @Test
    void visibilityScanDoesNotHydrateRuntimeCollections() {
        ApprovalInstanceReferencePO reference = new ApprovalInstanceReferencePO();
        reference.setId(31L);
        reference.setObjectType(BusinessObjectType.CUSTOMER.name());
        reference.setSubjectId(41L);
        Page<ApprovalInstanceReferencePO> page = new Page<>(1, 200, 1);
        page.setRecords(List.of(reference));
        instanceCalls.answer("selectApprovalScanPage", ignored -> page);

        var result = repository.scanPage(TENANT, new ApprovalInstanceQuery(
                MEMBER, ApprovalQueryBucket.PENDING, null, null, null, null, 1, 200));

        assertEquals(1, result.total());
        assertEquals(new ApprovalInstanceId(31L), result.items().getFirst().instanceId());
        assertEquals(0, taskCalls.total());
        assertEquals(0, managerCalls.total());
        assertEquals(0, watcherCalls.total());
    }

    @Test
    void summaryLookupDoesNotHydrateRuntimeCollections() {
        ApprovalInstancePO po = new ApprovalInstancePO();
        po.setId(31L);
        po.setTenantId(TENANT.getValue());
        po.setDefinitionId(2L);
        po.setDefinitionVersion(3L);
        po.setObjectType(BusinessObjectType.CUSTOMER.name());
        po.setSubjectId(41L);
        po.setIdempotencyKey("summary-key");
        po.setTitle("Summary");
        po.setApplicantActorId(MEMBER.getValue());
        po.setRepeatMode(ApprovalRepeatMode.ONCE.name());
        po.setStatus("PENDING");
        po.setCurrentStepOrder(0);
        po.setFactsJson("{}");
        po.setSubmittedTime(LocalDateTime.of(2026, 7, 17, 12, 0));
        po.setVersion(4L);
        po.setUpdatedTime(LocalDateTime.of(2026, 7, 17, 12, 1));
        instanceCalls.answer("selectList", ignored -> List.of(po));

        var summaries = repository.findSummariesByIds(
                TENANT, List.of(new ApprovalInstanceId(31L)));

        assertEquals(1, summaries.size());
        assertEquals(new ApprovalInstanceId(31L), summaries.getFirst().id());
        assertEquals(0, taskCalls.total());
        assertEquals(0, managerCalls.total());
        assertEquals(0, watcherCalls.total());
    }

    @Test
    void latestSubjectStatusProjectionUsesTenantScopedDeduplicatedSubjectIds() {
        ApprovalSubjectStatusPO approved = new ApprovalSubjectStatusPO();
        approved.setSubjectId(41L);
        approved.setStatus(ApprovalInstanceStatus.APPROVED.name());
        instanceCalls.answer("selectLatestSubjectStatuses", ignored -> List.of(approved));

        var statuses = repository.findLatestSubjectStatuses(
                TENANT, BusinessObjectType.RECEIVABLE, List.of(41L, 41L));

        assertEquals(1, statuses.size());
        assertEquals(41L, statuses.getFirst().subjectId());
        assertEquals(ApprovalInstanceStatus.APPROVED, statuses.getFirst().status());
        Object[] arguments = instanceCalls.arguments("selectLatestSubjectStatuses").getFirst();
        assertEquals(TENANT.getValue(), arguments[0]);
        assertEquals(BusinessObjectType.RECEIVABLE.name(), arguments[1]);
        assertEquals(List.of(41L), List.copyOf((java.util.Collection<Long>) arguments[2]));
        assertEquals(0, taskCalls.total());
        assertEquals(0, managerCalls.total());
        assertEquals(0, watcherCalls.total());
    }

    @Test
    void saveUpdatesOnlyTasksChangedByTheAction() {
        MemberId second = new MemberId(8L);
        MemberId third = new MemberId(9L);
        ApprovalInstance instance = ApprovalInstance.submit(
                TENANT, new ApprovalDefinitionId(2L), 3L,
                BusinessObjectType.CUSTOMER, 20L, "repository-diff-key",
                "Repository diff", new MemberId(10L), ApprovalRepeatMode.EVERY_STEP,
                Map.of(), List.of(), List.of(), List.of(new ResolvedApprovalStep(
                        "review", "Review", 0, ApprovalStepMode.ALL,
                        List.of(MEMBER, second, third))));
        LocalDateTime persistedTime = LocalDateTime.of(2026, 7, 17, 12, 0);
        instance.onPersisted(new ApprovalInstanceId(31L), 0L,
                persistedTime, persistedTime);
        instance.getTasks().forEach(task -> task.onPersisted(
                0L, persistedTime, persistedTime));
        instance.act(MEMBER, ApprovalActionType.APPROVE, null, null);
        instanceCalls.answer("update", ignored -> 1);
        taskCalls.answer("update", ignored -> 1);

        repository.save(TENANT, instance);

        assertEquals(1, taskCalls.count("update"));
        ApprovalTaskPO updatedTask = (ApprovalTaskPO) taskCalls.arguments("update")
                .getFirst()[0];
        assertEquals(MEMBER.getValue(), updatedTask.getAssignedActorId());
        assertEquals(0, taskCalls.count("insert"));
        assertFalse(instance.getTasks().stream()
                .anyMatch(task -> task.hasPendingPersistenceChanges()));
    }

    @Test
    void lockingRootHydrationUsesCurrentLockingReadForApprovalTasks() {
        ApprovalInstancePO root = pendingRoot();
        instanceCalls.answer("selectOne", ignored -> root);
        taskCalls.answer("selectList", ignored -> List.of(pendingTask(root.getId())));
        managerCalls.answer("selectList", ignored -> List.of());
        watcherCalls.answer("selectList", ignored -> List.of());

        assertTrue(repository.findByIdForUpdate(TENANT, new ApprovalInstanceId(root.getId())).isPresent());

        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<ApprovalTaskPO> taskQuery =
                (LambdaQueryWrapper<ApprovalTaskPO>) taskCalls.arguments("selectList").getFirst()[0];
        assertTrue(lastSql(taskQuery).contains("FOR UPDATE"));
    }

    private static String lastSql(LambdaQueryWrapper<?> query) {
        try {
            Field field = AbstractWrapper.class.getDeclaredField("lastSql");
            field.setAccessible(true);
            return ((SharedString) field.get(query)).getStringValue();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not inspect query lock clause", exception);
        }
    }

    private static ApprovalInstancePO pendingRoot() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 24, 10, 0);
        ApprovalInstancePO po = new ApprovalInstancePO();
        po.setId(31L);
        po.setTenantId(TENANT.getValue());
        po.setDefinitionId(2L);
        po.setDefinitionVersion(3L);
        po.setObjectType(BusinessObjectType.OA_REQUEST.name());
        po.setSubjectId(41L);
        po.setIdempotencyKey("lock-hydration-key");
        po.setTitle("Lock hydration");
        po.setApplicantActorId(MEMBER.getValue());
        po.setRepeatMode(ApprovalRepeatMode.ONCE.name());
        po.setStatus("PENDING");
        po.setCurrentStepOrder(0);
        po.setFactsJson("{}");
        po.setSubmittedTime(now);
        po.setVersion(4L);
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    private static ApprovalTaskPO pendingTask(long instanceId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 24, 10, 0);
        ApprovalTaskPO po = new ApprovalTaskPO();
        po.setTenantId(TENANT.getValue());
        po.setTaskKey("00000000-0000-0000-0000-000000000031");
        po.setInstanceId(instanceId);
        po.setStepCode("review");
        po.setStepName("Review");
        po.setStepOrder(0);
        po.setMode(ApprovalStepMode.ALL.name());
        po.setActorOrder(0);
        po.setAssignedActorId(MEMBER.getValue());
        po.setStatus(ApprovalTaskStatus.PENDING.name());
        po.setVersion(0L);
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        return po;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static final class MapperCalls implements InvocationHandler {
        private final Map<String, Function<Object[], Object>> answers = new HashMap<>();
        private final Map<String, List<Object[]>> calls = new HashMap<>();

        void answer(String method, Function<Object[], Object> answer) {
            answers.put(method, answer);
        }

        int count(String method) {
            return arguments(method).size();
        }

        int total() {
            return calls.values().stream().mapToInt(List::size).sum();
        }

        List<Object[]> arguments(String method) {
            return calls.getOrDefault(method, List.of());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "MapperCallsProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                };
            }
            Object[] safeArguments = arguments == null ? new Object[0] : arguments;
            calls.computeIfAbsent(method.getName(), ignored -> new ArrayList<>())
                    .add(safeArguments);
            Function<Object[], Object> answer = answers.get(method.getName());
            if (answer != null) {
                return answer.apply(safeArguments);
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
