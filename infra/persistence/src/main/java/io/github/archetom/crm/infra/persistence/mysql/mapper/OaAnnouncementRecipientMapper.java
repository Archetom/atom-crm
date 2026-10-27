package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementRecipientPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;

@Mapper
public interface OaAnnouncementRecipientMapper
        extends BaseMapper<OaAnnouncementRecipientPO> {

    int insertBatch(
            @Param("tenantId") Long tenantId,
            @Param("announcementId") Long announcementId,
            @Param("actorIds") Collection<Long> actorIds,
            @Param("deliveredTime") LocalDateTime deliveredTime);

    @Select("""
            SELECT 1 FROM oa_announcement_recipient
            WHERE tenant_id = #{tenantId} AND announcement_id = #{announcementId} AND actor_id = #{actorId}
            FOR UPDATE
            """)
    Integer selectForDeliveryAdmission(@Param("tenantId") Long tenantId,
                                       @Param("announcementId") Long announcementId,
                                       @Param("actorId") Long actorId);

    @Select("""
            SELECT 1 FROM oa_announcement_recipient
            WHERE tenant_id = #{tenantId} AND announcement_id = #{announcementId} AND actor_id = #{actorId}
            """)
    Integer selectSnapshot(@Param("tenantId") Long tenantId,
                           @Param("announcementId") Long announcementId,
                           @Param("actorId") Long actorId);
}
