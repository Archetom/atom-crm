package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkTaskMapper extends BaseMapper<WorkTaskPO> {

    @org.apache.ibatis.annotations.Select("""
            SELECT * FROM crm_work_task WHERE tenant_id = #{tenantId} AND id = #{taskId}
              AND status <> 'DELETED' FOR UPDATE
            """)
    WorkTaskPO selectForAttachmentMutation(@org.apache.ibatis.annotations.Param("tenantId") long tenantId,
                                           @org.apache.ibatis.annotations.Param("taskId") long taskId);

    @org.apache.ibatis.annotations.Select("""
            SELECT * FROM crm_work_task WHERE tenant_id = #{tenantId} AND id = #{taskId} FOR UPDATE
            """)
    WorkTaskPO selectForReminderEffect(@org.apache.ibatis.annotations.Param("tenantId") long tenantId,
                                       @org.apache.ibatis.annotations.Param("taskId") long taskId);
}
