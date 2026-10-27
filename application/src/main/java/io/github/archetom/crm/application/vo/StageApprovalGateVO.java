package io.github.archetom.crm.application.vo;

import java.util.List;

public record StageApprovalGateVO(String status, Long approvalInstanceId,
                                  List<ApprovalStartSelectionVO> approverSelections) {
}
