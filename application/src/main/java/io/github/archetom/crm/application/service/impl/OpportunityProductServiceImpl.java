package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityProductLineRequest;
import io.github.archetom.crm.api.dto.request.OpportunityProductsUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.ProductCatalogLock;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver.OpportunityAccess;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.OpportunityProductService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.OpportunityProductLineVO;
import io.github.archetom.crm.application.vo.OpportunityProductsVO;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OpportunityProductLine;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.exception.OpportunityNotFoundException;
import io.github.archetom.crm.domain.exception.ProductNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpportunityProductServiceImpl implements OpportunityProductService {

    private final OpportunityRepository opportunityRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final OrganizationAccessGuard accessGuard;
    private final OpportunityApplicationSupport opportunitySupport;
    private final ProductApplicationSupport productSupport;
    private final CustomizationObjectLock objectLock;
    private final ProductCatalogLock catalogLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<OpportunityProductsVO> get(
            AuthenticatedCaller caller, Long opportunityIdValue) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_PRODUCT_GET,
                new ServiceOperation<>() {
                    private OpportunityAccess opportunityAccess;
                    private OpportunityId opportunityId;

                    @Override
                    public void validate() {
                        opportunityAccess = opportunityAccessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        opportunityId = opportunityId(opportunityIdValue);
                    }

                    @Override
                    public OpportunityProductsVO execute() {
                        Opportunity opportunity = requireOpportunity(
                                opportunityAccess, opportunityId);
                        requireReadable(opportunityAccess, false);
                        requireSnapshotFieldsReadable(opportunityAccess);
                        return toVO(opportunity);
                    }
                });
    }

    @Override
    public Result<OpportunityProductsVO> replace(
            AuthenticatedCaller caller, Long opportunityIdValue,
            OpportunityProductsUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_PRODUCT_REPLACE,
                new ServiceOperation<>() {
                    private OpportunityAccess opportunityAccess;
                    private OpportunityId opportunityId;

                    @Override
                    public void validate() {
                        if (request == null || request.getLines() == null) {
                            throw opportunitySupport.error(
                                    ApplicationErrorCode.PARAMETER_INVALID,
                                    "Opportunity product request must not be empty");
                        }
                        opportunityAccess = opportunityAccessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_WRITE,
                                OpportunityAccessMode.WRITE);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        opportunityId = opportunityId(opportunityIdValue);
                    }

                    @Override
                    public OpportunityProductsVO execute() {
                        objectLock.acquire(opportunityAccess.tenantId(),
                                BusinessObjectType.OPPORTUNITY);
                        // Acquire before the first consistent read in this transaction so
                        // products and their categories come from one stable catalog state.
                        catalogLock.acquire(opportunityAccess.tenantId());
                        Opportunity opportunity = requireOpportunity(
                                opportunityAccess, opportunityId);
                        opportunitySupport.requireVersion(
                                opportunity, request.getVersion());
                        requireReadable(opportunityAccess, true);
                        requireSnapshotFieldsReadable(opportunityAccess);
                        List<ProductId> requestedIds = productIds(request.getLines());
                        List<Product> products = productRepository.findAvailableByIdsForUpdate(
                                opportunityAccess.tenantId(), requestedIds,
                                Math.max(1, requestedIds.size()));
                        Map<ProductId, Product> byId = products.stream().collect(
                                Collectors.toMap(Product::getId, Function.identity()));
                        if (byId.size() != requestedIds.size()) {
                            throw new ProductNotFoundException();
                        }
                        Map<ProductCategoryId, String> categories = categoryNames(
                                opportunityAccess, products);
                        List<OpportunityProductLine> lines = java.util.stream.IntStream
                                .range(0, request.getLines().size()).mapToObj(index -> {
                                    OpportunityProductLineRequest line =
                                            request.getLines().get(index);
                                    Product product = byId.get(new ProductId(line.getProductId()));
                                    String categoryName = product.getCategoryId() == null
                                            ? null : categories.get(product.getCategoryId());
                                    return OpportunityProductLine.snapshot(product,
                                            categoryName,
                                            line.getQuantity(), line.getUnitPrice(), index);
                                }).toList();
                        opportunity.replaceProductLines(lines);
                        opportunityRepository.save(opportunityAccess.tenantId(), opportunity,
                                opportunitySupport.fields(opportunityAccess.tenantId()));
                        return toVO(opportunity);
                    }
                });
    }

    private void requireReadable(OpportunityAccess access, boolean write) {
        Map<FieldCode, EffectiveFieldAccess> fields = opportunitySupport.accesses(
                access.tenantId(), access.memberId());
        EffectiveFieldAccess productAccess = fields.get(
                new FieldCode(OpportunityFieldCatalog.PRODUCTS));
        boolean denied = productAccess == null
                || productAccess.accessLevel() == FieldAccessLevel.HIDDEN
                || productAccess.maskType()
                    != io.github.archetom.crm.domain.model.FieldMaskType.NONE
                || write && productAccess.accessLevel() != FieldAccessLevel.WRITE;
        if (denied) {
            throw opportunitySupport.error(ApplicationErrorCode.ACCESS_DENIED,
                    write ? "Opportunity products are not writable"
                            : "Opportunity products are not readable");
        }
    }

    private void requireSnapshotFieldsReadable(OpportunityAccess access) {
        Map<FieldCode, EffectiveFieldAccess> fields = productSupport.accesses(
                access.tenantId(), access.memberId());
        List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.CATEGORY, ProductFieldCatalog.UNIT,
                        ProductFieldCatalog.LIST_PRICE)
                .forEach(code -> productSupport.requireReadableUnmasked(
                        fields, new FieldCode(code)));
    }

    private Opportunity requireOpportunity(
            OpportunityAccess access, OpportunityId opportunityId) {
        return opportunityRepository.findVisibleById(
                        access.tenantId(), opportunityId, access.visibility())
                .orElseThrow(OpportunityNotFoundException::new);
    }

    private List<ProductId> productIds(List<OpportunityProductLineRequest> lines) {
        LinkedHashSet<ProductId> ids = new LinkedHashSet<>();
        for (OpportunityProductLineRequest line : lines) {
            if (line == null) {
                throw opportunitySupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Opportunity product line is invalid");
            }
            ProductId id;
            try {
                id = new ProductId(line.getProductId());
            } catch (RuntimeException exception) {
                throw opportunitySupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product ID must be positive");
            }
            if (!ids.add(id)) {
                throw opportunitySupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Opportunity products must be unique");
            }
        }
        return List.copyOf(ids);
    }

    private Map<ProductCategoryId, String> categoryNames(
            OpportunityAccess access, List<Product> products) {
        Set<ProductCategoryId> ids = products.stream().map(Product::getCategoryId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ProductCategory> categories = categoryRepository.findAllActive(
                access.tenantId(), 5_001);
        if (categories.size() > 5_000) {
            throw opportunitySupport.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Product category catalog is too large");
        }
        Map<ProductCategoryId, String> result = categories.stream()
                .filter(value -> ids.contains(value.getId()))
                .collect(Collectors.toMap(ProductCategory::getId,
                        ProductCategory::getName));
        if (!result.keySet().containsAll(ids)) {
            throw new ProductNotFoundException();
        }
        return result;
    }

    private OpportunityProductsVO toVO(Opportunity opportunity) {
        List<OpportunityProductLineVO> lines = opportunity.getProductLines().stream()
                .map(line -> new OpportunityProductLineVO(
                        line.productId().getValue(), line.productName(), line.productCode(),
                        line.categoryName(), line.unit(), line.listPrice(), line.unitPrice(),
                        line.quantity(), line.discountRate(), line.lineAmount(),
                        line.displayOrder())).toList();
        BigDecimal total = lines.stream().map(OpportunityProductLineVO::lineAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new OpportunityProductsVO(opportunity.getId().getValue(),
                lines, total, opportunity.getVersion());
    }

    private OpportunityId opportunityId(Long value) {
        try {
            return new OpportunityId(value);
        } catch (RuntimeException exception) {
            throw opportunitySupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity ID must be positive");
        }
    }
}
