package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.application.export.CrmImportErrorReportDocument;
import io.github.archetom.crm.application.importer.CrmImportCsvParser;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.AttachmentTransferService;
import io.github.archetom.crm.application.service.ContactCreationCoordinator;
import io.github.archetom.crm.application.service.CrmImportDraftService;
import io.github.archetom.crm.application.service.LeadCreationCoordinator;
import io.github.archetom.crm.application.service.ProductCreationCoordinator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CrmImportConfirmVO;
import io.github.archetom.crm.application.vo.CrmImportDraftVO;
import io.github.archetom.crm.application.vo.CrmImportHistoryItemVO;
import io.github.archetom.crm.application.vo.CrmImportHistoryPageVO;
import io.github.archetom.crm.application.vo.CrmImportPreviewVO;
import io.github.archetom.crm.application.vo.CrmImportTemplateVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.CrmImportDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.CrmImportDraftState;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.CrmImportDraftRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.CrmImportDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;

/**
 * Owns the import state machine. CSV bytes are never copied into a request DTO, database column,
 * log, or response: parsing reads a clean, private controlled attachment in the request stack.
 */
@Service
@RequiredArgsConstructor
public class CrmImportDraftServiceImpl implements CrmImportDraftService {
    private static final long HISTORY_MAX_SIZE = 50;
    private static final long HISTORY_RETENTION_DAYS = 7;
    private static final Duration SOURCE_RETENTION = Duration.ofDays(HISTORY_RETENTION_DAYS);
    private final CrmImportDraftRepository draftRepository;
    private final AttachmentFileRepository fileRepository;
    private final OrganizationTenantRepository tenantRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AttachmentTransferService transferService;
    private final CrmImportDraftSourceCleanup sourceCleanup;
    private final LeadCreationCoordinator leadCreationCoordinator;
    private final ContactCreationCoordinator contactCreationCoordinator;
    private final ProductCreationCoordinator productCreationCoordinator;
    private final ProductRepository productRepository;
    private final CrmImportCsvParser parser;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;
    private final Clock clock;

