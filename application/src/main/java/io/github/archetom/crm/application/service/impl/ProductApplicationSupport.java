package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.vo.ProductCategoryVO;
import io.github.archetom.crm.application.vo.ProductEditableBaselineVO;
import io.github.archetom.crm.application.vo.ProductVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.entity.ProductFieldValue;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldValuePolicy;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ProductApplicationSupport {

    private static final String REDACTED = "[REDACTED]";
    private static final List<String> EDITABLE_BASELINE_FIELDS = List.of(
            ProductFieldCatalog.NAME,
            ProductFieldCatalog.CODE,
            ProductFieldCatalog.CATEGORY,
            ProductFieldCatalog.UNIT,
            ProductFieldCatalog.LIST_PRICE,
            ProductFieldCatalog.DESCRIPTION);

    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    List<FieldDefinition> fields(TenantId tenantId) {
        return fieldRepository.findAll(tenantId, BusinessObjectType.PRODUCT, true);
    }

    Map<FieldCode, FieldDefinition> fieldsByCode(List<FieldDefinition> fields) {
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, EffectiveFieldAccess> accesses(
            TenantId tenantId, MemberId memberId) {
        return fieldAccessResolver.resolve(
                tenantId, memberId, BusinessObjectType.PRODUCT);
    }

    void requireWrite(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Product field is not writable");
        }
    }

    void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Product field is not readable");
        }
    }

    ProductVO toVO(
            Product product, String categoryName,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return new ProductVO(product.getId().getValue(),
                number(product.getCategoryId() == null ? null
                                : product.getCategoryId().getValue(), accesses,
                        ProductFieldCatalog.CATEGORY),
                text(categoryName, accesses, ProductFieldCatalog.CATEGORY),
                number(product.getOwnerMemberId().getValue(), accesses,
                        ProductFieldCatalog.OWNER_ACTOR),
                text(product.getName(), accesses, ProductFieldCatalog.NAME),
                text(product.getCode(), accesses, ProductFieldCatalog.CODE),
                text(product.getUnit(), accesses, ProductFieldCatalog.UNIT),
                decimal(product.getListPrice(), accesses, ProductFieldCatalog.LIST_PRICE),
                text(product.getDescription(), accesses, ProductFieldCatalog.DESCRIPTION),
                text(product.getAvailability().name(), accesses,
                        ProductFieldCatalog.AVAILABILITY),
                text(product.getStatus().name(), accesses, ProductFieldCatalog.STATUS),
                customFields(product, accesses), product.getVersion(),
                visibleUnmasked(accesses, ProductFieldCatalog.CREATED_TIME)
                        ? product.getCreatedTime() : null,
                visibleUnmasked(accesses, ProductFieldCatalog.UPDATED_TIME)
                        ? product.getUpdatedTime() : null);
    }

    ProductEditableBaselineVO editableBaseline(
            Product product, Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (!canReturnEditableBaseline(accesses)) {
            return new ProductEditableBaselineVO(product.getId().getValue(),
                    product.getVersion(), false, null, null, null, null, null, null);
        }
        return new ProductEditableBaselineVO(product.getId().getValue(),
                product.getVersion(), true, product.getName(), product.getCode(),
                product.getCategoryId() == null ? null : product.getCategoryId().getValue(),
                product.getUnit(), product.getListPrice(), product.getDescription());
    }

    ProductCategoryVO categoryVO(ProductCategory value, List<ProductCategoryVO> children) {
        return new ProductCategoryVO(value.getId().getValue(),
                value.getParentCategoryId() == null ? null
                        : value.getParentCategoryId().getValue(),
                value.getName(), value.getStatus().name(), value.getVersion(),
                children == null ? List.of() : List.copyOf(children));
    }

    List<ProductFieldValue> customFields(
            Map<String, List<String>> requested,
            Collection<ProductFieldValue> existing,
            boolean replaceAll,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, ProductFieldValue> values = new LinkedHashMap<>();
        if (!replaceAll && existing != null) {
            existing.forEach(value -> values.put(value.fieldCode(), value));
        }
        if (requested != null) {
            for (Map.Entry<String, List<String>> entry : requested.entrySet()) {
                FieldCode code;
                try {
                    code = new FieldCode(entry.getKey());
                } catch (RuntimeException exception) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Product field code is invalid");
                }
                FieldDefinition field = fields.get(code);
                if (field == null || field.isBuiltin() || !field.isActive()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Product field is unavailable");
                }
                requireWrite(accesses, code.getValue());
                ProductFieldValue value = ProductFieldValuePolicy.normalize(
                        field, entry.getValue() == null ? List.of() : entry.getValue());
                if (value == null) {
                    values.remove(code);
                } else {
                    values.put(code, value);
                }
            }
        }
        for (FieldDefinition field : fields.values()) {
            if (!field.isBuiltin() && field.isActive() && field.isRequired()
                    && !values.containsKey(field.getCode())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "A required product field is empty");
            }
        }
        validateReferences(fields, values.values());
        return List.copyOf(values.values());
    }

    OrganizationMembership requireActiveMember(
            TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product owner is not an active tenant member"));
    }

    void requireVersion(Product product, Long version) {
        if (version == null || !version.equals(product.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Product version is stale");
        }
    }

    <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is invalid");
        }
    }

    NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private void validateReferences(
            Map<FieldCode, FieldDefinition> fields,
            Collection<ProductFieldValue> values) {
        Set<MemberId> members = new LinkedHashSet<>();
        Set<DepartmentId> departments = new LinkedHashSet<>();
        TenantId tenantId = null;
        for (ProductFieldValue value : values) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || !field.isActive()) {
                continue;
            }
            tenantId = field.getTenantId();
            if (field.getFieldType() == CustomFieldType.USER) {
                value.values().stream().map(Long::valueOf).map(MemberId::new)
                        .forEach(members::add);
            } else if (field.getFieldType() == CustomFieldType.DEPARTMENT) {
                value.values().stream().map(Long::valueOf).map(DepartmentId::new)
                        .forEach(departments::add);
            } else if (field.getFieldType() == CustomFieldType.RELATION) {
                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Product relation fields require an owning business module");
            }
        }
        if (tenantId == null) {
            return;
        }
        for (MemberId member : members) {
            requireActiveMember(tenantId, member);
        }
        if (!departments.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, departments.stream().toList()).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(departments)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product department field is invalid");
            }
        }
    }

    private Map<String, List<String>> customFields(
            Product product, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        product.getCustomFieldValues().forEach(value -> {
            EffectiveFieldAccess access = accesses.get(value.fieldCode());
            if (access != null && access.accessLevel() != FieldAccessLevel.HIDDEN) {
                values.put(value.fieldCode().getValue(), access.maskType() == FieldMaskType.NONE
                        ? value.values()
                        : value.values().stream().map(ignored -> REDACTED).toList());
            }
        });
        return Map.copyOf(values);
    }

    private String text(
            String value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.getOrDefault(
                new FieldCode(code), EffectiveFieldAccess.hidden());
        if (value == null || access.accessLevel() == FieldAccessLevel.HIDDEN) {
            return null;
        }
        if (access.maskType() == FieldMaskType.NONE) {
            return value;
        }
        if (access.maskType() == FieldMaskType.FULL
                || value.codePointCount(0, value.length()) < 3) {
            return REDACTED;
        }
        int count = value.codePointCount(0, value.length());
        int firstEnd = value.offsetByCodePoints(0, 1);
        int lastStart = value.offsetByCodePoints(0, count - 1);
        return value.substring(0, firstEnd) + "*".repeat(Math.min(8, count - 2))
                + value.substring(lastStart);
    }

    private Long number(
            Long value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return visibleUnmasked(accesses, code) ? value : null;
    }

    private BigDecimal decimal(
            BigDecimal value, Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        return visibleUnmasked(accesses, code) ? value : null;
    }

    private boolean visibleUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.getOrDefault(
                new FieldCode(code), EffectiveFieldAccess.hidden());
        return access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private boolean canReturnEditableBaseline(
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return EDITABLE_BASELINE_FIELDS.stream().allMatch(code -> {
            EffectiveFieldAccess access = accesses.get(new FieldCode(code));
            return access != null && access.accessLevel() == FieldAccessLevel.WRITE
                    && access.maskType() == FieldMaskType.NONE;
        });
    }
}
