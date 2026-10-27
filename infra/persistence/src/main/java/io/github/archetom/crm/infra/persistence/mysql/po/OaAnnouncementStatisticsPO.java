package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

@Data
public class OaAnnouncementStatisticsPO {
    private Long announcementId;
    private Long recipientTotal;
    private Long readTotal;
}
