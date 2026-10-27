package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementStatisticsPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OaAnnouncementMapper extends BaseMapper<OaAnnouncementPO> {

    List<Long> selectActiveRecipientIds(
            @Param("tenantId") Long tenantId,
            @Param("announcementId") Long announcementId,
            @Param("limit") int limit);

    List<OaAnnouncementStatisticsPO> selectStatistics(
            @Param("tenantId") Long tenantId,
            @Param("announcementIds") Collection<Long> announcementIds);

    long countUnread(
            @Param("tenantId") Long tenantId,
            @Param("actorId") Long actorId);
}
