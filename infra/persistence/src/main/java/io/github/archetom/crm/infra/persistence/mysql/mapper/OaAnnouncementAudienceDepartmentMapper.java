package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementAudienceDepartmentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface OaAnnouncementAudienceDepartmentMapper
        extends BaseMapper<OaAnnouncementAudienceDepartmentPO> {

    int insertBatch(
            @Param("tenantId") Long tenantId,
            @Param("announcementId") Long announcementId,
            @Param("departmentIds") Collection<Long> departmentIds);
}
