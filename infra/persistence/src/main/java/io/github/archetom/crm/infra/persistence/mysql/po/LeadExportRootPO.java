package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

/** Fixed Lead CSV root projection; it never crosses the persistence boundary. */
@Data
public class LeadExportRootPO {
    private Long leadId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String jobTitle;
    private String email;
    private String website;
    private String description;
    private LocalDateTime nextContactTime;
    private Boolean locked;
    private String dealStatus;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
