package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

import java.time.LocalDateTime;

/** Fixed B30a root projection; it never crosses the persistence boundary. */
@Data
public class CustomerExportRootPO {
    private Long customerId;
    private String name;
    private String phoneNumber;
    private String telephone;
    private String email;
    private String website;
    private String description;
    private Boolean locked;
    private String dealStatus;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
