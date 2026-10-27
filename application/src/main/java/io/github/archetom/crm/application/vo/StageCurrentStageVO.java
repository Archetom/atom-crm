package io.github.archetom.crm.application.vo;

import java.util.List;

public record StageCurrentStageVO(String code, String name, int order,
                                  List<StageTaskVO> tasks) {
}
