package io.github.archetom.crm.application.vo;

import java.util.List;

public record WorklistPageVO(List<WorklistItemVO> items, String nextCursor) {
}
