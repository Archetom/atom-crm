package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentFilePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface AttachmentFileMapper extends BaseMapper<AttachmentFilePO> {

    @Select("""
            SELECT f.* FROM crm_attachment_file f
            JOIN crm_attachment_relation r ON r.tenant_id = f.tenant_id AND r.file_id = f.id
            WHERE f.tenant_id = #{tenantId} AND f.id = #{fileId}
              AND r.target_type = 'WORK_TASK' AND r.work_task_id = #{targetId}
            FOR UPDATE
            """)
    AttachmentFilePO selectWorkTargetForUpdate(
            @Param("tenantId") long tenantId, @Param("targetId") long targetId,
            @Param("fileId") long fileId);

    @Select("""
            SELECT f.* FROM crm_attachment_file f
            JOIN crm_attachment_relation r ON r.tenant_id = f.tenant_id AND r.file_id = f.id
            WHERE f.tenant_id = #{tenantId} AND f.id = #{fileId}
              AND r.target_type = 'WORK_TASK' AND r.work_task_id = #{targetId}
              AND r.relation_state = 'ACTIVE' AND f.publication_state = 'AVAILABLE'
            """)
    AttachmentFilePO selectAvailableWorkTarget(
            @Param("tenantId") long tenantId, @Param("targetId") long targetId,
            @Param("fileId") long fileId);

    @Select("""
            SELECT f.* FROM crm_attachment_file f
            JOIN crm_attachment_relation r ON r.tenant_id = f.tenant_id AND r.file_id = f.id
            WHERE f.tenant_id = #{tenantId} AND r.target_type = 'WORK_TASK'
              AND r.work_task_id = #{targetId} AND r.relation_state = 'ACTIVE'
              AND f.publication_state = 'AVAILABLE'
            ORDER BY f.available_time DESC, f.id DESC LIMIT #{offset}, #{size}
            """)
    List<AttachmentFilePO> selectAvailableWorkPage(
            @Param("tenantId") long tenantId, @Param("targetId") long targetId,
            @Param("offset") long offset, @Param("size") long size);

    @Select("""
            SELECT COUNT(*) FROM crm_attachment_file f
            JOIN crm_attachment_relation r ON r.tenant_id = f.tenant_id AND r.file_id = f.id
            WHERE f.tenant_id = #{tenantId} AND r.target_type = 'WORK_TASK'
              AND r.work_task_id = #{targetId} AND r.relation_state = 'ACTIVE'
              AND f.publication_state = 'AVAILABLE'
            """)
    long countAvailableWorkTarget(@Param("tenantId") long tenantId, @Param("targetId") long targetId);

    /** Target column selection is a closed provider mapping, never request-supplied SQL. */
    @SelectProvider(type = AttachmentFileSqlProvider.class, method = "countAvailableByTarget")
    long countAvailableByTarget(@Param("tenantId") long tenantId, @Param("targetType") String targetType,
                                @Param("targetId") long targetId,
                                @Param("marketingActivityImageType") String marketingActivityImageType,
                                @Param("productImageType") String productImageType);

    @SelectProvider(type = AttachmentFileSqlProvider.class, method = "selectAvailablePageByTarget")
    List<AttachmentFilePO> selectAvailablePageByTarget(
            @Param("tenantId") long tenantId, @Param("targetType") String targetType,
            @Param("targetId") long targetId,
            @Param("marketingActivityImageType") String marketingActivityImageType,
            @Param("productImageType") String productImageType,
            @Param("offset") long offset, @Param("size") long size);

    @Select("""
            SELECT file_id FROM crm_attachment_relation
             WHERE tenant_id = #{tenantId} AND target_type = 'WORK_TASK' AND work_task_id = #{targetId}
             LIMIT 1 FOR UPDATE
            """)
    Long selectWorkHistoryForUpdate(@Param("tenantId") long tenantId, @Param("targetId") long targetId);
}
