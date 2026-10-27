package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductAvailabilityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.ProductCatalogLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ProductCommandService;
import io.github.archetom.crm.application.service.ProductCreationCoordinator;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.ProductImageDraftService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ProductCategoryVO;
import io.github.archetom.crm.application.vo.ProductVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.exception.ProductCategoryNotFoundException;
import io.github.archetom.crm.domain.exception.ProductNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductCommandServiceImpl implements ProductCommandService, ProductCreationCoordinator {

    private static final int MAX_CATEGORIES = 5_000;
    private static final int MAX_CATEGORY_DEPTH = 16;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductApplicationSupport support;
    private final OrganizationAccessGuard accessGuard;
    private final CustomizationObjectLock objectLock;
    private final ProductCatalogLock catalogLock;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final ProductImageDraftService productImageDraftService;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<ProductVO> create(
            AuthenticatedCaller caller, ProductCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_CREATE, createOperation(caller, request));
    }

    @Override
    public ProductVO createInCurrentTransaction(
            AuthenticatedCaller caller, ProductCreateRequest request) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Product creation coordination requires an active transaction");
        }
        ServiceOperation<ProductVO> operation = createOperation(caller, request);
        operation.validate();
        operation.prepare();
        ProductVO result = operation.execute();
        operation.onSuccess(result);
        return result;
    }

    private ServiceOperation<ProductVO> createOperation(
            AuthenticatedCaller caller, ProductCreateRequest request) {
        return new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId callerMember;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product create request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_WRITE);
                        if (changesFileFields(request.getFileFieldDraftId(), request.getFileFields())
                                || changesProductImages(request.getProductImageDraftId(), request.getProductImages())) {
                            accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        }
                        callerMember = member(caller.actorId());
                    }

                    @Override
                    public ProductVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.PRODUCT);
                        catalogLock.acquire(tenantId);
                        List<FieldDefinition> fields = support.fields(tenantId);
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> accesses =
                                support.accesses(tenantId, callerMember);
                        requireCreateWrites(request, accesses);
                        ProductCategory category = category(
                                tenantId, categoryId(request.getCategoryId()));
                        MemberId owner = request.getOwnerActorId() == null
                                ? callerMember : member(request.getOwnerActorId());
                        if (!owner.sameValueAs(callerMember)) {
                            accessGuard.requireActiveMembership(
                                    caller, AuthorityCatalog.PRODUCTS_ASSIGN);
                        }
                        support.requireActiveMember(tenantId, owner);
                        Product product = Product.create(tenantId,
                                category == null ? null : category.getId(), owner,
                                request.getName(), request.getCode(), request.getUnit(),
                                request.getListPrice(), request.getDescription(),
                                request.getAvailability() == null
                                        ? ProductAvailability.AVAILABLE
                                        : support.parse(ProductAvailability.class,
                                                request.getAvailability(),
                                                "Product availability"),
                                support.customFields(request.getCustomFields(), List.of(),
                                        true, byCode, accesses));
                        productRepository.save(tenantId, product, fields);
                        fileFieldBindingService.consumeCreateDraft(tenantId, callerMember,
                                BusinessObjectType.PRODUCT, request.getFileFieldDraftId(),
                                new AttachmentTarget(AttachmentTargetType.PRODUCT,
                                        product.getId().getValue()), request.getFileFields(), byCode, accesses);
                        productImageDraftService.consumeCreateDraft(tenantId, callerMember,
                                request.getProductImageDraftId(), request.getProductImages(),
                                new AttachmentTarget(AttachmentTargetType.PRODUCT, product.getId().getValue()));
                        return support.toVO(product,
                                category == null ? null : category.getName(), accesses);
                    }
        };
    }

    @Override
    public Result<ProductVO> update(
            AuthenticatedCaller caller, Long productIdValue,
            ProductUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ProductId productId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product update request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_WRITE);
                        if (changesFileFields(request.getFileFields())) {
                            accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                        }
                        memberId = member(caller.actorId());
                        productId = productId(productIdValue);
                    }

                    @Override
                    public ProductVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.PRODUCT);
                        catalogLock.acquire(tenantId);
                        Product product = requireProduct(tenantId, productId);
                        support.requireVersion(product, request.getVersion());
                        List<FieldDefinition> fields = support.fields(tenantId);
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> accesses =
                                support.accesses(tenantId, memberId);
                        requireUpdateWrites(accesses);
                        ProductCategory category = category(
                                tenantId, categoryId(request.getCategoryId()));
                        product.replaceDetails(category == null ? null : category.getId(),
                                request.getName(), request.getCode(), request.getUnit(),
                                request.getListPrice(), request.getDescription(),
                                support.customFields(request.getCustomFields(),
                                        product.getCustomFieldValues(), false,
                                        byCode, accesses));
                        productRepository.save(tenantId, product, fields);
                        fileFieldBindingService.replaceExisting(tenantId, memberId,
                                BusinessObjectType.PRODUCT,
                                new AttachmentTarget(AttachmentTargetType.PRODUCT,
                                        product.getId().getValue()), request.getFileFields(), byCode, accesses);
                        return support.toVO(product,
                                category == null ? null : category.getName(), accesses);
                    }
                });
    }

    @Override
    public Result<ProductVO> updateAvailability(
            AuthenticatedCaller caller, Long productIdValue,
            ProductAvailabilityUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_AVAILABILITY_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ProductId productId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product availability request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_WRITE);
                        memberId = member(caller.actorId());
                        productId = productId(productIdValue);
                    }

                    @Override
                    public ProductVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.PRODUCT);
                        catalogLock.acquire(tenantId);
                        Product product = requireProduct(tenantId, productId);
                        support.requireVersion(product, request.getVersion());
                        Map<FieldCode, EffectiveFieldAccess> accesses =
                                support.accesses(tenantId, memberId);
                        support.requireWrite(accesses, ProductFieldCatalog.AVAILABILITY);
                        product.changeAvailability(support.parse(
                                ProductAvailability.class, request.getAvailability(),
                                "Product availability"));
                        productRepository.save(tenantId, product, support.fields(tenantId));
                        return support.toVO(product,
                                categoryName(tenantId, product.getCategoryId()), accesses);
                    }
                });
    }

    @Override
    public Result<ProductVO> updateOwner(
            AuthenticatedCaller caller, Long productIdValue,
            ProductOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private MemberId owner;
                    private ProductId productId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product owner request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_ASSIGN);
                        memberId = member(caller.actorId());
                        owner = member(request.getOwnerActorId());
                        productId = productId(productIdValue);
                    }

                    @Override
                    public ProductVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.PRODUCT);
                        catalogLock.acquire(tenantId);
                        Product product = requireProduct(tenantId, productId);
                        support.requireVersion(product, request.getVersion());
                        Map<FieldCode, EffectiveFieldAccess> accesses =
                                support.accesses(tenantId, memberId);
                        support.requireWrite(accesses, ProductFieldCatalog.OWNER_ACTOR);
                        support.requireActiveMember(tenantId, owner);
                        product.changeOwner(owner);
                        productRepository.save(tenantId, product, support.fields(tenantId));
                        return support.toVO(product,
                                categoryName(tenantId, product.getCategoryId()), accesses);
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long productIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ProductId productId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_DELETE);
                        productId = productId(productIdValue);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.PRODUCT);
                        catalogLock.acquire(tenantId);
                        Product product = requireProduct(tenantId, productId);
                        support.requireVersion(product, version);
                        product.delete();
                        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.PRODUCT,
                                productId.getValue());
                        attachmentParentLifecycle.withdrawAll(tenantId, target);
                        fileFieldBindingService.withdrawAll(tenantId, target);
                        productRepository.save(tenantId, product, support.fields(tenantId));
                        return null;
                    }
                });
    }

    @Override
    public Result<ProductCategoryVO> createCategory(
            AuthenticatedCaller caller, ProductCategoryCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_CATEGORY_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product category create request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_WRITE);
                    }

                    @Override
                    public ProductCategoryVO execute() {
                        catalogLock.acquire(tenantId);
                        List<ProductCategory> categories = categories(tenantId);
                        if (categories.size() >= MAX_CATEGORIES) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Product category limit has been reached");
                        }
                        ProductCategoryId parentId = categoryId(
                                request.getParentCategoryId());
                        requirePlacement(null, parentId, categories);
                        ProductCategory category = ProductCategory.create(
                                tenantId, parentId, request.getName());
                        categoryRepository.save(tenantId, category);
                        return support.categoryVO(category, List.of());
                    }
                });
    }

    @Override
    public Result<ProductCategoryVO> updateCategory(
            AuthenticatedCaller caller, Long categoryIdValue,
            ProductCategoryUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_CATEGORY_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ProductCategoryId categoryId;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product category update request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_WRITE);
                        categoryId = categoryId(categoryIdValue);
                    }

                    @Override
                    public ProductCategoryVO execute() {
                        catalogLock.acquire(tenantId);
                        ProductCategory category = requireCategory(tenantId, categoryId);
                        requireVersion(category, request.getVersion());
                        List<ProductCategory> categories = categories(tenantId);
                        ProductCategoryId parentId = categoryId(
                                request.getParentCategoryId());
                        requirePlacement(categoryId, parentId, categories);
                        category.change(parentId, request.getName());
                        categoryRepository.save(tenantId, category);
                        return support.categoryVO(category, List.of());
                    }
                });
    }

    @Override
    public Result<Void> deleteCategory(
            AuthenticatedCaller caller, Long categoryIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.PRODUCT_CATEGORY_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ProductCategoryId categoryId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_DELETE);
                        categoryId = categoryId(categoryIdValue);
                    }

                    @Override
                    public Void execute() {
                        catalogLock.acquire(tenantId);
                        ProductCategory category = requireCategory(tenantId, categoryId);
                        requireVersion(category, version);
                        if (categoryRepository.existsActiveChild(tenantId, categoryId)
                                || productRepository.existsActiveByCategory(
                                        tenantId, categoryId)) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Product category is still in use");
                        }
                        category.delete();
                        categoryRepository.save(tenantId, category);
                        return null;
                    }
                });
    }

    private void requireCreateWrites(
            ProductCreateRequest request,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        support.requireWrite(accesses, ProductFieldCatalog.NAME);
        support.requireWrite(accesses, ProductFieldCatalog.CODE);
        support.requireWrite(accesses, ProductFieldCatalog.LIST_PRICE);
        requireWriteIfPresent(accesses, ProductFieldCatalog.CATEGORY,
                request.getCategoryId());
        requireWriteIfPresent(accesses, ProductFieldCatalog.UNIT,
                request.getUnit());
        requireWriteIfPresent(accesses, ProductFieldCatalog.DESCRIPTION,
                request.getDescription());
        requireWriteIfPresent(accesses, ProductFieldCatalog.OWNER_ACTOR,
                request.getOwnerActorId());
        requireWriteIfPresent(accesses, ProductFieldCatalog.AVAILABILITY,
                request.getAvailability());
    }

    private void requireUpdateWrites(Map<FieldCode, EffectiveFieldAccess> accesses) {
        support.requireWrite(accesses, ProductFieldCatalog.NAME);
        support.requireWrite(accesses, ProductFieldCatalog.CODE);
        support.requireWrite(accesses, ProductFieldCatalog.CATEGORY);
        support.requireWrite(accesses, ProductFieldCatalog.UNIT);
        support.requireWrite(accesses, ProductFieldCatalog.LIST_PRICE);
        support.requireWrite(accesses, ProductFieldCatalog.DESCRIPTION);
    }

    private void requireWriteIfPresent(
            Map<FieldCode, EffectiveFieldAccess> accesses,
            String fieldCode, Object value) {
        if (value != null) {
            support.requireWrite(accesses, fieldCode);
        }
    }

    private Product requireProduct(TenantId tenantId, ProductId productId) {
        return productRepository.findByIdForAttachmentMutation(tenantId, productId)
                .filter(value -> value.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(ProductNotFoundException::new);
    }

    private static boolean changesFileFields(Long draftId, Map<String, List<Long>> fileFields) {
        return draftId != null || changesFileFields(fileFields);
    }

    private static boolean changesFileFields(Map<String, List<Long>> fileFields) {
        return fileFields != null && !fileFields.isEmpty();
    }

    private static boolean changesProductImages(Long draftId, Map<String, List<Long>> images) {
        return draftId != null || images != null && !images.isEmpty();
    }

    private ProductCategory requireCategory(
            TenantId tenantId, ProductCategoryId categoryId) {
        return categoryRepository.findActiveById(tenantId, categoryId)
                .orElseThrow(ProductCategoryNotFoundException::new);
    }

    private ProductCategory category(
            TenantId tenantId, ProductCategoryId categoryId) {
        return categoryId == null ? null : requireCategory(tenantId, categoryId);
    }

    private String categoryName(TenantId tenantId, ProductCategoryId categoryId) {
        ProductCategory category = category(tenantId, categoryId);
        return category == null ? null : category.getName();
    }

    private List<ProductCategory> categories(TenantId tenantId) {
        List<ProductCategory> values = categoryRepository.findAllActive(
                tenantId, MAX_CATEGORIES + 1);
        if (values.size() > MAX_CATEGORIES) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Product category catalog is too large");
        }
        return values;
    }

    private void requirePlacement(
            ProductCategoryId movingId, ProductCategoryId parentId,
            List<ProductCategory> categories) {
        Map<ProductCategoryId, ProductCategory> byId = new HashMap<>();
        categories.forEach(value -> byId.put(value.getId(), value));
        if (parentId != null && !byId.containsKey(parentId)) {
            throw new ProductCategoryNotFoundException();
        }
        Set<ProductCategoryId> ancestors = new HashSet<>();
        ProductCategoryId cursor = parentId;
        int parentDepth = 0;
        while (cursor != null) {
            if (!ancestors.add(cursor) || movingId != null && movingId.sameValueAs(cursor)) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product category hierarchy would form a cycle");
            }
            parentDepth++;
            ProductCategory node = byId.get(cursor);
            if (node == null) {
                throw new ProductCategoryNotFoundException();
            }
            cursor = node.getParentCategoryId();
        }
        int subtreeDepth = movingId == null ? 1
                : subtreeDepth(movingId, children(categories), new HashSet<>());
        if (parentDepth + subtreeDepth > MAX_CATEGORY_DEPTH) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Product category hierarchy is too deep");
        }
    }

    private Map<ProductCategoryId, List<ProductCategoryId>> children(
            List<ProductCategory> categories) {
        Map<ProductCategoryId, List<ProductCategoryId>> children = new HashMap<>();
        for (ProductCategory category : categories) {
            if (category.getParentCategoryId() != null) {
                children.computeIfAbsent(category.getParentCategoryId(), ignored ->
                        new ArrayList<>()).add(category.getId());
            }
        }
        return children;
    }

    private int subtreeDepth(
            ProductCategoryId root,
            Map<ProductCategoryId, List<ProductCategoryId>> children,
            Set<ProductCategoryId> visiting) {
        if (!visiting.add(root)) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Stored product category hierarchy is invalid");
        }
        int depth = 1;
        for (ProductCategoryId child : children.getOrDefault(root, List.of())) {
            depth = Math.max(depth, 1 + subtreeDepth(child, children, visiting));
        }
        visiting.remove(root);
        return depth;
    }

    private void requireVersion(ProductCategory category, Long version) {
        if (version == null || !version.equals(category.getVersion())) {
            throw support.error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Product category version is stale");
        }
    }

    private ProductId productId(Long value) {
        try {
            return new ProductId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product ID must be positive");
        }
    }

    private ProductCategoryId categoryId(Long value) {
        try {
            return value == null ? null : new ProductCategoryId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product category ID must be positive");
        }
    }

    private MemberId member(Long value) {
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product owner ID must be positive");
        }
    }

    private void requireRequest(Object value, String description) {
        if (value == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }
}
