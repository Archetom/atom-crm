package io.github.archetom.crm.application.vo;

import java.util.List;

/** Echoes the evaluated record and every active workflow candidate. */
public record StageWorkflowApplicableVO(String objectType, Long recordId,
                                        List<StageWorkflowApplicableItemVO> workflows) {
}
