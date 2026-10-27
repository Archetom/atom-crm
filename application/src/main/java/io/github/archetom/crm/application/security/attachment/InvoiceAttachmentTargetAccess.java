package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.springframework.stereotype.Component;

@Component
public class InvoiceAttachmentTargetAccess extends CrmAttachmentTargetAccess {
    public InvoiceAttachmentTargetAccess(CrmAttachmentTargetAccessSupport support) {
        super(support, AttachmentTargetType.INVOICE);
    }
}
