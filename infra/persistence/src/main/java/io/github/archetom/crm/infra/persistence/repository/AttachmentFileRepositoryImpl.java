package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.AttachmentRelationState;
import io.github.archetom.crm.domain.model.MarketingActivityImageType;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.AttachmentFilePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentRelationMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentRelationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** MySQL implementation of the central, typed attachment relation. */
@Repository
@RequiredArgsConstructor
public class AttachmentFileRepositoryImpl implements AttachmentFileRepository {

    private static final int MAX_ACTIVE_RELATIONS = 100;

    private final AttachmentFileMapper fileMapper;
    private final AttachmentRelationMapper relationMapper;
    private final AttachmentFilePOConverter converter;

    @Override
    public AttachmentFile save(TenantId tenantId, AttachmentFile file) {
        requireTenantMatch(tenantId, file);
        if (file.getId() == null) {
            throw new IllegalArgumentException("attachment intent requires an exact target");
        }
        AttachmentFilePO po = converter.toPO(file);
        if (fileMapper.update(po, new LambdaUpdateWrapper<AttachmentFilePO>()
                .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                .eq(AttachmentFilePO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        file.onPersisted(new AttachmentFileId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return file;
    }

    @Override
    public AttachmentFile saveIntent(TenantId tenantId, AttachmentTarget target, AttachmentFile file,
                                     MarketingActivityImageType marketingActivityImageType,
                                     ProductImageType productImageType) {
        requireTenantMatch(tenantId, file);
        requireTarget(target);
        requireMarketingActivityImageType(target, marketingActivityImageType);
        requireProductImageType(target, productImageType);
        if (marketingActivityImageType != null && productImageType != null) {
            throw new IllegalArgumentException("attachment image groups are mutually exclusive");
        }
        if (file.getId() != null) {
            throw new IllegalArgumentException("attachment intent has already been persisted");
        }
        // Caller must already hold the exact target root lock; this lock/count is the database
        // backstop for the 100 active/private relation cap across replicas.
        if (relationMapper.selectCount(activeTargetQuery(tenantId, target).last("FOR UPDATE"))
                >= MAX_ACTIVE_RELATIONS) {
            throw new IllegalStateException("attachment target capacity is exhausted");
        }
        AttachmentFilePO po = converter.toPO(file);
        if (fileMapper.insert(po) != 1 || po.getId() == null) {
            throw new IllegalStateException("attachment file insert did not affect exactly one row");
        }
        AttachmentRelationPO relation = new AttachmentRelationPO();
        relation.setTenantId(tenantId.getValue());
        relation.setFileId(po.getId());
        relation.setTargetType(target.type().name());
        setTargetId(relation, target);
        relation.setMarketingActivityImageType(marketingActivityImageType == null ? null
                : marketingActivityImageType.name());
        relation.setProductImageType(productImageType == null ? null : productImageType.name());
        relation.setRelationState(AttachmentRelationState.ACTIVE.name());
        relation.setVersion(0L);
        if (relationMapper.insert(relation) != 1) {
            throw new IllegalStateException("attachment relation insert did not affect exactly one row");
        }
        file.onPersisted(new AttachmentFileId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return file;
    }

    @Override
    public void bindStagedProductImage(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId,
                                       ProductImageType productImageType) {
        requireTenant(tenantId);
        requireTarget(target);
        requireProductImageType(target, productImageType);
        if (fileId == null || productImageType == null) {
            throw new IllegalArgumentException("product image binding is invalid");
        }
        if (relationMapper.selectCount(activeTargetQuery(tenantId, target).last("FOR UPDATE"))
                >= MAX_ACTIVE_RELATIONS
                || countActiveProductImagesForUpdate(tenantId, target, productImageType) >= 9) {
            throw new IllegalStateException("product image capacity is exhausted");
        }
        AttachmentRelationPO relation = new AttachmentRelationPO();
        relation.setTenantId(tenantId.getValue());
        relation.setFileId(fileId.getValue());
        relation.setTargetType(target.type().name());
        setTargetId(relation, target);
        relation.setProductImageType(productImageType.name());
        relation.setRelationState(AttachmentRelationState.ACTIVE.name());
        relation.setVersion(0L);
        if (relationMapper.insert(relation) != 1) {
            throw new IllegalStateException("product image relation insert did not affect exactly one row");
        }
    }

    @Override
    public AttachmentFile saveStagedIntent(TenantId tenantId, AttachmentFile file) {
        requireTenantMatch(tenantId, file);
        if (file.getId() != null) {
            throw new IllegalArgumentException("attachment staged intent has already been persisted");
        }
        AttachmentFilePO po = converter.toPO(file);
        if (fileMapper.insert(po) != 1 || po.getId() == null) {
            throw new IllegalStateException("attachment staged file insert did not affect exactly one row");
        }
        file.onPersisted(new AttachmentFileId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return file;
    }

    @Override
    public Optional<AttachmentFile> findByIdForUpdate(TenantId tenantId, AttachmentFileId fileId) {
        requireTenant(tenantId);
        if (fileId == null) return Optional.empty();
        return Optional.ofNullable(converter.toDomain(fileMapper.selectOne(
                new LambdaQueryWrapper<AttachmentFilePO>()
                        .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                        .eq(AttachmentFilePO::getId, fileId.getValue()).last("FOR UPDATE"))));
    }

    @Override
    public Optional<AttachmentFile> findByTargetForUpdate(
            TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId) {
        requireTenant(tenantId);
        requireTarget(target);
        if (fileId == null) {
            return Optional.empty();
        }
        AttachmentRelationPO relation = relationMapper.selectOne(new QueryWrapper<AttachmentRelationPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("file_id", fileId.getValue())
                .eq("target_type", target.type().name())
                .eq(targetColumn(target), target.id()).last("FOR UPDATE"));
        if (relation == null) return Optional.empty();
        return Optional.ofNullable(converter.toDomain(fileMapper.selectOne(
                new LambdaQueryWrapper<AttachmentFilePO>()
                        .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                        .eq(AttachmentFilePO::getId, fileId.getValue()).last("FOR UPDATE"))));
    }

    @Override
    public Optional<AttachmentTarget> findTargetForFileForUpdate(TenantId tenantId, AttachmentFileId fileId) {
        requireTenant(tenantId);
        if (fileId == null) return Optional.empty();
        AttachmentRelationPO relation = relationMapper.selectByFileForUpdate(tenantId.getValue(), fileId.getValue());
        return targetOf(relation);
    }

    @Override
    public Optional<AttachmentTarget> findTargetForFile(TenantId tenantId, AttachmentFileId fileId) {
        requireTenant(tenantId);
        if (fileId == null) return Optional.empty();
        AttachmentRelationPO relation = relationMapper.selectByFile(tenantId.getValue(), fileId.getValue());
        return targetOf(relation);
    }

    @Override
    public void withdrawRelation(TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId) {
        requireTenant(tenantId); requireTarget(target);
        if (fileId == null || relationMapper.update(null, new UpdateWrapper<AttachmentRelationPO>()
                .set("relation_state", AttachmentRelationState.WITHDRAWN.name())
                .setSql("version = version + 1, updated_time = UTC_TIMESTAMP(6)")
                .eq("tenant_id", tenantId.getValue())
                .eq("file_id", fileId.getValue())
                .eq("target_type", target.type().name())
                .eq(targetColumn(target), target.id())
                .eq("relation_state", AttachmentRelationState.ACTIVE.name())) != 1) {
            throw new IllegalStateException("attachment relation was not active");
        }
    }

    @Override
    public Optional<AttachmentFile> findAvailableByTarget(
            TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId) {
        requireTenant(tenantId);
        requireTarget(target);
        if (fileId == null) {
            return Optional.empty();
        }
        return findByTarget(tenantId, target, fileId, false).filter(AttachmentFile::isAvailable);
    }

    @Override
    public PageResult<AttachmentFile> findAvailablePage(
            TenantId tenantId, AttachmentTarget target, long page, long size,
            MarketingActivityImageType marketingActivityImageType, ProductImageType productImageType) {
        requireTenant(tenantId);
        requireTarget(target);
        requireMarketingActivityImageType(target, marketingActivityImageType);
        requireProductImageType(target, productImageType);
        if (marketingActivityImageType != null && productImageType != null) {
            throw new IllegalArgumentException("attachment image groups are mutually exclusive");
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("attachment page is invalid");
        }
        long offset;
        try {
            offset = Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("attachment page is invalid", exception);
        }
        String imageType = marketingActivityImageType == null ? null : marketingActivityImageType.name();
        String productImage = productImageType == null ? null : productImageType.name();
        long total = fileMapper.countAvailableByTarget(tenantId.getValue(), target.type().name(), target.id(),
                imageType, productImage);
        if (offset >= total) return new PageResult<>(page, size, total, List.of());
        List<AttachmentFile> values = fileMapper.selectAvailablePageByTarget(
                tenantId.getValue(), target.type().name(), target.id(), imageType, productImage, offset, size).stream()
                .map(converter::toDomain).toList();
        return new PageResult<>(page, size, total, values);
    }

    @Override
    public long countActiveProductImagesForUpdate(TenantId tenantId, AttachmentTarget target,
                                                  ProductImageType productImageType) {
        requireTenant(tenantId);
        requireTarget(target);
        requireProductImageType(target, productImageType);
        if (productImageType == null) throw new IllegalArgumentException("product image type is required");
        return relationMapper.selectCount(activeTargetQuery(tenantId, target)
                .eq("product_image_type", productImageType.name()).last("FOR UPDATE"));
    }

    @Override
    public long countActiveMarketingActivityImagesForUpdate(TenantId tenantId, AttachmentTarget target,
                                                            MarketingActivityImageType marketingActivityImageType) {
        requireTenant(tenantId);
        requireTarget(target);
        requireMarketingActivityImageType(target, marketingActivityImageType);
        if (marketingActivityImageType == null) {
            throw new IllegalArgumentException("marketing activity image type is required");
        }
        return relationMapper.selectCount(activeTargetQuery(tenantId, target)
                .eq("marketing_activity_image_type", marketingActivityImageType.name()).last("FOR UPDATE"));
    }

    @Override
    public boolean existsHistoryByTargetForUpdate(TenantId tenantId, AttachmentTarget target) {
        requireTenant(tenantId);
        requireTarget(target);
        return relationMapper.selectOne(targetQuery(tenantId, target).last("LIMIT 1 FOR UPDATE")) != null;
    }

    @Override
    public List<AttachmentFile> findActiveByTargetForUpdate(TenantId tenantId, AttachmentTarget target) {
        requireTenant(tenantId);
        requireTarget(target);
        // A record holds at most MAX_ACTIVE_RELATIONS active attachments; the extra row makes an
        // overflow visible instead of letting an unbounded read silently grow with the data.
        return activeFiles(tenantId, target, true, MAX_ACTIVE_RELATIONS + 1);
    }

    @Override
    public List<AttachmentFile> findActiveByTargetForUpdate(
            TenantId tenantId, AttachmentTarget target, int limit) {
        requireTenant(tenantId);
        requireTarget(target);
        if (limit < 1 || limit > MAX_ACTIVE_RELATIONS + 1) {
            throw new IllegalArgumentException("attachment relation limit is invalid");
        }
        return activeFiles(tenantId, target, true, limit);
    }

    private Optional<AttachmentFile> findByTarget(
            TenantId tenantId, AttachmentTarget target, AttachmentFileId fileId, boolean forUpdate) {
        AttachmentRelationPO relation = relationMapper.selectOne(new QueryWrapper<AttachmentRelationPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("file_id", fileId.getValue())
                .eq("target_type", target.type().name())
                .eq(targetColumn(target), target.id())
                .eq("relation_state", AttachmentRelationState.ACTIVE.name())
                .last(forUpdate ? "FOR UPDATE" : ""));
        if (relation == null) return Optional.empty();
        return Optional.ofNullable(converter.toDomain(fileMapper.selectOne(
                new LambdaQueryWrapper<AttachmentFilePO>()
                        .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                        .eq(AttachmentFilePO::getId, fileId.getValue())
                        .last(forUpdate ? "FOR UPDATE" : ""))));
    }

    private List<AttachmentFile> activeFiles(
            TenantId tenantId, AttachmentTarget target, boolean forUpdate, int limit) {
        if (limit < 1 || limit > MAX_ACTIVE_RELATIONS + 1) {
            throw new IllegalArgumentException("attachment relation limit is invalid");
        }
        String suffix = "LIMIT " + limit + (forUpdate ? " FOR UPDATE" : "");
        List<AttachmentRelationPO> relations = relationMapper.selectList(activeTargetQuery(tenantId, target)
                .orderByAsc("file_id").last(suffix));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> fileIds = relations.stream().map(AttachmentRelationPO::getFileId).distinct().toList();
        // One query for the whole relation set. Ascending id keeps the same lock order the
        // per-relation loop produced, so the row-lock sequence is unchanged.
        List<AttachmentFilePO> files = fileMapper.selectList(
                new LambdaQueryWrapper<AttachmentFilePO>()
                        .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                        .in(AttachmentFilePO::getId, fileIds)
                        .orderByAsc(AttachmentFilePO::getId)
                        .last(forUpdate ? "FOR UPDATE" : ""));
        if (files.size() != fileIds.size()) {
            throw new IllegalStateException("an active attachment relation has no file row");
        }
        return files.stream().map(converter::toDomain).toList();
    }

    private static QueryWrapper<AttachmentRelationPO> activeTargetQuery(
            TenantId tenantId, AttachmentTarget target) {
        return targetQuery(tenantId, target)
                .eq("relation_state", AttachmentRelationState.ACTIVE.name());
    }

    private static QueryWrapper<AttachmentRelationPO> targetQuery(
            TenantId tenantId, AttachmentTarget target) {
        return new QueryWrapper<AttachmentRelationPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("target_type", target.type().name())
                .eq(targetColumn(target), target.id());
    }

    private static Optional<AttachmentTarget> targetOf(AttachmentRelationPO relation) {
        if (relation == null || relation.getTargetType() == null) return Optional.empty();
        try {
            AttachmentTargetType type = AttachmentTargetType.valueOf(relation.getTargetType());
            Long id = targetId(relation, type);
            return id == null ? Optional.empty() : Optional.of(new AttachmentTarget(type, id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void setTargetId(AttachmentRelationPO relation, AttachmentTarget target) {
        if (target.type() == AttachmentTargetType.WORK_TASK) relation.setWorkTaskId(target.id());
        else if (target.type() == AttachmentTargetType.CUSTOMER) relation.setCustomerId(target.id());
        else if (target.type() == AttachmentTargetType.CONTACT) relation.setContactId(target.id());
        else if (target.type() == AttachmentTargetType.LEAD) relation.setLeadId(target.id());
        else if (target.type() == AttachmentTargetType.OPPORTUNITY) relation.setOpportunityId(target.id());
        else if (target.type() == AttachmentTargetType.CONTRACT) relation.setContractId(target.id());
        else if (target.type() == AttachmentTargetType.INVOICE) relation.setInvoiceId(target.id());
        else if (target.type() == AttachmentTargetType.RECEIVABLE) relation.setReceivableId(target.id());
        else if (target.type() == AttachmentTargetType.RECEIVABLE_PLAN) relation.setReceivablePlanId(target.id());
        else if (target.type() == AttachmentTargetType.PRODUCT) relation.setProductId(target.id());
        else if (target.type() == AttachmentTargetType.RETURN_VISIT) relation.setReturnVisitId(target.id());
        else if (target.type() == AttachmentTargetType.MARKETING_ACTIVITY) relation.setMarketingActivityId(target.id());
        else if (target.type() == AttachmentTargetType.OA_ANNOUNCEMENT) relation.setOaAnnouncementId(target.id());
        else if (target.type() == AttachmentTargetType.OA_REQUEST) relation.setOaRequestId(target.id());
        else if (target.type() == AttachmentTargetType.IMPORT_DRAFT) relation.setImportDraftId(target.id());
        else throw new IllegalArgumentException("attachment target is unsupported");
    }

    private static String targetColumn(AttachmentTarget target) {
        if (target.type() == AttachmentTargetType.WORK_TASK) return "work_task_id";
        if (target.type() == AttachmentTargetType.CUSTOMER) return "customer_id";
        if (target.type() == AttachmentTargetType.CONTACT) return "contact_id";
        if (target.type() == AttachmentTargetType.LEAD) return "lead_id";
        if (target.type() == AttachmentTargetType.OPPORTUNITY) return "opportunity_id";
        if (target.type() == AttachmentTargetType.CONTRACT) return "contract_id";
        if (target.type() == AttachmentTargetType.INVOICE) return "invoice_id";
        if (target.type() == AttachmentTargetType.RECEIVABLE) return "receivable_id";
        if (target.type() == AttachmentTargetType.RECEIVABLE_PLAN) return "receivable_plan_id";
        if (target.type() == AttachmentTargetType.PRODUCT) return "product_id";
        if (target.type() == AttachmentTargetType.RETURN_VISIT) return "return_visit_id";
        if (target.type() == AttachmentTargetType.MARKETING_ACTIVITY) return "marketing_activity_id";
        if (target.type() == AttachmentTargetType.OA_ANNOUNCEMENT) return "oa_announcement_id";
        if (target.type() == AttachmentTargetType.OA_REQUEST) return "oa_request_id";
        if (target.type() == AttachmentTargetType.IMPORT_DRAFT) return "import_draft_id";
        throw new IllegalArgumentException("attachment target is unsupported");
    }

    private static Long targetId(AttachmentRelationPO relation, AttachmentTargetType type) {
        if (type == AttachmentTargetType.WORK_TASK) return relation.getWorkTaskId();
        if (type == AttachmentTargetType.CUSTOMER) return relation.getCustomerId();
        if (type == AttachmentTargetType.CONTACT) return relation.getContactId();
        if (type == AttachmentTargetType.LEAD) return relation.getLeadId();
        if (type == AttachmentTargetType.OPPORTUNITY) return relation.getOpportunityId();
        if (type == AttachmentTargetType.CONTRACT) return relation.getContractId();
        if (type == AttachmentTargetType.INVOICE) return relation.getInvoiceId();
        if (type == AttachmentTargetType.RECEIVABLE) return relation.getReceivableId();
        if (type == AttachmentTargetType.RECEIVABLE_PLAN) return relation.getReceivablePlanId();
        if (type == AttachmentTargetType.PRODUCT) return relation.getProductId();
        if (type == AttachmentTargetType.RETURN_VISIT) return relation.getReturnVisitId();
        if (type == AttachmentTargetType.MARKETING_ACTIVITY) return relation.getMarketingActivityId();
        if (type == AttachmentTargetType.OA_ANNOUNCEMENT) return relation.getOaAnnouncementId();
        if (type == AttachmentTargetType.OA_REQUEST) return relation.getOaRequestId();
        if (type == AttachmentTargetType.IMPORT_DRAFT) return relation.getImportDraftId();
        return null;
    }

    private static void requireTenantMatch(TenantId tenantId, AttachmentFile file) {
        requireTenant(tenantId);
        if (file == null || file.getTenantId() == null || !tenantId.sameValueAs(file.getTenantId())) {
            throw new IllegalArgumentException("attachment file tenant does not match repository tenant");
        }
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("attachment tenant is required");
        }
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() == null) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static void requireMarketingActivityImageType(
            AttachmentTarget target, MarketingActivityImageType marketingActivityImageType) {
        if (marketingActivityImageType != null && target.type() != AttachmentTargetType.MARKETING_ACTIVITY) {
            throw new IllegalArgumentException("marketing activity image type is unsupported");
        }
    }

    private static void requireProductImageType(AttachmentTarget target, ProductImageType productImageType) {
        if (productImageType != null && target.type() != AttachmentTargetType.PRODUCT) {
            throw new IllegalArgumentException("product image type is unsupported");
        }
    }
}
