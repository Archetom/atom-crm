package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.ProductImageDraft;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.ProductImageDraftState;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.repository.ProductImageDraftRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductImageDraftId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.AttachmentFilePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductImageDraftFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductImageDraftMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductImageDraftFilePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductImageDraftPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** MySQL persistence for authenticated Product image staging drafts. */
@Repository
@RequiredArgsConstructor
public class ProductImageDraftRepositoryImpl implements ProductImageDraftRepository {
    private final ProductImageDraftMapper draftMapper;
    private final ProductImageDraftFileMapper fileMapper;
    private final AttachmentFileMapper attachmentFileMapper;
    private final AttachmentFilePOConverter attachmentConverter;

    @Override
    public ProductImageDraft save(TenantId tenantId, ProductImageDraft draft) {
        requireTenant(tenantId);
        if (draft == null || draft.getTenantId() == null || !tenantId.sameValueAs(draft.getTenantId())) {
            throw new IllegalArgumentException("product image draft tenant does not match");
        }
        ProductImageDraftPO po = toPO(draft);
        if (draft.getId() == null) {
            if (draftMapper.insert(po) != 1 || po.getId() == null) {
                throw new IllegalStateException("product image draft insert failed");
            }
        } else if (draftMapper.update(po, new LambdaUpdateWrapper<ProductImageDraftPO>()
                .eq(ProductImageDraftPO::getTenantId, tenantId.getValue())
                .eq(ProductImageDraftPO::getId, po.getId())) != 1) {
            throw new AggregateVersionConflictException();
        }
        draft.onPersisted(new ProductImageDraftId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return draft;
    }

    @Override
    public Optional<ProductImageDraft> find(TenantId tenantId, ProductImageDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(draftMapper.selectOne(new LambdaQueryWrapper<ProductImageDraftPO>()
                .eq(ProductImageDraftPO::getTenantId, tenantId.getValue())
                .eq(ProductImageDraftPO::getId, draftId.getValue()))).map(ProductImageDraftRepositoryImpl::toDomain);
    }

    @Override
    public Optional<ProductImageDraft> findForUpdate(TenantId tenantId, ProductImageDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) return Optional.empty();
        return Optional.ofNullable(draftMapper.selectOne(new LambdaQueryWrapper<ProductImageDraftPO>()
                .eq(ProductImageDraftPO::getTenantId, tenantId.getValue())
                .eq(ProductImageDraftPO::getId, draftId.getValue()).last("FOR UPDATE")))
                .map(ProductImageDraftRepositoryImpl::toDomain);
    }

    @Override
    public Optional<ProductImageDraft> findForStagedFile(TenantId tenantId, AttachmentFileId fileId) {
        requireTenant(tenantId);
        if (fileId == null) return Optional.empty();
        ProductImageDraftFilePO staged = fileMapper.selectOne(new LambdaQueryWrapper<ProductImageDraftFilePO>()
                .eq(ProductImageDraftFilePO::getTenantId, tenantId.getValue())
                .eq(ProductImageDraftFilePO::getFileId, fileId.getValue())
                .eq(ProductImageDraftFilePO::getState, "STAGED"));
        return staged == null ? Optional.empty()
                : find(tenantId, new ProductImageDraftId(staged.getDraftId()));
    }

    @Override
    public void stage(TenantId tenantId, ProductImageDraftId draftId, ProductImageType imageType,
                      AttachmentFileId fileId, int ordinal) {
        requireTenant(tenantId);
        if (draftId == null || imageType == null || fileId == null || ordinal < 0 || ordinal > 8) {
            throw new IllegalArgumentException("product image draft candidate is invalid");
        }
        ProductImageDraftFilePO po = new ProductImageDraftFilePO();
        po.setTenantId(tenantId.getValue());
        po.setDraftId(draftId.getValue());
        po.setImageType(imageType.name());
        po.setFileId(fileId.getValue());
        po.setOrdinal(ordinal);
        po.setState("STAGED");
        if (fileMapper.insert(po) != 1) {
            throw new IllegalStateException("product image draft candidate insert failed");
        }
    }

    @Override
    public List<StagedFile> findStagedFiles(TenantId tenantId, ProductImageDraftId draftId) {
        return staged(tenantId, draftId, false);
    }

    @Override
    public List<StagedFile> findStagedFilesForUpdate(TenantId tenantId, ProductImageDraftId draftId) {
        return staged(tenantId, draftId, true);
    }

