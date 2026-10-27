package io.github.archetom.crm.application.vo;

import java.util.List;

/** Server-derived input shape for one active-stage field. */
public record StageFieldInputVO(String inputType, boolean candidatesRequired,
                                String relationObjectType, boolean fileDraftRequired,
                                List<StageAddressPartVO> addressParts,
                                boolean supported, String blockerCode) {
}
