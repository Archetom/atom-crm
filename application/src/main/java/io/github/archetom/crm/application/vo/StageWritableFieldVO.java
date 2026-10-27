package io.github.archetom.crm.application.vo;

import java.util.List;

/** B05-authorized active-stage field metadata and value projection. */
public record StageWritableFieldVO(String fieldCode, String displayName, String fieldType,
                                   boolean required, List<StageFieldOptionVO> allowedValues,
                                   List<String> currentValues, StageAddressValueVO currentAddress,
                                   StageFieldInputVO input,
                                   boolean masked) {
}
