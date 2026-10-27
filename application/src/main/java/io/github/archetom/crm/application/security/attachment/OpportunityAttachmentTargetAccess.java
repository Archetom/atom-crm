package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.springframework.stereotype.Component;

@Component
public class OpportunityAttachmentTargetAccess extends CrmAttachmentTargetAccess {
    public OpportunityAttachmentTargetAccess(CrmAttachmentTargetAccessSupport support) {
        super(support, AttachmentTargetType.OPPORTUNITY);
    }
}
