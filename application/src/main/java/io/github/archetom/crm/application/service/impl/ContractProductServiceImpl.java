package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractProductLineRequest;
import io.github.archetom.crm.api.dto.request.ContractProductsUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.ProductCatalogLock;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver.ContractAccess;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ContractProductService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ContractProductLineVO;
import io.github.archetom.crm.application.vo.ContractProductsVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.ContractProductLine;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.exception.ContractNotFoundException;
import io.github.archetom.crm.domain.exception.ProductNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
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

/**
 * Keeps contract-product replacement separate from the general contract PATCH flow.
 * The returned snapshot is deliberately narrower than contract detail output, so it
 * can act as a complete relation-management baseline without exposing derived totals.
 */
@Service
@RequiredArgsConstructor
public class ContractProductServiceImpl implements ContractProductService {
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivablePlanRepository receivablePlanRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ContractAccessResolver accessResolver;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final ContractApplicationSupport contractSupport;
    private final ProductApplicationSupport productSupport;
    private final CustomizationObjectLock objectLock;
    private final ProductCatalogLock catalogLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<ContractProductsVO> get(
            AuthenticatedCaller caller, Long contractIdValue) {
        return queryTemplate.execute(UseCaseOperation.CONTRACT_PRODUCT_GET,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractId contractId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                                ContractAccessMode.READ);
                        organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        contractId = contractId(contractIdValue);
                    }

