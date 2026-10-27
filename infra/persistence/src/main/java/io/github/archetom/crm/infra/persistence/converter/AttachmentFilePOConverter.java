package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentUploadOrigin;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFilePO;
import org.springframework.stereotype.Component;

@Component
public class AttachmentFilePOConverter {

    public AttachmentFilePO toPO(AttachmentFile file) {
        AttachmentFilePO po = new AttachmentFilePO();
        po.setId(file.getId() == null ? null : file.getId().getValue());
        po.setTenantId(file.getTenantId().getValue());
        po.setUploadOrigin(file.getUploadOrigin().kind().name());
        po.setUploaderActorId(file.getUploaderMemberId() == null ? null : file.getUploaderMemberId().getValue());
        po.setPublicMarketingDraftId(file.getUploadOrigin() instanceof AttachmentUploadOrigin.PublicMarketingForm value
                ? value.draftId().getValue() : null);
        po.setDisplayName(file.getDisplayName());
        po.setDeclaredMediaType(file.getDeclaredMediaType());
        po.setExpectedBytes(file.getExpectedBytes());
        po.setExpectedSha256(file.getExpectedSha256());
        po.setByteState(file.getByteState().name());
        po.setInspectionState(file.getInspectionState().name());
        po.setPublicationState(file.getPublicationState().name());
        po.setContentVersion(file.getContentVersion());
        po.setActualBytes(file.getActualBytes());
        po.setMediaType(file.getMediaType());
        po.setControlledReason(file.getControlledReason());
        po.setAvailableTime(file.getAvailableTime());
        po.setVersion(file.getVersion());
        po.setCreatedTime(file.getCreatedTime());
        po.setUpdatedTime(file.getUpdatedTime());
        return po;
    }

    public AttachmentFile toDomain(AttachmentFilePO po) {
        if (po == null) {
            return null;
        }
        return AttachmentFile.reconstitute(new AttachmentFile.Snapshot(
                new AttachmentFileId(po.getId()), new TenantId(po.getTenantId()), origin(po), po.getDisplayName(),
                po.getDeclaredMediaType(), po.getExpectedBytes(), po.getExpectedSha256(),
                AttachmentByteState.valueOf(po.getByteState()),
                AttachmentInspectionState.valueOf(po.getInspectionState()),
                AttachmentPublicationState.valueOf(po.getPublicationState()),
                po.getContentVersion(), po.getActualBytes(), po.getMediaType(),
                po.getControlledReason(), po.getAvailableTime(), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }

    private AttachmentUploadOrigin origin(AttachmentFilePO po) {
        if (AttachmentUploadOrigin.Kind.PUBLIC_MARKETING_FORM.name().equals(po.getUploadOrigin())) {
            return new AttachmentUploadOrigin.PublicMarketingForm(
                    new MarketingFileDraftId(po.getPublicMarketingDraftId()));
        }
        return new AttachmentUploadOrigin.Member(new MemberId(po.getUploaderActorId()));
    }
}
