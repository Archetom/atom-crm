package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class LeadResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long convertedContactId;
    private Long parentLeadId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String jobTitle;
    private String email;
    private String website;
    private String description;
    private LocalDateTime nextContactTime;
    private String status;
    private Boolean locked;
    private String dealStatus;
    private LocalDateTime dealTime;
    private List<String> tags;
    private List<LeadAddressResponse> addresses;
    private List<LeadCollaboratorResponse> collaborators;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Boolean starred;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime lastActivityTime;
    private String lastActivityContent;
}
