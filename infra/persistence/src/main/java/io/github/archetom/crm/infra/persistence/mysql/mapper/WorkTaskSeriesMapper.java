package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkTaskSeriesMapper extends BaseMapper<WorkTaskSeriesPO> {
    /** A bounded, unlocked cursor scan determines the next reconciliation batch. */
    @Select("""
            SELECT * FROM crm_work_task_series
            WHERE status = 'ACTIVE'
              AND (#{afterTenantId} IS NULL OR tenant_id > #{afterTenantId}
                   OR (tenant_id = #{afterTenantId} AND id > #{afterSeriesId}))
            ORDER BY tenant_id, id
            LIMIT #{limit}
            """)
    List<WorkTaskSeriesPO> selectActiveForRecurrenceReconciliation(
            @Param("afterTenantId") Long afterTenantId, @Param("afterSeriesId") Long afterSeriesId,
            @Param("limit") int limit);

    /** Schedule rows are locked first; only then lock the exact candidate roots. */
    @Select("""
            <script>
            SELECT * FROM crm_work_task_series
            WHERE status = 'ACTIVE' AND id IN
              <foreach collection="seriesIds" item="seriesId" open="(" separator="," close=")">
                #{seriesId}
              </foreach>
            ORDER BY tenant_id, id
            FOR UPDATE
            </script>
            """)
    List<WorkTaskSeriesPO> selectActiveForRecurrenceReconciliationForUpdate(
            @Param("seriesIds") List<Long> seriesIds);

    @Select("SELECT COUNT(*) FROM crm_work_task_series WHERE tenant_id = #{tenantId} AND status = 'ACTIVE'")
    long countActiveForRecurrenceReconciliation(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE crm_work_task_series
            SET next_due_at = #{nextDueAt}, version = version + 1, updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND id = #{seriesId} AND status = 'ACTIVE'
            """)
    int updateActiveNextDueAt(@Param("tenantId") long tenantId, @Param("seriesId") long seriesId,
                              @Param("nextDueAt") LocalDateTime nextDueAt);

    @Update("""
            UPDATE crm_work_task_series
            SET status = 'ENDED', next_nominal_local_time = NULL, next_due_at = NULL,
                last_safe_reason = 'TIME_RANGE', version = version + 1, updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND id = #{seriesId} AND status = 'ACTIVE'
            """)
    int endForRecurrenceTimeRange(@Param("tenantId") long tenantId, @Param("seriesId") long seriesId);
}
