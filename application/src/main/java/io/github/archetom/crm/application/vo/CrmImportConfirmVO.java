package io.github.archetom.crm.application.vo;

public record CrmImportConfirmVO(Long draftId, String objectType, String status, int importedRows,
                                 Long version) { }
