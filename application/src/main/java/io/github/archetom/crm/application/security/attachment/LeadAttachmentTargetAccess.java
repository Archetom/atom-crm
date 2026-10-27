package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.springframework.stereotype.Component;

@Component
public class LeadAttachmentTargetAccess extends CrmAttachmentTargetAccess {
    public LeadAttachmentTargetAccess(CrmAttachmentTargetAccessSupport support) {
        super(support, AttachmentTargetType.LEAD);
    }
}
