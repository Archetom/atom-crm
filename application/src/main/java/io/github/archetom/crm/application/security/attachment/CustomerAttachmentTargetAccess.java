package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.springframework.stereotype.Component;

@Component
public class CustomerAttachmentTargetAccess extends CrmAttachmentTargetAccess {
    public CustomerAttachmentTargetAccess(CrmAttachmentTargetAccessSupport support) {
        super(support, AttachmentTargetType.CUSTOMER);
    }
}
