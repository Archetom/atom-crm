package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.MarketingFileDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;

/**
 * Closed upload attribution. Anonymous public bytes name their exact draft, never a member
 * selected by the form owner or request body.
 */
public sealed interface AttachmentUploadOrigin
        permits AttachmentUploadOrigin.Member, AttachmentUploadOrigin.PublicMarketingForm {

    Kind kind();

    enum Kind {
        MEMBER,
        PUBLIC_MARKETING_FORM
    }

    record Member(MemberId memberId) implements AttachmentUploadOrigin {
        public Member {
            if (memberId == null) {
                throw new IllegalArgumentException("attachment member origin is required");
            }
        }
        @Override public Kind kind() { return Kind.MEMBER; }
    }

    record PublicMarketingForm(MarketingFileDraftId draftId) implements AttachmentUploadOrigin {
        public PublicMarketingForm {
            if (draftId == null) {
                throw new IllegalArgumentException("attachment public marketing draft is required");
            }
        }
        @Override public Kind kind() { return Kind.PUBLIC_MARKETING_FORM; }
    }
}
