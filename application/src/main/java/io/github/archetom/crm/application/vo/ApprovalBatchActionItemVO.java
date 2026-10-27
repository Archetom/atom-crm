package io.github.archetom.crm.application.vo;

public record ApprovalBatchActionItemVO(Long instanceId, String status, Long version,
                                        String errCode) {
}
