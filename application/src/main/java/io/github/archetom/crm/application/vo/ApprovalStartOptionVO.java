package io.github.archetom.crm.application.vo;

import java.util.List;

public record ApprovalStartOptionVO(Long definitionId, String name,
                                    List<ApprovalStartSelectionVO> selections) {
}
