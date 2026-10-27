package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record SavedViewVO(Long id, String objectType, String name,
                          List<SavedViewConditionVO> conditions, int sortOrder,
                          boolean hidden, boolean defaultView, Long version,
                          LocalDateTime createdTime, LocalDateTime updatedTime) {
}
