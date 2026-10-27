package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

/** One-time public draft response. The capability is intentionally not part of any domain state. */
public record PublicMarketingFileDraftVO(Long draftId, List<String> fieldCodes,
                                         String uploadCapability, LocalDateTime expiresAt) {
    public PublicMarketingFileDraftVO {
        fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
    }
}
