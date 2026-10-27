package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceMemberPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface OaAnnouncementAudienceMemberMapper
        extends BaseMapper<OaAnnouncementAudienceMemberPO> {

    int insertBatch(
            @Param("tenantId") Long tenantId,
            @Param("announcementId") Long announcementId,
            @Param("actorIds") Collection<Long> actorIds);
}
