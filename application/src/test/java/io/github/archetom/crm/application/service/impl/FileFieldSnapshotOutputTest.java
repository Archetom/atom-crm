package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.valueobject.FieldCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FileFieldSnapshotOutputTest {

    @Test
    void suppressesApprovalAndStageFileMetadataWithoutFilesRead() {
        AttachmentApplicationSupport attachmentSupport = mock(AttachmentApplicationSupport.class);
        FileFieldSnapshotOutput output = new FileFieldSnapshotOutput(attachmentSupport);
        FieldCode field = new FieldCode("cf_evidence");

        assertTrue(output.visible(Map.of(field, List.of()), Map.of(), false).isEmpty());
        assertTrue(output.visibleByStage(Map.of("review", Map.of(field, List.of())), Map.of(), false).isEmpty());
        verifyNoInteractions(attachmentSupport);
    }

    @Test
    void retainsCapturedEmptyStageMarkerOnlyForFilesReadCallers() {
        FileFieldSnapshotOutput output = new FileFieldSnapshotOutput(mock(AttachmentApplicationSupport.class));

        assertEquals(Map.of("review", Map.of()), output.visibleByStage(
                Map.of("review", Map.of()), Map.of(), true));
    }
}
