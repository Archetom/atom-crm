package io.github.archetom.crm.application.vo;

import java.util.List;

public record ApprovalStartOptionsVO(String objectType, Long subjectId,
                                     List<ApprovalStartOptionVO> options) {
}