    @Override
    public Result<CrmImportDraftVO> create(AuthenticatedCaller caller, String rawObjectType) {
        return commandTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private CrmImportObjectType objectType;

            @Override public void validate() {
                objectType = objectType(rawObjectType);
                tenantId = requireObjectWrite(caller, objectType);
                accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
                memberId = new MemberId(caller.actorId());
            }

            @Override public CrmImportDraftVO execute() {
                Instant now = clock.instant();
                CrmImportDraft draft = CrmImportDraft.create(tenantId, memberId, objectType,
                        local(now.plus(SOURCE_RETENTION)), local(now));
                draftRepository.save(tenantId, draft);
                return draft(draft);
            }
        });
    }

    @Override
    public Result<CrmImportTemplateVO> template(AuthenticatedCaller caller, String rawObjectType) {
        return queryTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_TEMPLATE, new ServiceOperation<>() {
            private CrmImportObjectType objectType;
            @Override public void validate() {
                objectType = objectType(rawObjectType);
                requireObjectWrite(caller, objectType);
            }
            @Override public CrmImportTemplateVO execute() {
                return new CrmImportTemplateVO(objectType.name(), parser.template(objectType));
            }
        });
    }

    @Override
    public Result<CrmImportHistoryPageVO> history(
            AuthenticatedCaller caller, String rawObjectType, Long rawPage, Long rawSize) {
        return queryTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_HISTORY, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private CrmImportObjectType objectType;
            private long page;
            private long size;

            @Override public void validate() {
                objectType = objectType(rawObjectType);
                tenantId = requireObjectWrite(caller, objectType);
                memberId = new MemberId(caller.actorId());
                page = rawPage == null ? 1 : rawPage;
                size = rawSize == null ? 20 : rawSize;
                if (page < 1 || size < 1 || size > HISTORY_MAX_SIZE) {
                    throw invalid("CRM import history page is invalid");
                }
            }

            @Override public CrmImportHistoryPageVO execute() {
                LocalDateTime completedSince = local(clock.instant().minusSeconds(
                        HISTORY_RETENTION_DAYS * 24 * 60 * 60));
                PageResult<CrmImportDraft> values = draftRepository.findCompletedByUploader(
                        tenantId, memberId, objectType, completedSince, page, size);
                return new CrmImportHistoryPageVO(values.page(), values.size(), values.total(),
                        values.items().stream().map(value -> new CrmImportHistoryItemVO(
                                value.getId().getValue(), value.getUpdatedTime(), value.getImportedRows())).toList());
            }
        });
    }

    @Override
    public Result<CrmImportPreviewVO> preview(AuthenticatedCaller caller, Long rawDraftId) {
        Result<Source> admitted = source(caller, rawDraftId, UseCaseOperation.CRM_IMPORT_DRAFT_PREVIEW);
        if (!admitted.isSuccess()) return copyFailure(admitted);
        Source source = admitted.getData();
        if (source.file() == null) return success(preview(source.draft(), "AWAITING_UPLOAD", 0, List.of()));
        if (!source.file().isAvailable()) return success(preview(source.draft(), "PROCESSING_UPLOAD", 0, List.of()));
        Result<CrmImportCsvParser.Parsed> parsed = parse(caller, source, UseCaseOperation.CRM_IMPORT_DRAFT_PREVIEW);
        if (!parsed.isSuccess()) return copyFailure(parsed);
        CrmImportCsvParser.Parsed value = parsed.getData();
        return success(preview(source.draft(), value.valid() ? "READY" : "INVALID", value.validRows(),
                value.issues().stream().map(issue -> new CrmImportPreviewVO.IssueVO(issue.rowNumber(), issue.errCode()))
                        .toList()));
    }

    @Override
    public Result<CrmImportErrorReportDocument> errorReport(AuthenticatedCaller caller, Long rawDraftId) {
        Result<Source> admitted = source(caller, rawDraftId, UseCaseOperation.CRM_IMPORT_DRAFT_ERROR_REPORT);
        if (!admitted.isSuccess()) return copyFailure(admitted);
        Source source = admitted.getData();
        if (source.file() == null || !source.file().isAvailable()) {
            return reject(UseCaseOperation.CRM_IMPORT_DRAFT_ERROR_REPORT, "CRM import source is not ready");
        }
        Result<CrmImportCsvParser.Parsed> parsed = parse(caller, source, UseCaseOperation.CRM_IMPORT_DRAFT_ERROR_REPORT);
        if (!parsed.isSuccess()) return copyFailure(parsed);
        if (parsed.getData().issues().isEmpty()) {
            return reject(UseCaseOperation.CRM_IMPORT_DRAFT_ERROR_REPORT, "CRM import has no errors");
        }
        return success(errorReport(parsed.getData().issues()));
    }

    @Override
    public Result<CrmImportConfirmVO> confirm(AuthenticatedCaller caller, Long rawDraftId, Long version) {
        Result<Optional<CrmImportConfirmVO>> replay = replay(caller, rawDraftId, version);
        if (!replay.isSuccess()) return copyFailure(replay);
        if (replay.getData().isPresent()) return success(replay.getData().get());

        Result<Source> admitted = source(caller, rawDraftId, UseCaseOperation.CRM_IMPORT_DRAFT_CONFIRM);
        if (!admitted.isSuccess()) return copyFailure(admitted);
        Source source = admitted.getData();
        if (source.file() == null || !source.file().isAvailable()) return reject(UseCaseOperation.CRM_IMPORT_DRAFT_CONFIRM,
                "CRM import source is not ready");
        Result<CrmImportCsvParser.Parsed> parsed = parse(caller, source, UseCaseOperation.CRM_IMPORT_DRAFT_CONFIRM);
        if (!parsed.isSuccess()) return copyFailure(parsed);
        if (!parsed.getData().valid()) return reject(UseCaseOperation.CRM_IMPORT_DRAFT_CONFIRM,
                "CRM import preview is invalid");

        return commandTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_CONFIRM, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public CrmImportConfirmVO execute() {
                // Taken here rather than in validate(): the tenant lock and the FOR UPDATE reads
                // hold only for the current transaction, and validate() runs before one is open —
                // so acquiring them there releases them immediately and leaves the state transition
                // below unserialized. The sibling source() and replay() operations already read
                // inside execute() for the same reason.
                Context context = current(caller, rawDraftId, true);
                if (version == null || version < 0 || !version.equals(context.draft().getVersion())) {
                    throw conflict();
                }
                if (!context.draft().isActiveFor(context.memberId(), local(clock.instant()))) throw missing();
                if (context.file() == null || !context.file().getId().sameValueAs(source.file().getId())
                        || !context.file().isAvailable()) throw invalid("CRM import source is not ready");
                CrmImportCsvParser.Parsed rows = parsed.getData();
                int importedRows = importRows(context.tenantId(), caller, context.draft().getObjectType(), rows);
                Instant now = clock.instant();
                context.draft().consume(context.memberId(), importedRows, local(now));
                sourceCleanup.withdraw(context.tenantId(), context.target(), context.file(), now);
                draftRepository.save(context.tenantId(), context.draft());
                return new CrmImportConfirmVO(context.draft().getId().getValue(),
                        context.draft().getObjectType().name(), "COMPLETED", importedRows,
                        context.draft().getVersion());
            }
        });
    }

    @Override
    public Result<Void> withdraw(AuthenticatedCaller caller, Long rawDraftId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_WITHDRAW, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public Void execute() {
                // Read under the transaction so the locks actually hold for the state transition;
                // see the note in confirm().
                Context context = current(caller, rawDraftId, true);
                if (version == null || version < 0 || !version.equals(context.draft().getVersion())) throw conflict();
                if (!context.draft().isActiveFor(context.memberId(), local(clock.instant()))) throw missing();
                Instant now = clock.instant();
                context.draft().withdraw(context.memberId(), local(now));
                if (context.file() != null) sourceCleanup.withdraw(context.tenantId(), context.target(), context.file(), now);
                draftRepository.save(context.tenantId(), context.draft());
                return null;
            }
        });
    }

    private Result<Source> source(AuthenticatedCaller caller, Long rawDraftId, UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public Source execute() {
                Context context = current(caller, rawDraftId, true);
                if (!context.draft().isActiveFor(context.memberId(), local(clock.instant()))) throw missing();
                return new Source(context.draft(), context.file());
            }
        });
    }

    private Result<CrmImportCsvParser.Parsed> parse(AuthenticatedCaller caller, Source source,
                                                     UseCaseOperation operation) {
        Result<AttachmentTransferService.Download> opened = transferService.openDownload(caller, "import-draft",
                source.draft().getId().getValue(), source.file().getId().getValue(), null);
        if (!opened.isSuccess()) return copyFailure(opened);
        try (var handle = opened.getData().handle(); InputStream input = handle.inputStream()) {
            return success(parser.parse(source.draft().getObjectType(), input, opened.getData().totalBytes()));
        } catch (Exception exception) {
            return reject(operation, "CRM import source cannot be parsed");
        }
    }

    private Result<Optional<CrmImportConfirmVO>> replay(AuthenticatedCaller caller, Long rawDraftId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CRM_IMPORT_DRAFT_CONFIRM, new ServiceOperation<>() {
            @Override public void validate() { }
            @Override public Optional<CrmImportConfirmVO> execute() {
                Context context = current(caller, rawDraftId, false);
                if (!context.memberId().sameValueAs(context.draft().getUploaderMemberId())) throw missing();
                if (context.draft().getState() == CrmImportDraftState.CONSUMED && version != null && version >= 0
                        && context.draft().getVersion() == version + 1) {
                    return Optional.of(new CrmImportConfirmVO(context.draft().getId().getValue(),
                            context.draft().getObjectType().name(), "COMPLETED", context.draft().getImportedRows(),
                            context.draft().getVersion()));
                }
                return Optional.empty();
            }
        });
    }

    /** Locks tenant -> import root -> attachment relation/file, matching the shared B24 ordering. */
    private Context current(AuthenticatedCaller caller, Long rawDraftId, boolean forUpdate) {
        CrmImportDraftId draftId = draftId(rawDraftId);
        TenantId tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        if (!tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("CRM import tenant is unavailable");
        }
        // Re-read under a share lock now that the tenant root is serialized: the check above ran
        // against the transaction's earlier snapshot and would still admit a member deactivated
        // between that read and this lock.
        accessGuard.requireActiveMembershipForShare(caller, AuthorityCatalog.FILES_WRITE);
        CrmImportDraft draft = (forUpdate ? draftRepository.findForUpdate(tenantId, draftId)
                : draftRepository.find(tenantId, draftId)).orElseThrow(CrmImportDraftServiceImpl::missing);
        TenantId objectTenant = requireObjectWrite(caller, draft.getObjectType());
        MemberId memberId = new MemberId(caller.actorId());
        if (!tenantId.sameValueAs(objectTenant) || !memberId.sameValueAs(draft.getUploaderMemberId())) throw missing();
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.IMPORT_DRAFT, draftId.getValue());
        List<AttachmentFile> files = fileRepository.findActiveByTargetForUpdate(tenantId, target, 2);
        if (files.size() > 1) throw invalid("CRM import source is invalid");
        return new Context(tenantId, memberId, draft, target, files.isEmpty() ? null : files.getFirst());
    }

    /** Product import follows the reference default: duplicate active product codes are skipped, never overwritten. */
    private int importRows(TenantId tenantId, AuthenticatedCaller caller, CrmImportObjectType objectType,
                           CrmImportCsvParser.Parsed rows) {
        return switch (objectType) {
            case LEAD -> {
                rows.leads().forEach(request -> leadCreationCoordinator.createInCurrentTransaction(caller, request));
                yield rows.leads().size();
            }
            case CONTACT -> {
                rows.contacts().forEach(request -> contactCreationCoordinator.createInCurrentTransaction(caller, request));
                yield rows.contacts().size();
            }
            case PRODUCT -> importProducts(tenantId, caller, rows.products());
        };
    }

    private int importProducts(TenantId tenantId, AuthenticatedCaller caller,
                               List<ProductCreateRequest> requests) {
        var acceptedCodeKeys = new HashSet<String>();
        int imported = 0;
        for (ProductCreateRequest request : requests) {
            String codeKey = IdentifierKeyNormalizer.normalize(request.getCode());
            if (!acceptedCodeKeys.add(codeKey) || productRepository.existsActiveByCode(tenantId, codeKey)) {
                continue;
            }
            productCreationCoordinator.createInCurrentTransaction(caller, request);
            imported++;
        }
        return imported;
    }

    private TenantId requireObjectWrite(AuthenticatedCaller caller, CrmImportObjectType objectType) {
        return accessGuard.requireActiveMembership(caller, switch (objectType) {
            case LEAD -> AuthorityCatalog.LEADS_WRITE;
            case CONTACT -> AuthorityCatalog.CONTACTS_WRITE;
            case PRODUCT -> AuthorityCatalog.PRODUCTS_WRITE;
        });
    }

    private static CrmImportObjectType objectType(String raw) {
        try { return CrmImportObjectType.valueOf(raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (RuntimeException exception) { throw invalid("CRM import object type is invalid"); }
    }

    private static CrmImportDraftId draftId(Long value) {
        try { return new CrmImportDraftId(value); }
        catch (RuntimeException exception) { throw missing(); }
    }

    private static CrmImportDraftVO draft(CrmImportDraft value) {
        return new CrmImportDraftVO(value.getId().getValue(), value.getObjectType().name(),
                value.getState().name(), value.getVersion(), value.getDeadline(), value.getImportedRows());
    }

    private static CrmImportPreviewVO preview(CrmImportDraft draft, String status, int validRows,
                                              List<CrmImportPreviewVO.IssueVO> issues) {
        return new CrmImportPreviewVO(draft.getId().getValue(), draft.getObjectType().name(), status,
                validRows, List.copyOf(issues));
    }

    private static CrmImportErrorReportDocument errorReport(List<CrmImportCsvParser.Issue> issues) {
        StringBuilder content = new StringBuilder("\uFEFFrowNumber,errCode\r\n");
        for (CrmImportCsvParser.Issue issue : issues) {
            content.append(issue.rowNumber()).append(',').append(issue.errCode()).append("\r\n");
        }
        return new CrmImportErrorReportDocument(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC).withNano(value.getNano() / 1_000 * 1_000);
    }

    private <T> Result<T> reject(UseCaseOperation operation, String detail) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            @Override public void validate() { throw invalid(detail); }
            @Override public T execute() { throw new IllegalStateException(); }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> Result<T> copyFailure(Result<?> value) { return (Result<T>) value; }

    private static <T> Result<T> success(T value) {
        Result<T> result = new Result<>(); result.setSuccess(true); result.setData(value); return result;
    }

    private static NonRetryableApplicationException invalid(String detail) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, detail);
    }

    private static NonRetryableApplicationException conflict() {
        return new NonRetryableApplicationException(ApplicationErrorCode.VERSION_CONFLICT,
                "CRM import draft version does not match");
    }

    private static NonRetryableApplicationException missing() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "CRM import draft is unavailable");
    }

    private record Source(CrmImportDraft draft, AttachmentFile file) { }
    private record Context(TenantId tenantId, MemberId memberId, CrmImportDraft draft,
                           AttachmentTarget target, AttachmentFile file) { }
}
