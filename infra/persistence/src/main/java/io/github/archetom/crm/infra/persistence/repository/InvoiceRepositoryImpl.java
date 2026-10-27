package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.InvoiceFieldValue;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.InvoiceAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.InvoiceQuery;
import io.github.archetom.crm.domain.model.InvoiceSortDirection;
import io.github.archetom.crm.domain.model.InvoiceCustomerSummary;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.InvoicePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.InvoiceFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.InvoiceMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.InvoiceUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoicePO;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoiceUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import io.github.archetom.crm.infra.persistence.support.QueryFilterConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class InvoiceRepositoryImpl implements InvoiceRepository {

    private static final Map<FieldCode, String> BUILTIN_COLUMNS = Map.ofEntries(
            Map.entry(new FieldCode(InvoiceFieldCatalog.APPLICATION_NUMBER), "application_number"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.CUSTOMER), "customer_id"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.CONTRACT), "contract_id"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.AMOUNT), "amount"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.REQUESTED_DATE), "requested_date"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.INVOICE_TYPE), "invoice_type"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.TITLE_TYPE), "title_type"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.TITLE), "title"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.TAX_NUMBER), "tax_number"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.BANK_NAME), "bank_name"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.BANK_ACCOUNT), "bank_account"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.REGISTERED_ADDRESS), "registered_address"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.REGISTERED_PHONE), "registered_phone"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.CONTACT_NAME), "contact_name"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.CONTACT_PHONE), "contact_phone"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.SHIPPING_ADDRESS), "shipping_address"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.REMARK), "remark"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.OWNER_ACTOR), "owner_actor_id"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.STATUS), "status"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.INVOICE_NUMBER), "invoice_number"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.ISSUED_DATE), "issued_date"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.LOGISTICS_NUMBER), "logistics_number"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.CREATED_TIME), "created_time"),
            Map.entry(new FieldCode(InvoiceFieldCatalog.UPDATED_TIME), "updated_time"));

    private final InvoiceMapper invoiceMapper;
    private final InvoiceFieldValueMapper fieldValueMapper;
    private final InvoiceUniqueFieldValueMapper uniqueValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final InvoicePOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;

    @Override
    public Invoice save(
            TenantId tenantId, Invoice invoice,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, invoice);
        InvoicePO po = converter.toPO(invoice);
        Map<FieldCode, FieldDefinition> fields = definitions(fieldDefinitions);
        try {
            if (po.getId() == null) {
                if (invoiceMapper.insert(po) != 1) {
                    throw new IllegalStateException("Invoice insert did not affect one row");
                }
            } else {
                LambdaUpdateWrapper<InvoicePO> update = new LambdaUpdateWrapper<InvoicePO>()
                        .eq(InvoicePO::getTenantId, tenantId.getValue())
                        .eq(InvoicePO::getId, po.getId());
                if (invoiceMapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }
            InvoiceId id = new InvoiceId(po.getId());
            synchronize(tenantId, id, invoice, fields);
            invoice.onPersisted(id, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return invoice;
        } catch (DuplicateKeyException exception) {
            throw new InvoiceAlreadyExistsException();
        }
    }

    @Override
    public Optional<Invoice> findById(TenantId tenantId, InvoiceId invoiceId) {
        requireTenant(tenantId);
        if (invoiceId == null) {
            return Optional.empty();
        }
        InvoicePO po = invoiceMapper.selectOne(new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getTenantId, tenantId.getValue())
                .eq(InvoicePO::getId, invoiceId.getValue())
                .ne(InvoicePO::getStatus, InvoiceStatus.DELETED.name()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Invoice> findByIdForAttachmentMutation(TenantId tenantId, InvoiceId invoiceId) {
        requireTenant(tenantId);
        if (invoiceId == null) return Optional.empty();
        InvoicePO po = invoiceMapper.selectOne(new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getTenantId, tenantId.getValue())
                .eq(InvoicePO::getId, invoiceId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Invoice> findVisibleById(
            TenantId tenantId, InvoiceId invoiceId, InvoiceVisibility visibility) {
        requireTenant(tenantId);
        if (invoiceId == null) {
            return Optional.empty();
        }
        InvoicePO po = invoiceMapper.selectOne(visible(tenantId, visibility)
                .eq("id", invoiceId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Invoice> findVisibleByIdForUpdate(
            TenantId tenantId, InvoiceId invoiceId, InvoiceVisibility visibility) {
        requireTenant(tenantId);
        if (invoiceId == null) {
            return Optional.empty();
        }
        InvoicePO po = invoiceMapper.selectOne(visible(tenantId, visibility)
                .eq("id", invoiceId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<InvoiceId> ids,
            InvoiceVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("invoice visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(InvoiceId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return invoiceMapper.selectList(visible(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(InvoicePO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Invoice> findVisibleByIds(
            TenantId tenantId, Collection<InvoiceId> invoiceIds,
            InvoiceVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (invoiceIds == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("invoice visibility batch is invalid");
        }
        List<Long> ids = invoiceIds.stream().filter(java.util.Objects::nonNull)
                .map(InvoiceId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return hydrated(tenantId, invoiceMapper.selectList(visible(tenantId, visibility)
                .in("id", ids).orderByAsc("id").last("LIMIT " + limit)));
    }

    @Override
    public PageResult<Invoice> findInvoices(TenantId tenantId, InvoiceQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("invoice query is required");
        }
        QueryWrapper<InvoicePO> wrapper = visible(tenantId, query.visibility());
        applyKeyword(wrapper, query);
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.subordinateOwnerMemberIds() != null) {
            List<Long> ownerIds = query.subordinateOwnerMemberIds().stream()
                    .map(value -> value.getValue()).toList();
            if (ownerIds.isEmpty()) wrapper.apply("1 = 0");
            else wrapper.in("owner_actor_id", ownerIds);
        }
        if (query.customerId() != null) {
            wrapper.eq("customer_id", query.customerId().getValue());
        }
        if (query.contractId() != null) {
            wrapper.eq("contract_id", query.contractId().getValue());
        }
        if (query.status() != null) {
            wrapper.eq("status", query.status().name());
        }
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        boolean ascending = query.sort().direction() == InvoiceSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<InvoicePO> page = invoiceMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0, page.getTotal()), hydrated(tenantId, page.getRecords()));
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.InvoiceSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("invoice custom sort is invalid");
        }
        String scalar = """
                (SELECT invoice_field_sort.scalar_value
                FROM crm_invoice_field_value invoice_field_sort
                WHERE invoice_field_sort.tenant_id = crm_invoice.tenant_id
                  AND invoice_field_sort.invoice_id = crm_invoice.id
                  AND invoice_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    @Override
    public InvoiceCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, InvoiceVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        Map<String, Object> result = invoiceMapper.selectMaps(visible(tenantId, visibility)
                        .eq("customer_id", customerId.getValue())
                        .eq("status", InvoiceStatus.PENDING.name())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new InvoiceCustomerSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    private void applyKeyword(QueryWrapper<InvoicePO> wrapper, InvoiceQuery query) {
        if (query.keyword() == null) {
            return;
        }
        if (query.keywordCandidates().isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        boolean applicationNumber = query.keywordCandidates().containsKey(
                new FieldCode(InvoiceFieldCatalog.APPLICATION_NUMBER));
        boolean title = query.keywordCandidates().containsKey(
                new FieldCode(InvoiceFieldCatalog.TITLE));
        boolean invoiceNumber = query.keywordCandidates().containsKey(
                new FieldCode(InvoiceFieldCatalog.INVOICE_NUMBER));
        wrapper.and(nested -> {
            boolean previous = false;
            if (applicationNumber) {
                nested.apply("application_number LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(query.keyword()));
                previous = true;
            }
            if (title) {
                if (previous) {
                    nested.or();
                }
                nested.apply("title LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(query.keyword()));
                previous = true;
            }
            if (invoiceNumber) {
                if (previous) {
                    nested.or();
                }
                nested.apply("invoice_number LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(query.keyword()));
            }
        });
    }

    private void applyCondition(QueryWrapper<InvoicePO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("invoice filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            QueryFilterConditions.applyBuiltin(query, column, condition);
            return;
        }
        QueryFilterConditions.applyCustom(query, condition, "crm_invoice",
                "crm_invoice_field_value", "invoice_id");
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    @Override
    public BigDecimal sumCommittedAmount(
            TenantId tenantId, ContractId contractId, InvoiceId excludingInvoiceId) {
        requireTenant(tenantId);
        if (contractId == null) {
            throw new IllegalArgumentException("contract ID is required");
        }
        QueryWrapper<InvoicePO> query = new QueryWrapper<InvoicePO>()
                .select("COALESCE(SUM(amount), 0) AS total_amount")
                .eq("tenant_id", tenantId.getValue())
                .eq("contract_id", contractId.getValue())
                .ne("status", InvoiceStatus.DELETED.name());
        if (excludingInvoiceId != null) {
            query.ne("id", excludingInvoiceId.getValue());
        }
        Object value = invoiceMapper.selectMaps(query).stream().findFirst()
                .map(map -> map.get("total_amount")).orElse(BigDecimal.ZERO);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    @Override
    public boolean existsByContract(TenantId tenantId, ContractId contractId) {
        requireTenant(tenantId);
        if (contractId == null) {
            throw new IllegalArgumentException("contract ID is required");
        }
        return invoiceMapper.selectCount(new LambdaQueryWrapper<InvoicePO>()
                .eq(InvoicePO::getTenantId, tenantId.getValue())
                .eq(InvoicePO::getContractId, contractId.getValue())) > 0;
    }

    private void synchronize(
            TenantId tenantId, InvoiceId id, Invoice invoice,
            Map<FieldCode, FieldDefinition> fields) {
        fieldValueMapper.delete(new LambdaQueryWrapper<InvoiceFieldValuePO>()
                .eq(InvoiceFieldValuePO::getTenantId, tenantId.getValue())
                .eq(InvoiceFieldValuePO::getInvoiceId, id.getValue()));
        uniqueValueMapper.delete(new LambdaQueryWrapper<InvoiceUniqueFieldValuePO>()
                .eq(InvoiceUniqueFieldValuePO::getTenantId, tenantId.getValue())
                .eq(InvoiceUniqueFieldValuePO::getInvoiceId, id.getValue()));
        for (InvoiceFieldValue value : invoice.getCustomFieldValues()) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("invoice field definition is missing");
            }
            fieldValueMapper.insert(converter.toPO(tenantId, id, value, field));
            if (!invoice.isDeleted() && field.isUnique()) {
                uniqueValueMapper.insert(unique(tenantId, id, field, value.values().getFirst()));
            }
        }
    }

    private List<Invoice> hydrated(TenantId tenantId, List<InvoicePO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roots.stream().map(InvoicePO::getId).distinct().toList();
        List<InvoiceFieldValuePO> values = fieldValueMapper.selectList(
                new LambdaQueryWrapper<InvoiceFieldValuePO>()
                        .eq(InvoiceFieldValuePO::getTenantId, tenantId.getValue())
                        .in(InvoiceFieldValuePO::getInvoiceId, ids)
                        .orderByAsc(InvoiceFieldValuePO::getFieldId));
        Set<Long> fieldIds = values.stream().map(InvoiceFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fields = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.INVOICE.name())
                .in(CustomFieldDefinitionPO::getId, fieldIds)).stream()
                .map(fieldDefinitionConverter::toDomain)
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
        Map<Long, List<InvoiceFieldValuePO>> valueMap = values.stream()
                .collect(Collectors.groupingBy(InvoiceFieldValuePO::getInvoiceId));
        return roots.stream().map(po -> converter.toDomain(po,
                valueMap.getOrDefault(po.getId(), List.of()), fields)).toList();
    }

    private QueryWrapper<InvoicePO> visible(
            TenantId tenantId, InvoiceVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("invoice visibility is required");
        }
        QueryWrapper<InvoicePO> query = new QueryWrapper<InvoicePO>()
                .eq("tenant_id", tenantId.getValue())
                .ne("status", InvoiceStatus.DELETED.name());
        if (visibility.dataScope() == DataScope.TENANT) {
            return query;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            return query.eq("owner_actor_id", visibility.callerMemberId().getValue());
        }
        List<Long> departments = visibility.ownerDepartmentIds().stream()
                .map(DepartmentId::getValue).sorted().toList();
        return query.apply("""
                EXISTS (SELECT 1 FROM organization_membership owner_membership
                 WHERE owner_membership.tenant_id = crm_invoice.tenant_id
                   AND owner_membership.actor_id = crm_invoice.owner_actor_id
                   AND owner_membership.status = 'ACTIVE'
                   AND owner_membership.department_id IN (%s))
                """.formatted(placeholders(departments.size())), departments.toArray());
    }

    private InvoiceUniqueFieldValuePO unique(
            TenantId tenantId, InvoiceId id, FieldDefinition field, String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
        InvoiceUniqueFieldValuePO po = new InvoiceUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setInvoiceId(id.getValue());
        po.setObjectType(BusinessObjectType.INVOICE.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedValue(normalized);
        po.setNormalizedHash(hash(normalized));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private Map<FieldCode, FieldDefinition> definitions(Collection<FieldDefinition> values) {
        if (values == null) {
            return Map.of();
        }
        return values.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private void requireTenantMatch(TenantId tenantId, Invoice invoice) {
        requireTenant(tenantId);
        if (invoice == null || invoice.getTenantId() == null
                || !tenantId.sameValueAs(invoice.getTenantId())) {
            throw new IllegalArgumentException("invoice tenant does not match");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant is required");
        }
    }

    private String placeholders(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(index -> "{" + index + "}").collect(Collectors.joining(","));
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
