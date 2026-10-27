package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.OaAnnouncement;
import io.github.archetom.crm.domain.entity.OaAnnouncementAudienceSelector;
import io.github.archetom.crm.domain.model.OaAnnouncementStatus;
import io.github.archetom.crm.domain.model.OaAnnouncementPublicationSafeReason;
import io.github.archetom.crm.domain.model.OaAnnouncementSummary;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaAnnouncementId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceDepartmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceMemberPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementPO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class OaAnnouncementPOConverter {

    public OaAnnouncementSummary toSummary(OaAnnouncementPO root) {
        if (root == null) {
            return null;
        }
        return new OaAnnouncementSummary(
                new OaAnnouncementId(root.getId()),
                new MemberId(root.getCreatedByActorId()),
                root.getTitle(), root.getBody(),
                OaAnnouncementStatus.valueOf(root.getStatus()),
                root.getScheduledPublishTime(), member(root.getScheduledByActorId()),
                reason(root.getLastPublicationSafeReason()),
                root.getPublishedTime(), root.getWithdrawnTime(), root.getVersion(),
                root.getCreatedTime(), root.getUpdatedTime());
    }

    public OaAnnouncement toDomain(
            OaAnnouncementPO root,
            Collection<OaAnnouncementAudienceDepartmentPO> departments,
            Collection<OaAnnouncementAudienceMemberPO> members) {
        if (root == null) {
            return null;
        }
        List<OaAnnouncementAudienceSelector> audience = new ArrayList<>();
        if (Boolean.TRUE.equals(root.getTenantAudience())) {
            audience.add(OaAnnouncementAudienceSelector.tenant());
        }
        if (departments != null) {
            departments.stream()
                    .sorted(Comparator.comparing(
                            OaAnnouncementAudienceDepartmentPO::getDepartmentId))
                    .map(value -> OaAnnouncementAudienceSelector.department(
                            new DepartmentId(value.getDepartmentId())))
                    .forEach(audience::add);
        }
        if (members != null) {
            members.stream()
                    .sorted(Comparator.comparing(
                            OaAnnouncementAudienceMemberPO::getActorId))
                    .map(value -> OaAnnouncementAudienceSelector.member(
                            new MemberId(value.getActorId())))
                    .forEach(audience::add);
        }
        return OaAnnouncement.reconstitute(new OaAnnouncement.Snapshot(
                new OaAnnouncementId(root.getId()),
                new TenantId(root.getTenantId()),
                new MemberId(root.getCreatedByActorId()),
                root.getTitle(), root.getBody(), audience,
                OaAnnouncementStatus.valueOf(root.getStatus()),
                root.getScheduledPublishTime(), member(root.getScheduledByActorId()),
                root.getPublicationGeneration() == null ? 0L : root.getPublicationGeneration(),
                reason(root.getLastPublicationSafeReason()),
                root.getPublishedTime(), root.getWithdrawnTime(),
                root.getVersion(), root.getCreatedTime(), root.getUpdatedTime()));
    }

    public OaAnnouncementPO toPO(OaAnnouncement value) {
        if (value == null) {
            return null;
        }
        OaAnnouncementPO po = new OaAnnouncementPO();
        po.setId(value.getId() == null ? null : value.getId().getValue());
        po.setTenantId(value.getTenantId().getValue());
        po.setCreatedByActorId(value.getCreatedByMemberId().getValue());
        po.setTitle(value.getTitle());
        po.setBody(value.getBody());
        po.setTenantAudience(value.getAudienceSelectors().stream()
                .anyMatch(selector -> selector.type()
                        == io.github.archetom.crm.domain.model.OaAnnouncementAudienceType.TENANT));
        po.setStatus(value.getStatus().name());
        po.setScheduledPublishTime(value.getScheduledPublishTime());
        po.setScheduledByActorId(value.getScheduledByMemberId() == null ? null
                : value.getScheduledByMemberId().getValue());
        po.setPublicationGeneration(value.getPublicationGeneration());
        po.setLastPublicationSafeReason(value.getLastPublicationSafeReason() == null ? null
                : value.getLastPublicationSafeReason().name());
        po.setPublishedTime(value.getPublishedTime());
        po.setWithdrawnTime(value.getWithdrawnTime());
        po.setVersion(value.getVersion());
        po.setCreatedTime(value.getCreatedTime());
        po.setUpdatedTime(value.getUpdatedTime());
        return po;
    }

    private static MemberId member(Long value) {
        return value == null ? null : new MemberId(value);
    }

    private static OaAnnouncementPublicationSafeReason reason(String value) {
        return value == null ? null : OaAnnouncementPublicationSafeReason.valueOf(value);
    }
}
