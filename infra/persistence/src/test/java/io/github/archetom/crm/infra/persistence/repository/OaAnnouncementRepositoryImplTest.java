package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementQuery;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.OaAnnouncementPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementAudienceDepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementAudienceMemberMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementReadReceiptMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementRecipientMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceDepartmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceMemberPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementReadReceiptPO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaAnnouncementRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);

    private MapperCalls announcementCalls;
    private MapperCalls departmentCalls;
    private MapperCalls memberCalls;
    private MapperCalls receiptCalls;
    private OaAnnouncementRepositoryImpl repository;

    @BeforeAll
    static void initializeMapperMetadata() {
        // The call-recording proxies below bypass MyBatis startup. Register the actual mappers
        // so lambda wrappers have the same annotation-derived table/column metadata as production.
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(OaAnnouncementMapper.class);
        configuration.addMapper(OaAnnouncementAudienceDepartmentMapper.class);
        configuration.addMapper(OaAnnouncementAudienceMemberMapper.class);
        configuration.addMapper(OaAnnouncementRecipientMapper.class);
        configuration.addMapper(OaAnnouncementReadReceiptMapper.class);
    }

    @BeforeEach
    void setUp() {
        announcementCalls = new MapperCalls();
        departmentCalls = new MapperCalls();
        memberCalls = new MapperCalls();
        receiptCalls = new MapperCalls();
        repository = new OaAnnouncementRepositoryImpl(
                proxy(OaAnnouncementMapper.class, announcementCalls),
                proxy(OaAnnouncementAudienceDepartmentMapper.class, departmentCalls),
                proxy(OaAnnouncementAudienceMemberMapper.class, memberCalls),
                proxy(OaAnnouncementRecipientMapper.class, new MapperCalls()),
                proxy(OaAnnouncementReadReceiptMapper.class, receiptCalls),
                new OaAnnouncementPOConverter());
    }

    @Test
    void listInboxAndSharedReadLockNeverHydrateAudienceSelectors() {
        OaAnnouncementPO root = root();
        Page<OaAnnouncementPO> page = new Page<>(1, 200, 1);
        page.setRecords(List.of(root));
        announcementCalls.answer("selectPage", ignored -> page);
        announcementCalls.answer("selectOne", ignored -> root);
        receiptCalls.answer("selectList", ignored -> List.of());
        receiptCalls.answer("selectOne", ignored -> null);

        var management = repository.findPage(
                TENANT, new OaAnnouncementQuery(1, 200, null));
        var inbox = repository.findInboxPage(
                TENANT, new OaAnnouncementInboxQuery(1, 200, MEMBER));
        var locked = repository.findInboxItemForShare(
                TENANT, new OaAnnouncementId(root.getId()), MEMBER);

        assertEquals("Root-only", management.items().getFirst().title());
        assertEquals("Root-only", inbox.items().getFirst().title());
        assertTrue(locked.isPresent());
        assertEquals(0, departmentCalls.total());
        assertEquals(0, memberCalls.total());
    }

    @Test
    void duplicateReadReceiptReturnsPersistedFirstReadTime() {
        LocalDateTime firstReadTime = LocalDateTime.of(2026, 7, 19, 11, 0);
        OaAnnouncementReadReceiptPO persisted = new OaAnnouncementReadReceiptPO();
        persisted.setTenantId(TENANT.getValue());
        persisted.setAnnouncementId(31L);
        persisted.setActorId(MEMBER.getValue());
        persisted.setReadTime(firstReadTime);
        receiptCalls.answer("insertIfAbsent", ignored -> 0);
        receiptCalls.answer("selectOne", ignored -> persisted);

        LocalDateTime result = repository.saveReadReceiptIfAbsent(
                TENANT, new OaAnnouncementId(31L), MEMBER,
                firstReadTime.plusHours(1));

        assertEquals(firstReadTime, result);
        assertEquals(1, receiptCalls.count("insertIfAbsent"));
    }

    @Test
    void managedDetailStillHydratesCompleteAudience() {
        OaAnnouncementPO root = root();
        root.setTenantAudience(true);
        announcementCalls.answer("selectOne", ignored -> root);
        OaAnnouncementAudienceDepartmentPO department =
                new OaAnnouncementAudienceDepartmentPO();
        department.setTenantId(TENANT.getValue());
        department.setAnnouncementId(root.getId());
        department.setDepartmentId(11L);
        OaAnnouncementAudienceMemberPO member = new OaAnnouncementAudienceMemberPO();
        member.setTenantId(TENANT.getValue());
        member.setAnnouncementId(root.getId());
        member.setActorId(12L);
        departmentCalls.answer("selectList", ignored -> List.of(department));
        memberCalls.answer("selectList", ignored -> List.of(member));

        var detail = repository.findById(
                TENANT, new OaAnnouncementId(root.getId()));

        assertTrue(detail.isPresent());
        assertEquals(3, detail.orElseThrow().getAudienceSelectors().size());
        assertEquals(1, departmentCalls.count("selectList"));
        assertEquals(1, memberCalls.count("selectList"));
    }

    private OaAnnouncementPO root() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        OaAnnouncementPO root = new OaAnnouncementPO();
        root.setId(31L);
        root.setTenantId(TENANT.getValue());
        root.setCreatedByActorId(MEMBER.getValue());
        root.setTitle("Root-only");
        root.setBody("Plain body");
        root.setTenantAudience(false);
        root.setStatus("PUBLISHED");
        root.setPublishedTime(now);
        root.setVersion(2L);
        root.setCreatedTime(now.minusHours(1));
        root.setUpdatedTime(now);
        return root;
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
            return calls.getOrDefault(method, List.of()).size();
        }

        int total() {
            return calls.values().stream().mapToInt(List::size).sum();
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
