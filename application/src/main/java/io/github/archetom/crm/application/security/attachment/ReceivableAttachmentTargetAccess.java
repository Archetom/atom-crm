package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.springframework.stereotype.Component;

@Component
public class ReceivableAttachmentTargetAccess extends CrmAttachmentTargetAccess {
    public ReceivableAttachmentTargetAccess(CrmAttachmentTargetAccessSupport support) {
        super(support, AttachmentTargetType.RECEIVABLE);
    }
}