    @Override
    public void markStagedBound(TenantId tenantId, ProductImageDraftId draftId, List<StagedFile> values) {
        requireTenant(tenantId);
        if (draftId == null || values == null || values.isEmpty()) {
            throw new IllegalArgumentException("product image draft binding is invalid");
        }
        for (StagedFile value : values) {
            if (value == null || value.imageType() == null || value.file() == null || value.file().getId() == null
                    || fileMapper.update(null, new LambdaUpdateWrapper<ProductImageDraftFilePO>()
                    .set(ProductImageDraftFilePO::getState, "BOUND")
                    .eq(ProductImageDraftFilePO::getTenantId, tenantId.getValue())
                    .eq(ProductImageDraftFilePO::getDraftId, draftId.getValue())
                    .eq(ProductImageDraftFilePO::getImageType, value.imageType().name())
                    .eq(ProductImageDraftFilePO::getFileId, value.file().getId().getValue())
                    .eq(ProductImageDraftFilePO::getState, "STAGED")) != 1) {
                throw new IllegalStateException("product image draft was already consumed");
            }
        }
    }

    @Override
    public void withdrawStagedFiles(TenantId tenantId, ProductImageDraftId draftId) {
        requireTenant(tenantId);
        if (draftId == null) throw new IllegalArgumentException("product image draft is required");
        fileMapper.update(null, new LambdaUpdateWrapper<ProductImageDraftFilePO>()
                .set(ProductImageDraftFilePO::getState, "WITHDRAWN")
                .eq(ProductImageDraftFilePO::getTenantId, tenantId.getValue())
                .eq(ProductImageDraftFilePO::getDraftId, draftId.getValue())
                .eq(ProductImageDraftFilePO::getState, "STAGED"));
    }

    private List<StagedFile> staged(TenantId tenantId, ProductImageDraftId draftId, boolean forUpdate) {
        requireTenant(tenantId);
        if (draftId == null) return List.of();
        List<ProductImageDraftFilePO> rows = fileMapper.selectList(new LambdaQueryWrapper<ProductImageDraftFilePO>()
                .eq(ProductImageDraftFilePO::getTenantId, tenantId.getValue())
                .eq(ProductImageDraftFilePO::getDraftId, draftId.getValue())
                .eq(ProductImageDraftFilePO::getState, "STAGED")
                .orderByAsc(ProductImageDraftFilePO::getImageType, ProductImageDraftFilePO::getOrdinal)
                .last(forUpdate ? "FOR UPDATE" : ""));
        Map<Long, AttachmentFile> files = files(tenantId, rows.stream().map(ProductImageDraftFilePO::getFileId)
                .collect(Collectors.toSet()), forUpdate);
        return rows.stream().map(value -> new StagedFile(ProductImageType.valueOf(value.getImageType()),
                files.get(value.getFileId()), value.getOrdinal())).peek(value -> {
            if (value.file() == null) throw new IllegalStateException("stored product image candidate is incomplete");
        }).toList();
    }

    private Map<Long, AttachmentFile> files(TenantId tenantId, Set<Long> ids, boolean forUpdate) {
        if (ids.isEmpty()) return Map.of();
        return attachmentFileMapper.selectList(new LambdaQueryWrapper<AttachmentFilePO>()
                .eq(AttachmentFilePO::getTenantId, tenantId.getValue())
                .in(AttachmentFilePO::getId, ids).last(forUpdate ? "FOR UPDATE" : "")).stream()
                .map(attachmentConverter::toDomain).collect(Collectors.toMap(
                        value -> value.getId().getValue(), Function.identity()));
    }

    private static ProductImageDraftPO toPO(ProductImageDraft value) {
        ProductImageDraftPO po = new ProductImageDraftPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setUploaderActorId(value.getUploaderMemberId().getValue());
        po.setDeadline(value.getDeadline());
        po.setState(value.getState().name());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    private static ProductImageDraft toDomain(ProductImageDraftPO po) {
        return ProductImageDraft.reconstitute(new ProductImageDraft.Snapshot(
                new ProductImageDraftId(po.getId()), new TenantId(po.getTenantId()),
                new MemberId(po.getUploaderActorId()), po.getDeadline(),
                ProductImageDraftState.valueOf(po.getState()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }

    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("product image draft tenant is required");
    }
}
