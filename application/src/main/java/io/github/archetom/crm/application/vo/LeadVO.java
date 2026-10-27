package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LeadVO(
        Long id,
        Long ownerActorId,
        Long customerId,
        Long convertedContactId,
        Long parentLeadId,
        String name,
        String phoneNumber,
        String telephone,
        String jobTitle,
        String email,
        String website,
        String description,
        LocalDateTime nextContactTime,
        String status,
        Boolean locked,
        String dealStatus,
        LocalDateTime dealTime,
        List<String> tags,
        List<LeadAddressVO> addresses,
        List<LeadCollaboratorVO> collaborators,
        Map<String, List<String>> customFields,
        boolean starred,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime lastActivityTime,
        String lastActivityContent) {
}