                    @Override
                    public ContractProductsVO execute() {
                        Contract contract = requireContract(access, contractId, false);
                        requireContractProducts(access, false);
                        requireProductSnapshotFields(access);
                        return toVO(contract);
                    }
                });
    }

    @Override
    public Result<ContractProductsVO> replace(
            AuthenticatedCaller caller, Long contractIdValue,
            ContractProductsUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTRACT_PRODUCT_REPLACE,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractId contractId;

                    @Override
                    public void validate() {
                        if (request == null || request.getLines() == null) {
                            throw contractSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Contract product request must not be empty");
                        }
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_WRITE,
                                ContractAccessMode.WRITE);
                        organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        contractId = contractId(contractIdValue);
                    }

                    @Override
                    public ContractProductsVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTRACT);
                        // Acquire before any product/category read to make one product catalog
                        // state authoritative for this complete relation replacement.
                        catalogLock.acquire(access.tenantId());
                        Contract contract = requireContract(access, contractId, true);
                        contractSupport.requireVersion(contract, request.getVersion());
                        requireContractProducts(access, true);
                        requireProductSnapshotFields(access);
                        contract.replaceProductLines(productLines(access, request.getLines()));
                        requireContractAmountNotBelowDependents(access, contractId, contract);
                        contractRepository.save(access.tenantId(), contract,
                                contractSupport.fields(access.tenantId()));
                        return toVO(contract);
                    }
                });
    }

    private Contract requireContract(
            ContractAccess access, ContractId contractId, boolean forUpdate) {
        return (forUpdate
                ? contractRepository.findVisibleByIdForUpdate(
                        access.tenantId(), contractId, access.visibility())
                : contractRepository.findVisibleById(
                        access.tenantId(), contractId, access.visibility()))
                .orElseThrow(ContractNotFoundException::new);
    }

    private void requireContractProducts(ContractAccess access, boolean write) {
        Map<FieldCode, EffectiveFieldAccess> fields = contractSupport.accesses(
                access.tenantId(), access.memberId());
        EffectiveFieldAccess products = fields.get(new FieldCode(ContractFieldCatalog.PRODUCTS));
        boolean denied = products == null
                || products.accessLevel() == FieldAccessLevel.HIDDEN
                || products.maskType() != FieldMaskType.NONE
                || write && products.accessLevel() != FieldAccessLevel.WRITE;
        if (denied) {
            throw contractSupport.error(ApplicationErrorCode.ACCESS_DENIED,
                    write ? "Contract products are not writable"
                            : "Contract products are not readable");
        }
    }

    private void requireProductSnapshotFields(ContractAccess access) {
        Map<FieldCode, EffectiveFieldAccess> fields = productSupport.accesses(
                access.tenantId(), access.memberId());
        List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.CATEGORY, ProductFieldCatalog.UNIT,
                        ProductFieldCatalog.LIST_PRICE)
                .forEach(code -> productSupport.requireReadableUnmasked(
                        fields, new FieldCode(code)));
    }

    private List<ContractProductLine> productLines(
            ContractAccess access, List<ContractProductLineRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<ProductId> ids = new LinkedHashSet<>();
        for (ContractProductLineRequest request : requests) {
            if (request == null || !ids.add(productId(request.getProductId()))) {
                throw contractSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract product lines are invalid");
            }
        }
        List<Product> products = productRepository.findAvailableByIdsForUpdate(
                access.tenantId(), ids, ids.size());
        Map<ProductId, Product> byId = products.stream().collect(Collectors.toMap(
                Product::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw new ProductNotFoundException();
        }
        Map<ProductCategoryId, String> categories = categoryNames(access, products);
        return java.util.stream.IntStream.range(0, requests.size()).mapToObj(index -> {
            ContractProductLineRequest request = requests.get(index);
            Product product = byId.get(new ProductId(request.getProductId()));
            return ContractProductLine.snapshot(product,
                    product.getCategoryId() == null ? null : categories.get(product.getCategoryId()),
                    request.getQuantity(), request.getUnitPrice(), index);
        }).toList();
    }

    private Map<ProductCategoryId, String> categoryNames(
            ContractAccess access, List<Product> products) {
        Set<ProductCategoryId> ids = products.stream().map(Product::getCategoryId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ProductCategory> categories = categoryRepository.findAllActive(
                access.tenantId(), 5_001);
        if (categories.size() > 5_000) {
            throw contractSupport.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Product category catalog is too large");
        }
        Map<ProductCategoryId, String> result = categories.stream()
                .filter(value -> ids.contains(value.getId()))
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        if (!result.keySet().containsAll(ids)) {
            throw new ProductNotFoundException();
        }
        return result;
    }

    private void requireContractAmountNotBelowDependents(
            ContractAccess access, ContractId contractId, Contract contract) {
        BigDecimal committed = invoiceRepository.sumCommittedAmount(
                access.tenantId(), contractId, null);
        BigDecimal received = receivableRepository.sumActiveAmount(
                access.tenantId(), contractId, null);
        BigDecimal planned = receivablePlanRepository.sumActivePlannedAmount(
                access.tenantId(), contractId, null);
        if (contract.getTotalAmount().compareTo(committed) < 0) {
            throw contractSupport.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Contract amount cannot be below committed invoice amount");
        }
        if (contract.getTotalAmount().compareTo(received) < 0) {
            throw contractSupport.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Contract amount cannot be below received amount");
        }
        if (contract.getTotalAmount().compareTo(planned) < 0) {
            throw contractSupport.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Contract amount cannot be below planned receivable amount");
        }
    }

    private ContractProductsVO toVO(Contract contract) {
        List<ContractProductLineVO> lines = contract.getProductLines().stream().map(line ->
                new ContractProductLineVO(line.productId().getValue(), line.productName(),
                        line.productCode(), line.categoryName(), line.unit(), line.listPrice(),
                        line.unitPrice(), line.quantity(), line.discountRate(), line.lineAmount(),
                        line.displayOrder())).toList();
        return new ContractProductsVO(contract.getId().getValue(), lines, contract.getVersion());
    }

    private ContractId contractId(Long value) {
        try {
            return new ContractId(value);
        } catch (RuntimeException exception) {
            throw contractSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract ID must be positive");
        }
    }

    private ProductId productId(Long value) {
        try {
            return new ProductId(value);
        } catch (RuntimeException exception) {
            throw contractSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product ID must be positive");
        }
    }
}
