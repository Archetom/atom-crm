package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.vo.AttachmentVO;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Re-authorizes immutable FILE snapshots when a workflow response is assembled. */
@Component
@RequiredArgsConstructor
class FileFieldSnapshotOutput {
    private final AttachmentApplicationSupport attachmentSupport;

    Map<String, List<AttachmentVO>> visible(
            Map<FieldCode, List<FileFieldRepository.BoundFile>> values,
            Map<FieldCode, EffectiveFieldAccess> accesses, boolean filesReadable) {
        LinkedHashMap<String, List<AttachmentVO>> result = new LinkedHashMap<>();
        if (!filesReadable || values == null || accesses == null) return Map.of();
        values.forEach((field, files) -> {
            EffectiveFieldAccess access = accesses.get(field);
            if (access == null || !access.accessLevel().covers(FieldAccessLevel.READ)
                    || access.maskType() != FieldMaskType.NONE) return;
            result.put(field.getValue(), files.stream().map(FileFieldRepository.BoundFile::file)
                    .map(attachmentSupport::toVO).toList());
        });
        return Map.copyOf(result);
    }

    Map<String, Map<String, List<AttachmentVO>>> visibleByStage(
            Map<String, Map<FieldCode, List<FileFieldRepository.BoundFile>>> values,
            Map<FieldCode, EffectiveFieldAccess> accesses, boolean filesReadable) {
        LinkedHashMap<String, Map<String, List<AttachmentVO>>> result = new LinkedHashMap<>();
        if (!filesReadable || values == null) return Map.of();
        values.forEach((stage, fields) -> result.put(stage, visible(fields, accesses, true)));
        return Map.copyOf(result);
    }
}
