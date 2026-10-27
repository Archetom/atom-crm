package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OaAnnouncementDomainException;
import io.github.archetom.crm.domain.model.OaAnnouncementAudienceType;
import io.github.archetom.crm.domain.model.OaAnnouncementDeliveryStatistics;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxItem;
import io.github.archetom.crm.domain.model.OaAnnouncementInboxQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementQuery;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.model.OaAnnouncementSummary;
import io.github.archetom.crm.domain.repository.OaAnnouncementRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.OaAnnouncementPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementAudienceDepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementAudienceMemberMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementReadReceiptMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaAnnouncementRecipientMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceDepartmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceMemberPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementReadReceiptPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OaAnnouncementRepositoryImpl implements OaAnnouncementRepository {

    private static final int INSERT_CHUNK = 500;

    private final OaAnnouncementMapper announcementMapper;
    private final OaAnnouncementAudienceDepartmentMapper departmentMapper;
    private final OaAnnouncementAudienceMemberMapper memberMapper;
    private final OaAnnouncementRecipientMapper recipientMapper;
    private final OaAnnouncementReadReceiptMapper receiptMapper;
    private final OaAnnouncementPOConverter converter;

    @Override
    public OaAnnouncement save(TenantId tenantId, OaAnnouncement announcement) {
        requireTenantMatch(tenantId, announcement);
        OaAnnouncementPO po = converter.toPO(announcement);
        try {
            if (po.getId() == null) {
                if (announcementMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "OA announcement insert did not affect exactly one row");
                }
            } else if (announcementMapper.update(po,
                    new LambdaUpdateWrapper<OaAnnouncementPO>()
                            .eq(OaAnnouncementPO::getTenantId, tenantId.getValue())
                            .eq(OaAnnouncementPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            OaAnnouncementId announcementId = new OaAnnouncementId(po.getId());
            synchronizeAudience(tenantId, announcementId, announcement);
            announcement.onPersisted(announcementId, po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return announcement;
        } catch (DuplicateKeyException exception) {
            throw new OaAnnouncementDomainException(
                    "OA announcement conflicts with existing data");
        }
    }

    @Override
    public Optional<OaAnnouncement> findById(
            TenantId tenantId, OaAnnouncementId announcementId) {
        requireTenant(tenantId);
        if (announcementId == null) {
            return Optional.empty();
        }
        return hydrateOne(tenantId, announcementMapper.selectOne(
                tenantFilter(tenantId).eq(
                        OaAnnouncementPO::getId, announcementId.getValue())));
    }

    @Override
    public Optional<OaAnnouncement> findByIdForUpdate(
            TenantId tenantId, OaAnnouncementId announcementId) {
        requireTenant(tenantId);
        if (announcementId == null) {
            return Optional.empty();
        }
        return hydrateOne(tenantId, announcementMapper.selectOne(
                tenantFilter(tenantId)
                        .eq(OaAnnouncementPO::getId, announcementId.getValue())
                        .last("FOR UPDATE")));
    }

    @Override
    public PageResult<OaAnnouncementSummary> findPage(
            TenantId tenantId, OaAnnouncementQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("OA announcement query is required");
        }
        LambdaQueryWrapper<OaAnnouncementPO> filter = tenantFilter(tenantId);
        if (query.status() == null) {
            filter.ne(OaAnnouncementPO::getStatus,
                    OaAnnouncementStatus.DELETED.name());
        } else {
            filter.eq(OaAnnouncementPO::getStatus, query.status().name());
        }
        IPage<OaAnnouncementPO> page = announcementMapper.selectPage(
                new Page<>(query.page(), query.size()), filter
                        .orderByDesc(OaAnnouncementPO::getUpdatedTime)
                        .orderByDesc(OaAnnouncementPO::getId));
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0, page.getTotal()), page.getRecords().stream()
                        .map(converter::toSummary).toList());
    }

    @Override
    public long countByStatus(TenantId tenantId, OaAnnouncementStatus status) {
        requireTenant(tenantId);
        if (status == null) {
            throw new IllegalArgumentException("OA announcement status is required");
        }
        return announcementMapper.selectCount(tenantFilter(tenantId)
                .eq(OaAnnouncementPO::getStatus, status.name()));
    }

    @Override
    public Map<OaAnnouncementId, OaAnnouncementDeliveryStatistics> findStatistics(
            TenantId tenantId, Collection<OaAnnouncementId> announcementIds) {
        requireTenant(tenantId);
        List<Long> ids = values(announcementIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return announcementMapper.selectStatistics(tenantId.getValue(), ids).stream()
                .collect(Collectors.toMap(
                        value -> new OaAnnouncementId(value.getAnnouncementId()),
                        value -> new OaAnnouncementDeliveryStatistics(
                                value.getRecipientTotal(), value.getReadTotal()),
                        (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public List<MemberId> resolveActiveRecipientIds(
            TenantId tenantId, OaAnnouncementId announcementId, int limit) {
        requireTenant(tenantId);
        if (announcementId == null || limit < 1 || limit > 10_001) {
            throw new IllegalArgumentException(
                    "OA announcement recipient resolution is invalid");
        }
        return announcementMapper.selectActiveRecipientIds(
                        tenantId.getValue(), announcementId.getValue(), limit).stream()
                .map(MemberId::new).toList();
    }

    @Override
    public void saveRecipientSnapshot(
            TenantId tenantId, OaAnnouncementId announcementId,
            Collection<MemberId> recipientIds, LocalDateTime deliveredTime) {
        requireTenant(tenantId);
        if (announcementId == null || recipientIds == null || recipientIds.isEmpty()
                || deliveredTime == null) {
            throw new IllegalArgumentException(
                    "OA announcement recipient snapshot is invalid");
        }
        List<Long> ids = recipientIds.stream().map(MemberId::getValue).distinct().toList();
        for (int start = 0; start < ids.size(); start += INSERT_CHUNK) {
            List<Long> chunk = ids.subList(start, Math.min(start + INSERT_CHUNK, ids.size()));
            if (recipientMapper.insertBatch(tenantId.getValue(),
                    announcementId.getValue(), chunk, deliveredTime) != chunk.size()) {
                throw new IllegalStateException(
                        "OA announcement recipient insert count is invalid");
            }
        }
    }

    @Override
    public boolean hasRecipientForDeliveryAdmission(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId) {
        requireTenant(tenantId);
        if (announcementId == null || memberId == null) return false;
        return recipientMapper.selectForDeliveryAdmission(tenantId.getValue(), announcementId.getValue(),
                memberId.getValue()) != null;
    }

    @Override
    public boolean hasRecipientSnapshot(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId) {
        requireTenant(tenantId);
        if (announcementId == null || memberId == null) return false;
        return recipientMapper.selectSnapshot(tenantId.getValue(), announcementId.getValue(),
                memberId.getValue()) != null;
    }

    @Override
    public Optional<OaAnnouncementInboxItem> findInboxItem(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId) {
        return findInboxItem(tenantId, announcementId, memberId, false);
    }

    @Override
    public Optional<OaAnnouncementInboxItem> findInboxItemForShare(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId) {
        return findInboxItem(tenantId, announcementId, memberId, true);
    }

    @Override
    public PageResult<OaAnnouncementInboxItem> findInboxPage(
            TenantId tenantId, OaAnnouncementInboxQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException(
                    "OA announcement inbox query is required");
        }
        QueryWrapper<OaAnnouncementPO> filter = recipientVisible(
                tenantId, query.memberId())
                .orderByDesc("published_time").orderByDesc("id");
        IPage<OaAnnouncementPO> page = announcementMapper.selectPage(
                new Page<>(query.page(), query.size()), filter);
        Map<Long, LocalDateTime> readTimes = readTimes(
                tenantId, query.memberId(), page.getRecords().stream()
                        .map(OaAnnouncementPO::getId).toList());
        List<OaAnnouncementInboxItem> items = page.getRecords().stream()
                .map(value -> inboxItem(value, readTimes.get(value.getId()))).toList();
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0, page.getTotal()), items);
    }

    @Override
    public long countUnread(TenantId tenantId, MemberId memberId) {
        requireTenant(tenantId);
        if (memberId == null) {
            throw new IllegalArgumentException(
                    "OA announcement recipient is required");
        }
        return Math.max(0, announcementMapper.countUnread(
                tenantId.getValue(), memberId.getValue()));
    }

    @Override
    public Optional<LocalDateTime> findReadTime(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId) {
        requireTenant(tenantId);
        if (announcementId == null || memberId == null) {
            return Optional.empty();
        }
        OaAnnouncementReadReceiptPO receipt = receiptMapper.selectOne(receiptFilter(
                tenantId, announcementId, memberId));
        return Optional.ofNullable(receipt == null ? null : receipt.getReadTime());
    }

    @Override
    public LocalDateTime saveReadReceiptIfAbsent(
            TenantId tenantId, OaAnnouncementId announcementId,
            MemberId memberId, LocalDateTime readTime) {
        requireTenant(tenantId);
        if (announcementId == null || memberId == null || readTime == null) {
            throw new IllegalArgumentException(
                    "OA announcement read receipt is invalid");
        }
        int affected = receiptMapper.insertIfAbsent(
                tenantId.getValue(), announcementId.getValue(),
                memberId.getValue(), readTime);
        if (affected < 0 || affected > 1) {
            throw new IllegalStateException(
                    "OA announcement read receipt upsert count is invalid");
        }
        OaAnnouncementReadReceiptPO persisted = receiptMapper.selectOne(
                receiptFilter(tenantId, announcementId, memberId).last("FOR SHARE"));
        if (persisted == null || persisted.getReadTime() == null) {
            throw new IllegalStateException(
                    "OA announcement read receipt was not persisted");
        }
        return persisted.getReadTime();
    }

    private Optional<OaAnnouncementInboxItem> findInboxItem(
            TenantId tenantId, OaAnnouncementId announcementId,
            MemberId memberId, boolean lock) {
        requireTenant(tenantId);
        if (announcementId == null || memberId == null) {
            return Optional.empty();
        }
        QueryWrapper<OaAnnouncementPO> query = recipientVisible(tenantId, memberId)
                .eq("id", announcementId.getValue());
        if (lock) {
            query.last("FOR SHARE");
        }
        OaAnnouncementPO root = announcementMapper.selectOne(query);
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(inboxItem(root,
                findReadTime(tenantId, announcementId, memberId).orElse(null)));
    }

    private OaAnnouncementInboxItem inboxItem(
            OaAnnouncementPO root, LocalDateTime readTime) {
        return new OaAnnouncementInboxItem(
                new OaAnnouncementId(root.getId()), root.getTitle(), root.getBody(),
                root.getPublishedTime(), readTime);
    }

    private QueryWrapper<OaAnnouncementPO> recipientVisible(
            TenantId tenantId, MemberId memberId) {
        requireTenant(tenantId);
        if (memberId == null) {
            throw new IllegalArgumentException(
                    "OA announcement recipient is required");
        }
        return new QueryWrapper<OaAnnouncementPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", OaAnnouncementStatus.PUBLISHED.name())
                .apply("EXISTS (SELECT 1 FROM oa_announcement_recipient recipient"
                                + " INNER JOIN organization_membership membership"
                                + " ON membership.tenant_id = recipient.tenant_id"
                                + " AND membership.actor_id = recipient.actor_id"
                                + " AND membership.status = 'ACTIVE'"
                                + " WHERE recipient.tenant_id = oa_announcement.tenant_id"
                                + " AND recipient.announcement_id = oa_announcement.id"
                                + " AND recipient.actor_id = {0})",
                        memberId.getValue());
    }

    private Map<Long, LocalDateTime> readTimes(
            TenantId tenantId, MemberId memberId, Collection<Long> announcementIds) {
        if (announcementIds == null || announcementIds.isEmpty()) {
            return Map.of();
        }
        return receiptMapper.selectList(
                        new LambdaQueryWrapper<OaAnnouncementReadReceiptPO>()
                                .eq(OaAnnouncementReadReceiptPO::getTenantId,
                                        tenantId.getValue())
                                .eq(OaAnnouncementReadReceiptPO::getActorId,
                                        memberId.getValue())
                                .in(OaAnnouncementReadReceiptPO::getAnnouncementId,
                                        announcementIds)).stream()
                .collect(Collectors.toMap(
                        OaAnnouncementReadReceiptPO::getAnnouncementId,
                        OaAnnouncementReadReceiptPO::getReadTime));
    }

    private Optional<OaAnnouncement> hydrateOne(
            TenantId tenantId, OaAnnouncementPO root) {
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(hydrate(tenantId, List.of(root)).getFirst());
    }

    private List<OaAnnouncement> hydrate(
            TenantId tenantId, Collection<OaAnnouncementPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roots.stream().map(OaAnnouncementPO::getId).toList();
        Map<Long, List<OaAnnouncementAudienceDepartmentPO>> departments =
                departmentMapper.selectList(
                                new LambdaQueryWrapper<OaAnnouncementAudienceDepartmentPO>()
                                        .eq(OaAnnouncementAudienceDepartmentPO::getTenantId,
                                                tenantId.getValue())
                                        .in(OaAnnouncementAudienceDepartmentPO::getAnnouncementId,
                                                ids)).stream()
                        .collect(Collectors.groupingBy(
                                OaAnnouncementAudienceDepartmentPO::getAnnouncementId));
        Map<Long, List<OaAnnouncementAudienceMemberPO>> members =
                memberMapper.selectList(
                                new LambdaQueryWrapper<OaAnnouncementAudienceMemberPO>()
                                        .eq(OaAnnouncementAudienceMemberPO::getTenantId,
                                                tenantId.getValue())
                                        .in(OaAnnouncementAudienceMemberPO::getAnnouncementId,
                                                ids)).stream()
                        .collect(Collectors.groupingBy(
                                OaAnnouncementAudienceMemberPO::getAnnouncementId));
        return roots.stream().map(root -> converter.toDomain(
                root, departments.getOrDefault(root.getId(), List.of()),
                members.getOrDefault(root.getId(), List.of()))).toList();
    }

    private void synchronizeAudience(
            TenantId tenantId, OaAnnouncementId announcementId,
            OaAnnouncement announcement) {
        departmentMapper.delete(
                new LambdaQueryWrapper<OaAnnouncementAudienceDepartmentPO>()
                        .eq(OaAnnouncementAudienceDepartmentPO::getTenantId,
                                tenantId.getValue())
                        .eq(OaAnnouncementAudienceDepartmentPO::getAnnouncementId,
                                announcementId.getValue()));
        memberMapper.delete(
                new LambdaQueryWrapper<OaAnnouncementAudienceMemberPO>()
                        .eq(OaAnnouncementAudienceMemberPO::getTenantId,
                                tenantId.getValue())
                        .eq(OaAnnouncementAudienceMemberPO::getAnnouncementId,
                                announcementId.getValue()));

        List<Long> departmentIds = announcement.getAudienceSelectors().stream()
                .filter(value -> value.type()
                        == OaAnnouncementAudienceType.DEPARTMENT_SUBTREE)
                .map(value -> value.departmentId().getValue()).toList();
        insertChunks(departmentIds, chunk -> departmentMapper.insertBatch(
                tenantId.getValue(), announcementId.getValue(), chunk));
        List<Long> memberIds = announcement.getAudienceSelectors().stream()
                .filter(value -> value.type() == OaAnnouncementAudienceType.MEMBER)
                .map(value -> value.memberId().getValue()).toList();
        insertChunks(memberIds, chunk -> memberMapper.insertBatch(
                tenantId.getValue(), announcementId.getValue(), chunk));
    }

    private void insertChunks(List<Long> ids, Function<List<Long>, Integer> insert) {
        for (int start = 0; start < ids.size(); start += INSERT_CHUNK) {
            List<Long> chunk = ids.subList(start, Math.min(start + INSERT_CHUNK, ids.size()));
            if (insert.apply(chunk) != chunk.size()) {
                throw new IllegalStateException(
                        "OA announcement audience insert count is invalid");
            }
        }
    }

    private LambdaQueryWrapper<OaAnnouncementReadReceiptPO> receiptFilter(
            TenantId tenantId, OaAnnouncementId announcementId, MemberId memberId) {
        return new LambdaQueryWrapper<OaAnnouncementReadReceiptPO>()
                .eq(OaAnnouncementReadReceiptPO::getTenantId, tenantId.getValue())
                .eq(OaAnnouncementReadReceiptPO::getAnnouncementId,
                        announcementId.getValue())
                .eq(OaAnnouncementReadReceiptPO::getActorId, memberId.getValue());
    }

    private LambdaQueryWrapper<OaAnnouncementPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<OaAnnouncementPO>()
                .eq(OaAnnouncementPO::getTenantId, tenantId.getValue());
    }

    private List<Long> values(Collection<OaAnnouncementId> ids) {
        return ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(OaAnnouncementId::getValue).distinct().toList();
    }

    private void requireTenantMatch(
            TenantId tenantId, OaAnnouncement announcement) {
        requireTenant(tenantId);
        if (announcement == null || announcement.getTenantId() == null
                || !tenantId.sameValueAs(announcement.getTenantId())) {
            throw new IllegalArgumentException(
                    "OA announcement tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
    }
}
