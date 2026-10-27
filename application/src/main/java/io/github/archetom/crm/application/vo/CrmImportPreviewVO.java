package io.github.archetom.crm.application.vo;

import java.util.List;

/** Contains only row numbers and reviewed machine codes, never source-cell contents. */
public record CrmImportPreviewVO(Long draftId, String objectType, String status, int validRows,
                                 List<IssueVO> issues) {
    public record IssueVO(int rowNumber, String errCode) { }
}
