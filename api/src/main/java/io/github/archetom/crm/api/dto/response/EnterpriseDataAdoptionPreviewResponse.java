package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Ephemeral source preview and non-value diff indicators for a specific Customer target. */
@Data
@Accessors(chain = true)
public class EnterpriseDataAdoptionPreviewResponse {
    private EnterpriseDataObservationResponse observation;
    private String previewReference;
    private List<DiffResponse> diffs;

    @Override public String toString() { return "EnterpriseDataAdoptionPreviewResponse[redacted]"; }

    @Data
    @Accessors(chain = true)
    public static class DiffResponse {
        private String fact;
        private String targetLocator;
        private boolean changed;
    }
}
