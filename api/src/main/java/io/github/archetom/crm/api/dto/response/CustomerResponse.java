package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class CustomerResponse {
    private Long id;
    private Long ownerActorId;
    private Long parentCustomerId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String email;
    private String website;
    private String description;
    private String status;
    private Boolean locked;
    private String dealStatus;
    private LocalDateTime dealTime;
    private List<String> tags;
    private List<CustomerAddressResponse> addresses;
    private List<CustomerCollaboratorResponse> collaborators;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Boolean starred;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime lastActivityTime;
    private String lastActivityContent;
}
