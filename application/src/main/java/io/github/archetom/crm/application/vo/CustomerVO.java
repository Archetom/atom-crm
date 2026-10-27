package io.github.archetom.crm.application.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CustomerVO(
        Long id,
        Long ownerActorId,
        Long parentCustomerId,
        String name,
        String phoneNumber,
        String telephone,
        String email,
        String website,
        String description,
        String status,
        Boolean locked,
        String dealStatus,
        LocalDateTime dealTime,
        List<String> tags,
        List<CustomerAddressVO> addresses,
        List<CustomerCollaboratorVO> collaborators,
        Map<String, List<String>> customFields,
        boolean starred,
        Long version,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime lastActivityTime,
        String lastActivityContent) {
}
