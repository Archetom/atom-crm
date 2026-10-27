package io.github.archetom.crm.application.vo;

import java.util.List;

public record ApprovalBatchActionResultVO(boolean atomic, boolean applied,
                                          List<ApprovalBatchActionItemVO> items) {
}
