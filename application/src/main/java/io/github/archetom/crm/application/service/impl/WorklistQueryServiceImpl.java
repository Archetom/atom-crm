package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.service.WorklistQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.WorklistItemVO;
import io.github.archetom.crm.application.vo.WorklistPageVO;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.WorklistSource;
import io.github.archetom.crm.domain.model.WorklistSourceRecord;
import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.domain.model.WorkTaskWorklistRecord;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.repository.WorklistSourceRepository;
import io.github.archetom.crm.domain.repository.WorklistAcknowledgementRepository;
import io.github.archetom.crm.domain.repository.WorkTaskRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A local, bounded worklist derived from source records and current access. */
@Service
@RequiredArgsConstructor
public class WorklistQueryServiceImpl implements WorklistQueryService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SOURCE_SCAN_SIZE = 100;
    private static final int MAX_SOURCE_CANDIDATES_PER_PAGE = 500;
    private static final int MAX_CURSOR_LENGTH = 2_048;
    private static final String CURSOR_VERSION = "v3";
    private static final String LEGACY_CURSOR_VERSION = "v2";
    private static final LocalDate LAST_QUERYABLE_DATE = LocalDate.of(9999, 12, 30);
    private static final Comparator<WorklistItemVO> ITEM_ORDER = Comparator
            .comparing(WorklistItemVO::dueTime)
            .thenComparing(WorklistItemVO::kind)
            .thenComparing(WorklistItemVO::sourceId);

    private final OrganizationAccessGuard organizationAccessGuard;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final ReturnVisitAccessResolver returnVisitAccessResolver;
    private final ReturnVisitApplicationSupport returnVisitSupport;
    private final ActivityRepository activityRepository;
    private final ReturnVisitRepository returnVisitRepository;
    private final WorklistSourceRepository sourceRepository;
    private final WorkTaskRepository workTaskRepository;
    private final WorklistAcknowledgementRepository acknowledgementRepository;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<WorklistPageVO> query(AuthenticatedCaller caller, LocalDate date, String cursor, Integer size) {
        return queryTemplate.execute(UseCaseOperation.WORKLIST_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private LocalDate day;
            private WorklistCursor after;
            private int pageSize;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(caller,
                        AuthorityCatalog.WORKLIST_READ);
                day = date == null ? LocalDate.now() : date;
                if (day.getYear() < 1000 || day.isAfter(LAST_QUERYABLE_DATE)) {
                    throw invalid("worklist date is invalid");
                }
                pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
                if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
                    throw invalid("worklist page size is invalid");
                }
                after = cursor(cursor, day);
            }

            @Override
            public WorklistPageVO execute() {
                LocalDateTime from = day.atStartOfDay();
                LocalDateTime until = day.plusDays(1).atStartOfDay();
                int sourceLimit = pageSize + 1;
                EnumMap<CursorSource, SourceBatch> batches = new EnumMap<>(CursorSource.class);
                batches.put(CursorSource.ACTIVITY, activityBatch(caller, tenantId, from, until,
                        after.position(CursorSource.ACTIVITY), sourceLimit));
                batches.put(CursorSource.RETURN_VISIT, returnVisitBatch(caller, tenantId, from, until,
                        after.position(CursorSource.RETURN_VISIT), sourceLimit));
                batches.put(CursorSource.WORK_TASK, workTaskBatch(caller, tenantId, from, until,
                        after.position(CursorSource.WORK_TASK), sourceLimit));
                for (CursorSource source : CursorSource.values()) {
                    if (source.projectionSource() != null) {
                        batches.put(source, projectionBatch(caller, tenantId, from, until,
                                after.position(source), sourceLimit, source));
                    }
                }

                List<WorklistItemVO> merged = batches.values().stream()
                        .flatMap(batch -> batch.items().stream()).collect(java.util.stream.Collectors.toCollection(
                                ArrayList::new));
                WorklistItemVO frontier = batches.entrySet().stream()
                        .filter(entry -> !entry.getValue().completeForPage())
                        .map(entry -> frontier(entry.getValue().scanned(), entry.getKey().kind()))
                        .min(ITEM_ORDER).orElse(null);
                if (frontier != null) {
                    merged.removeIf(item -> ITEM_ORDER.compare(item, frontier) > 0);
                }
                merged.sort(ITEM_ORDER);
                Set<WorklistItemFingerprint> acknowledged = acknowledgementRepository.findAcknowledged(
                        tenantId, new MemberId(caller.actorId()), merged.stream()
                                .map(WorklistQueryServiceImpl::fingerprint).toList());
                List<WorklistItemVO> consumed = new ArrayList<>();
                List<WorklistItemVO> page = new ArrayList<>(pageSize);
                for (WorklistItemVO item : merged) {
                    consumed.add(item);
                    if (!acknowledged.contains(fingerprint(item))) page.add(item);
                    if (page.size() == pageSize) break;
                }

                EnumMap<CursorSource, SourceCursor> positions = new EnumMap<>(CursorSource.class);
                batches.forEach((source, batch) -> positions.put(source, advance(
                        after.position(source), batch, consumed, source.kind())));
                WorklistCursor next = new WorklistCursor(positions);
                return new WorklistPageVO(List.copyOf(page),
                        next.exhausted() ? null : encode(day, next));
            }
        });
    }

    private SourceBatch activityBatch(AuthenticatedCaller caller, TenantId tenantId,
                                      LocalDateTime from, LocalDateTime until,
                                      SourceCursor after, int itemLimit) {
        if (!caller.hasAuthority(AuthorityCatalog.ACTIVITIES_READ) || after.exhausted()) {
            return SourceBatch.exhausted(after);
        }
        SourceCursor scannedCursor = after;
        List<WorklistItemVO> items = new ArrayList<>(itemLimit);
        int scanned = 0;
        boolean exhausted = false;
        while (items.size() < itemLimit && scanned < MAX_SOURCE_CANDIDATES_PER_PAGE) {
            List<Activity> page = activityRepository.findDue(tenantId, from, until,
                    scannedCursor.time(), scannedCursor.id(), SOURCE_SCAN_SIZE);
            if (page.isEmpty()) {
                exhausted = true;
                break;
            }
            scanned += page.size();
            List<Activity> visible = visibleActivities(caller, tenantId, page);
            int remaining = itemLimit - items.size();
            if (visible.size() >= remaining) {
                List<Activity> selected = visible.subList(0, remaining);
                selected.stream().map(WorklistQueryServiceImpl::activityItem).forEach(items::add);
                Activity last = selected.getLast();
                scannedCursor = SourceCursor.after(last.getNextContactTime(), last.getId().getValue());
                break;
            }
            visible.stream().map(WorklistQueryServiceImpl::activityItem).forEach(items::add);
            Activity last = page.getLast();
            scannedCursor = SourceCursor.after(last.getNextContactTime(), last.getId().getValue());
            if (page.size() < SOURCE_SCAN_SIZE) {
                exhausted = true;
                break;
            }
        }
        return new SourceBatch(List.copyOf(items), scannedCursor, exhausted,
                exhausted || items.size() >= itemLimit);
    }

    private SourceBatch returnVisitBatch(AuthenticatedCaller caller, TenantId tenantId,
                                         LocalDateTime from, LocalDateTime until,
                                         SourceCursor after, int itemLimit) {
        if (!caller.hasAuthority(AuthorityCatalog.RETURN_VISITS_READ) || after.exhausted()) {
            return SourceBatch.exhausted(after);
        }
        try {
            ReturnVisitAccessResolver.ReturnVisitAccess access = returnVisitAccessResolver.access(caller,
                    AuthorityCatalog.RETURN_VISITS_READ, ReturnVisitAccessMode.READ);
            Map<FieldCode, EffectiveFieldAccess> fields = returnVisitSupport.accesses(
                    tenantId, access.memberId());
            returnVisitSupport.requireReadableUnmasked(fields, ReturnVisitFieldCatalog.NUMBER);
            returnVisitSupport.requireReadableUnmasked(fields, ReturnVisitFieldCatalog.VISIT_TIME);
            List<ReturnVisit> values = returnVisitRepository.findScheduled(tenantId, access.visibility(),
                    from, until, after.time(), after.id(), itemLimit);
            SourceCursor scanned = values.isEmpty() ? after : SourceCursor.after(
                    values.getLast().getVisitTime(), values.getLast().getId().getValue());
            boolean exhausted = values.size() < itemLimit;
            return new SourceBatch(values.stream().map(WorklistQueryServiceImpl::returnVisitItem).toList(),
                    scanned, exhausted, true);
        } catch (NonRetryableApplicationException exception) {
            return SourceBatch.exhausted(after);
        }
    }

    private SourceBatch projectionBatch(AuthenticatedCaller caller, TenantId tenantId,
                                        LocalDateTime from, LocalDateTime until,
                                        SourceCursor after, int itemLimit, CursorSource source) {
        if (!caller.hasAuthority(source.authority()) || after.exhausted()
                || !fieldsVisible(tenantId, caller, source)) {
            return SourceBatch.exhausted(after);
        }
        SourceCursor scannedCursor = after;
        List<WorklistItemVO> items = new ArrayList<>(itemLimit);
        int scanned = 0;
        boolean exhausted = false;
        while (items.size() < itemLimit && scanned < MAX_SOURCE_CANDIDATES_PER_PAGE) {
            List<WorklistSourceRecord> page = sourceRepository.findDue(tenantId,
                    new MemberId(caller.actorId()), source.projectionSource(), from, until,
                    scannedCursor.time(), scannedCursor.id(), SOURCE_SCAN_SIZE);
            if (page.isEmpty()) {
                exhausted = true;
                break;
            }
            scanned += page.size();
            List<WorklistSourceRecord> visible = visibleRecords(caller, tenantId, page);
            int remaining = itemLimit - items.size();
            if (visible.size() >= remaining) {
                List<WorklistSourceRecord> selected = visible.subList(0, remaining);
                selected.stream().map(value -> projectionItem(source, value)).forEach(items::add);
                WorklistSourceRecord last = selected.getLast();
                scannedCursor = SourceCursor.after(last.dueTime(), last.sourceId());
                break;
            }
            visible.stream().map(value -> projectionItem(source, value)).forEach(items::add);
            WorklistSourceRecord last = page.getLast();
            scannedCursor = SourceCursor.after(last.dueTime(), last.sourceId());
            if (page.size() < SOURCE_SCAN_SIZE) {
                exhausted = true;
                break;
            }
        }
        return new SourceBatch(List.copyOf(items), scannedCursor, exhausted,
                exhausted || items.size() >= itemLimit);
    }

    private SourceBatch workTaskBatch(
            AuthenticatedCaller caller, TenantId tenantId,
            LocalDateTime from, LocalDateTime until,
            SourceCursor after, int itemLimit) {
        if (!caller.hasAuthority(AuthorityCatalog.WORK_TASKS_READ) || after.exhausted()) {
            return SourceBatch.exhausted(after);
        }
        List<WorkTaskWorklistRecord> values = workTaskRepository.findDueForAssignee(
                tenantId, new MemberId(caller.actorId()), from, until,
                after.time(), after.id(), itemLimit);
        SourceCursor scanned = values.isEmpty() ? after : SourceCursor.after(
                values.getLast().dueTime(), values.getLast().taskId().getValue());
        boolean exhausted = values.size() < itemLimit;
        return new SourceBatch(values.stream().map(WorklistQueryServiceImpl::workTaskItem).toList(),
                scanned, exhausted, true);
    }

    private boolean fieldsVisible(TenantId tenantId, AuthenticatedCaller caller, CursorSource source) {
        if (source.objectType() == null) {
            return true;
        }
        try {
            Map<FieldCode, EffectiveFieldAccess> accesses = fieldAccessResolver.resolve(
                    tenantId, new MemberId(caller.actorId()), source.objectType());
            return readable(accesses, source.dueField()) && readable(accesses, source.summaryField());
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean readable(Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.getOrDefault(new FieldCode(code),
                EffectiveFieldAccess.hidden());
        return access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private List<Activity> visibleActivities(AuthenticatedCaller caller, TenantId tenantId,
                                              List<Activity> activities) {
        Map<BusinessObjectType, Set<Long>> visible = visible(caller, tenantId,
                activities.stream().collect(java.util.stream.Collectors.groupingBy(
                        Activity::getSubjectType, () -> new EnumMap<>(BusinessObjectType.class),
                        java.util.stream.Collectors.mapping(Activity::getSubjectId,
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new)))));
        return activities.stream().filter(value -> visible.getOrDefault(value.getSubjectType(), Set.of())
                .contains(value.getSubjectId())).toList();
    }

    private List<WorklistSourceRecord> visibleRecords(AuthenticatedCaller caller, TenantId tenantId,
                                                       List<WorklistSourceRecord> records) {
        Map<BusinessObjectType, Set<Long>> visible = visible(caller, tenantId,
                records.stream().collect(java.util.stream.Collectors.groupingBy(
                        WorklistSourceRecord::subjectType, () -> new EnumMap<>(BusinessObjectType.class),
                        java.util.stream.Collectors.mapping(WorklistSourceRecord::subjectId,
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new)))));
        boolean oaSummaryVisible = oaSummaryVisible(caller, tenantId, records);
        return records.stream().filter(value -> visible.getOrDefault(value.subjectType(), Set.of())
                .contains(value.subjectId()))
                .filter(value -> value.subjectType() != BusinessObjectType.OA_REQUEST
                        || oaSummaryVisible).toList();
    }

    private boolean oaSummaryVisible(
            AuthenticatedCaller caller, TenantId tenantId,
            Collection<WorklistSourceRecord> records) {
        if (records.stream().noneMatch(value ->
                value.subjectType() == BusinessObjectType.OA_REQUEST)) {
            return true;
        }
        try {
            return readable(fieldAccessResolver.resolve(tenantId,
                            new MemberId(caller.actorId()), BusinessObjectType.OA_REQUEST),
                    OaRequestFieldCatalog.REQUEST_TITLE);
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private Map<BusinessObjectType, Set<Long>> visible(
            AuthenticatedCaller caller, TenantId tenantId,
            Map<BusinessObjectType, ? extends Set<Long>> requested) {
        Map<BusinessObjectType, Set<Long>> result = new EnumMap<>(BusinessObjectType.class);
        requested.forEach((type, ids) -> {
            try {
                result.put(type, recordAccessGuard.visibleSubjectIds(caller, tenantId, type, ids));
            } catch (NonRetryableApplicationException ignored) {
                result.put(type, Set.of());
            }
        });
        return result;
    }

    private SourceCursor advance(SourceCursor current, SourceBatch batch,
                                 List<WorklistItemVO> page, String kind) {
        List<WorklistItemVO> emitted = page.stream().filter(item -> kind.equals(item.kind())).toList();
        if (emitted.size() == batch.items().size()) {
            return batch.exhausted() ? SourceCursor.exhaustedCursor() : batch.scanned();
        }
        if (!emitted.isEmpty()) {
            WorklistItemVO last = emitted.getLast();
            return SourceCursor.after(last.dueTime(), last.sourceId());
        }
        if (batch.items().isEmpty()) {
            return batch.exhausted() ? SourceCursor.exhaustedCursor() : batch.scanned();
        }
        return current;
    }

    private WorklistItemVO frontier(SourceCursor cursor, String kind) {
        if (cursor == null || cursor.time() == null || cursor.id() == null) {
            throw new IllegalStateException("worklist source frontier is unavailable");
        }
        return new WorklistItemVO(kind, cursor.id(), 0L, "", 1L, cursor.time(), "");
    }

    private static WorklistItemVO activityItem(Activity value) {
        return new WorklistItemVO(CursorSource.ACTIVITY.kind(), value.getId().getValue(),
                value.getVersion(), value.getSubjectType().name(), value.getSubjectId(),
                value.getNextContactTime(), value.getPurpose() == null
                ? value.getCategory() : value.getPurpose());
    }

    private static WorklistItemVO returnVisitItem(ReturnVisit value) {
        return new WorklistItemVO(CursorSource.RETURN_VISIT.kind(), value.getId().getValue(),
                value.getVersion(), BusinessObjectType.RETURN_VISIT.name(),
                value.getId().getValue(), value.getVisitTime(), value.getVisitNumber());
    }

    private static WorklistItemVO workTaskItem(WorkTaskWorklistRecord value) {
        return new WorklistItemVO(CursorSource.WORK_TASK.kind(), value.taskId().getValue(),
                value.sourceVersion(), "WORK_TASK", value.taskId().getValue(),
                value.dueTime(), value.summary());
    }

    private WorklistItemVO projectionItem(CursorSource source, WorklistSourceRecord value) {
        return new WorklistItemVO(source.kind(), value.sourceId(), value.sourceVersion(),
                value.subjectType().name(), value.subjectId(), value.dueTime(), value.summary());
    }

    private static WorklistItemFingerprint fingerprint(WorklistItemVO value) {
        return new WorklistItemFingerprint(value.kind(), value.sourceId(), value.sourceVersion(),
                value.dueTime());
    }

    private WorklistCursor cursor(String token, LocalDate day) {
        if (token == null || token.isBlank()) {
            return WorklistCursor.start();
        }
        if (token.length() > MAX_CURSOR_LENGTH) {
            throw invalid("worklist cursor is invalid");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            boolean current = CURSOR_VERSION.equals(parts[0])
                    && parts.length == CursorSource.values().length + 2;
            boolean legacy = LEGACY_CURSOR_VERSION.equals(parts[0])
                    && parts.length == CursorSource.values().length + 1;
            if ((!current && !legacy) || !day.equals(LocalDate.parse(parts[1]))) {
                throw new IllegalArgumentException();
            }
            EnumMap<CursorSource, SourceCursor> positions = new EnumMap<>(CursorSource.class);
            CursorSource[] sources = CursorSource.values();
            int encodedSources = legacy ? sources.length - 1 : sources.length;
            for (int index = 0; index < encodedSources; index++) {
                positions.put(sources[index], sourceCursor(parts[index + 2], day));
            }
            if (legacy) {
                positions.put(CursorSource.WORK_TASK, SourceCursor.exhaustedCursor());
            }
            return new WorklistCursor(positions);
        } catch (RuntimeException exception) {
            throw invalid("worklist cursor is invalid");
        }
    }

    private SourceCursor sourceCursor(String value, LocalDate day) {
        if ("S".equals(value)) return SourceCursor.start();
        if ("E".equals(value)) return SourceCursor.exhaustedCursor();
        String[] parts = value.split(",", -1);
        if (parts.length != 3 || !"A".equals(parts[0])) throw new IllegalArgumentException();
        LocalDateTime time = LocalDateTime.parse(parts[1]);
        long id = Long.parseLong(parts[2]);
        if (id <= 0 || time.isBefore(day.atStartOfDay())
                || !time.isBefore(day.plusDays(1).atStartOfDay())) {
            throw new IllegalArgumentException();
        }
        return SourceCursor.after(time, id);
    }

    private String encode(LocalDate day, WorklistCursor cursor) {
        List<String> parts = new ArrayList<>(CursorSource.values().length + 2);
        parts.add(CURSOR_VERSION);
        parts.add(day.toString());
        for (CursorSource source : CursorSource.values()) {
            parts.add(sourceToken(cursor.position(source)));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.join("|", parts).getBytes(StandardCharsets.UTF_8));
    }

    private String sourceToken(SourceCursor cursor) {
        if (cursor.exhausted()) return "E";
        if (cursor.time() == null) return "S";
        return "A," + cursor.time() + "," + cursor.id();
    }

    private NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    private enum CursorSource {
        ACTIVITY(null, "FOLLOW_UP", AuthorityCatalog.ACTIVITIES_READ, null, null, null),
        RETURN_VISIT(null, "RETURN_VISIT", AuthorityCatalog.RETURN_VISITS_READ, null, null, null),
        CONTACT(WorklistSource.CONTACT, "CONTACT_FOLLOW_UP", AuthorityCatalog.CONTACTS_READ,
                BusinessObjectType.CONTACT, ContactFieldCatalog.NEXT_CONTACT_TIME, ContactFieldCatalog.NAME),
        LEAD(WorklistSource.LEAD, "LEAD_FOLLOW_UP", AuthorityCatalog.LEADS_READ,
                BusinessObjectType.LEAD, LeadFieldCatalog.NEXT_CONTACT_TIME, LeadFieldCatalog.NAME),
        OPPORTUNITY(WorklistSource.OPPORTUNITY, "OPPORTUNITY_FOLLOW_UP",
                AuthorityCatalog.OPPORTUNITIES_READ, BusinessObjectType.OPPORTUNITY,
                OpportunityFieldCatalog.NEXT_CONTACT_TIME, OpportunityFieldCatalog.NAME),
        RECEIVABLE_PLAN(WorklistSource.RECEIVABLE_PLAN, "RECEIVABLE_PLAN_REMINDER",
                AuthorityCatalog.RECEIVABLE_PLANS_READ, BusinessObjectType.RECEIVABLE_PLAN,
                ReceivablePlanFieldCatalog.DUE_DATE, ReceivablePlanFieldCatalog.NUMBER),
        CONTRACT(WorklistSource.CONTRACT, "CONTRACT_EXPIRY", AuthorityCatalog.CONTRACTS_READ,
                BusinessObjectType.CONTRACT, ContractFieldCatalog.END_DATE, ContractFieldCatalog.NUMBER),
        APPROVAL(WorklistSource.APPROVAL, "APPROVAL_PENDING", AuthorityCatalog.APPROVALS_READ,
                null, null, null),
        WORK_TASK(null, "WORK_TASK", AuthorityCatalog.WORK_TASKS_READ,
                null, null, null);

        private final WorklistSource projectionSource;
        private final String kind;
        private final String authority;
        private final BusinessObjectType objectType;
        private final String dueField;
        private final String summaryField;

        CursorSource(WorklistSource projectionSource, String kind, String authority,
                     BusinessObjectType objectType, String dueField, String summaryField) {
            this.projectionSource = projectionSource;
            this.kind = kind;
            this.authority = authority;
            this.objectType = objectType;
            this.dueField = dueField;
            this.summaryField = summaryField;
        }

        private WorklistSource projectionSource() { return projectionSource; }
        private String kind() { return kind; }
        private String authority() { return authority; }
        private BusinessObjectType objectType() { return objectType; }
        private String dueField() { return dueField; }
        private String summaryField() { return summaryField; }
    }

    private record WorklistCursor(Map<CursorSource, SourceCursor> positions) {
        private WorklistCursor {
            EnumMap<CursorSource, SourceCursor> copy = new EnumMap<>(CursorSource.class);
            if (positions != null) copy.putAll(positions);
            for (CursorSource source : CursorSource.values()) {
                if (!copy.containsKey(source) || copy.get(source) == null) {
                    throw new IllegalArgumentException("worklist cursor source is missing");
                }
            }
            positions = Map.copyOf(copy);
        }

        private static WorklistCursor start() {
            EnumMap<CursorSource, SourceCursor> positions = new EnumMap<>(CursorSource.class);
            for (CursorSource source : CursorSource.values()) positions.put(source, SourceCursor.start());
            return new WorklistCursor(positions);
        }

        private SourceCursor position(CursorSource source) { return positions.get(source); }

        private boolean exhausted() {
            return positions.values().stream().allMatch(SourceCursor::exhausted);
        }
    }

    private record SourceCursor(LocalDateTime time, Long id, boolean exhausted) {
        private SourceCursor {
            if (exhausted && (time != null || id != null) || !exhausted && (time == null) != (id == null)
                    || id != null && id <= 0) {
                throw new IllegalArgumentException("worklist source cursor is invalid");
            }
        }

        private static SourceCursor start() { return new SourceCursor(null, null, false); }
        private static SourceCursor after(LocalDateTime time, Long id) {
            return new SourceCursor(time, id, false);
        }
        private static SourceCursor exhaustedCursor() { return new SourceCursor(null, null, true); }
    }

    private record SourceBatch(List<WorklistItemVO> items, SourceCursor scanned,
                               boolean exhausted, boolean completeForPage) {
        private static SourceBatch exhausted(SourceCursor cursor) {
            return new SourceBatch(List.of(), cursor.exhausted() ? cursor : SourceCursor.exhaustedCursor(),
                    true, true);
        }
    }
}
