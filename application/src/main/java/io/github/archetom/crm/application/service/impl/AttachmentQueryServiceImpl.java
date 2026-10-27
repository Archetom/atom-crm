package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccess;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.service.AttachmentQueryService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AttachmentUploadStatusVO;
import io.github.archetom.crm.application.vo.AttachmentVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.MarketingActivityImageType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentQueryServiceImpl implements AttachmentQueryService {
    private final AttachmentFileRepository fileRepository;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final AttachmentApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<AttachmentUploadStatusVO> uploadStatus(AuthenticatedCaller caller, String targetType,
                                                          Long targetId, Long fileIdValue) {
        // Status needs an exact uploader and current collaboration predicate; the short command
        // transaction is read-only in behavior but obtains the required target/file lock safely.
        return commandTemplate.execute(UseCaseOperation.ATTACHMENT_UPLOAD_STATUS_GET,
                new ServiceOperation<>() {
                    private AttachmentTarget target;
                    private AttachmentFileId fileId;
                    private TenantId tenantId;
                    @Override public void validate() {
                        target = support.target(targetType, targetId);
                        fileId = support.fileId(fileIdValue);
                        tenantId = organizationAccessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.FILES_WRITE);
                    }
                    @Override public AttachmentUploadStatusVO execute() {
                        var lifecycle = targetAccess(target).lockForAttachmentMutation(caller, target,
                                io.github.archetom.crm.application.security.attachment.AttachmentTargetOperation.STATUS);
                        if (!tenantId.sameValueAs(lifecycle.tenantId()) || !lifecycle.attachmentsPermitted()) {
                            throw support.notFound();
                        }
                        AttachmentFile file = fileRepository.findByTargetForUpdate(tenantId, target, fileId)
                                .orElseThrow(support::notFound);
                        if (!file.isUploadedBy(new MemberId(caller.actorId()))) {
                            throw support.notFound();
                        }
                        return support.status(file);
                    }
                });
    }

    @Override
    public Result<Pager<AttachmentVO>> query(AuthenticatedCaller caller, String targetType,
                                              Long targetId, Integer pageValue, Integer sizeValue) {
        return query(caller, targetType, targetId, pageValue, sizeValue, null);
    }

    @Override
    public Result<Pager<AttachmentVO>> query(AuthenticatedCaller caller, String targetType,
                                              Long targetId, Integer pageValue, Integer sizeValue,
                                              String marketingActivityImageTypeValue) {
        return query(caller, targetType, targetId, pageValue, sizeValue,
                marketingActivityImageTypeValue, null);
    }

    @Override
    public Result<Pager<AttachmentVO>> query(AuthenticatedCaller caller, String targetType,
                                              Long targetId, Integer pageValue, Integer sizeValue,
                                              String marketingActivityImageTypeValue,
                                              String productImageTypeValue) {
        return queryTemplate.execute(UseCaseOperation.ATTACHMENT_QUERY, new ServiceOperation<>() {
            private AttachmentTarget target;
            private TenantId tenantId;
            private MarketingActivityImageType marketingActivityImageType;
            private io.github.archetom.crm.domain.model.ProductImageType productImageType;
            private long page;
            private long size;
            @Override public void validate() {
                target = support.target(targetType, targetId);
                marketingActivityImageType = support.marketingActivityImageTypeForQuery(target,
                        marketingActivityImageTypeValue);
                productImageType = support.productImageTypeForQuery(target, productImageTypeValue);
                if (marketingActivityImageType != null && productImageType != null) {
                    throw support.invalid("attachment image groups are mutually exclusive");
                }
                page = pageValue == null ? 1 : pageValue;
                size = sizeValue == null ? 20 : sizeValue;
                if (page < 1 || size < 1 || size > 100) throw support.invalid("attachment page is invalid");
                tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
            }
            @Override public Pager<AttachmentVO> execute() {
                TenantId targetTenant = targetAccess(target).authorizeRead(caller, target);
                if (!tenantId.sameValueAs(targetTenant)) throw support.notFound();
                PageResult<AttachmentFile> result = fileRepository.findAvailablePage(tenantId, target, page, size,
                        marketingActivityImageType, productImageType);
                Pager<AttachmentVO> pager = new Pager<>();
                pager.setPageNum(result.page());
                pager.setPageSize(result.size());
                pager.setTotalNum(result.total());
                pager.setObjectList(result.items().stream().map(support::toVO).toList());
                return pager;
            }
        });
    }

    @Override
    public Result<AttachmentVO> get(AuthenticatedCaller caller, String targetType, Long targetId,
                                    Long fileIdValue) {
        return queryTemplate.execute(UseCaseOperation.ATTACHMENT_GET, new ServiceOperation<>() {
            private AttachmentTarget target;
            private AttachmentFileId fileId;
            private TenantId tenantId;
            @Override public void validate() {
                target = support.target(targetType, targetId);
                fileId = support.fileId(fileIdValue);
                tenantId = organizationAccessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
            }
            @Override public AttachmentVO execute() {
                TenantId targetTenant = targetAccess(target).authorizeRead(caller, target);
                if (!tenantId.sameValueAs(targetTenant)) throw support.notFound();
                return fileRepository.findAvailableByTarget(tenantId, target, fileId)
                        .map(support::toVO).orElseThrow(support::notFound);
            }
        });
    }

    private AttachmentTargetAccess targetAccess(AttachmentTarget target) {
        try { return targetAccessRegistry.require(target.type()); }
        catch (UnsupportedOperationException exception) { throw support.invalid("attachment target is unsupported"); }
    }
}
