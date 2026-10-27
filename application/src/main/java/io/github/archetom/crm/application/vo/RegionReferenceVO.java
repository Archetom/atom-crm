package io.github.archetom.crm.application.vo;

import lombok.Value;

/** Read-only reference-data item supplied independently from organization state. */
@Value
public class RegionReferenceVO {
    String code;
    String parentCode;
    String name;
    String level;
}
