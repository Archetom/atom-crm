package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;

public record FieldDefinitionVO(Long id, String objectType, String code, String displayName,
                                String description, String fieldType, boolean builtin,
                                boolean required, boolean unique, boolean searchable,
                                String status, int displayOrder, String sectionName,
                                List<FieldOptionVO> options, List<FilterOperatorOptionVO> filterOperators,
                                Long version,
                                LocalDateTime createdTime, LocalDateTime updatedTime) {
}
