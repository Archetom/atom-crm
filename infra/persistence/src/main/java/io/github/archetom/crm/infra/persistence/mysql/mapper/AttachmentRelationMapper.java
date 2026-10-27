package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentRelationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AttachmentRelationMapper extends BaseMapper<AttachmentRelationPO> {

    @Select("""
            SELECT COUNT(*) FROM crm_attachment_relation
            WHERE tenant_id = #{tenantId} AND target_type = 'WORK_TASK'
              AND work_task_id = #{targetId} AND relation_state = 'ACTIVE'
            FOR UPDATE
            """)
    long countActiveWorkRelationsForUpdate(
            @Param("tenantId") long tenantId, @Param("targetId") long targetId);

    @Select("""
            SELECT * FROM crm_attachment_relation WHERE tenant_id = #{tenantId} AND file_id = #{fileId}
            FOR UPDATE
            """)
    io.github.archetom.crm.infra.persistence.mysql.po.AttachmentRelationPO selectByFileForUpdate(
            @Param("tenantId") long tenantId, @Param("fileId") long fileId);

    @Select("""
            SELECT * FROM crm_attachment_relation WHERE tenant_id = #{tenantId} AND file_id = #{fileId}
            """)
    io.github.archetom.crm.infra.persistence.mysql.po.AttachmentRelationPO selectByFile(
            @Param("tenantId") long tenantId, @Param("fileId") long fileId);

    @Update("""
            UPDATE crm_attachment_relation SET relation_state = 'WITHDRAWN', version = version + 1,
              updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND file_id = #{fileId} AND target_type = 'WORK_TASK'
              AND work_task_id = #{targetId} AND relation_state = 'ACTIVE'
            """)
    int withdrawWorkRelation(@Param("tenantId") long tenantId, @Param("targetId") long targetId,
                            @Param("fileId") long fileId);
}
