package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AutomationSchedulePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AutomationScheduleMapper {
    @Select("SELECT UTC_TIMESTAMP(6)")
    LocalDateTime selectUtcNow();

    @Select("""
            SELECT * FROM crm_automation_schedule
            WHERE tenant_id = #{tenantId} AND kind = #{kind}
            FOR UPDATE
            """)
    AutomationSchedulePO selectForUpdate(@Param("tenantId") long tenantId, @Param("kind") String kind);

    @Select("""
            <script>
            SELECT * FROM crm_automation_schedule
            WHERE status = 'ACTIVE' AND cadence_revision = #{cadenceRevision} AND next_due_at &lt;= #{now}
              AND kind IN
              <foreach collection="kinds" item="kind" open="(" separator="," close=")">
                #{kind}
              </foreach>
              AND NOT EXISTS (
                  SELECT 1 FROM crm_automation_run run
                  WHERE run.tenant_id = crm_automation_schedule.tenant_id
                    AND run.kind = crm_automation_schedule.kind
                    AND run.active_slot = 1)
            ORDER BY next_due_at ASC, tenant_id ASC, kind ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            </script>
            """)
    AutomationSchedulePO selectDueForUpdate(@Param("now") LocalDateTime now,
                                             @Param("cadenceRevision") String cadenceRevision,
                                             @Param("kinds") java.util.Collection<String> kinds);

    @Select("SELECT COUNT(*) FROM crm_automation_schedule WHERE cadence_revision <> #{cadenceRevision}")
    long countRevisionDrift(@Param("cadenceRevision") String cadenceRevision);

    @Update("""
            UPDATE crm_automation_schedule
            SET cadence_revision = #{cadenceRevision}, generation = generation + 1,
                next_due_at = CASE WHEN status = 'ACTIVE' THEN #{now} ELSE NULL END,
                version = version + 1, updated_time = UTC_TIMESTAMP(6)
            WHERE cadence_revision <> #{cadenceRevision}
            """)
    int reconcileCadenceRevision(@Param("cadenceRevision") String cadenceRevision,
                                 @Param("now") LocalDateTime now);

    @Select("""
            SELECT kind, status, COUNT(*) AS count
            FROM crm_automation_schedule
            GROUP BY kind, status
            """)
    java.util.List<io.github.archetom.crm.infra.persistence.mysql.po.AutomationMetricPO> selectStateCounts();

    @Insert("""
            INSERT IGNORE INTO crm_automation_schedule
                (tenant_id, kind, status, generation, cadence_revision, next_due_at, version)
            VALUES (#{tenantId}, #{kind}, 'PAUSED', 1, #{cadenceRevision}, NULL, 0)
            """)
    int insertPausedIfAbsent(@Param("tenantId") long tenantId, @Param("kind") String kind,
                              @Param("cadenceRevision") String cadenceRevision);

    @Update("""
            UPDATE crm_automation_schedule
            SET status = #{status}, generation = #{generation}, cadence_revision = #{cadenceRevision},
                next_due_at = #{nextDueAt}, version = version + 1, updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND kind = #{kind}
            """)
    int updateState(@Param("tenantId") long tenantId, @Param("kind") String kind,
                    @Param("status") String status, @Param("generation") long generation,
                    @Param("cadenceRevision") String cadenceRevision,
                    @Param("nextDueAt") LocalDateTime nextDueAt);

    @Update("""
            UPDATE crm_automation_schedule
            SET next_due_at = #{nextDueAt}, version = version + 1, updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND kind = #{kind} AND status = 'ACTIVE'
              AND generation = #{generation}
            """)
    int advanceDue(@Param("tenantId") long tenantId, @Param("kind") String kind,
                   @Param("generation") long generation, @Param("nextDueAt") LocalDateTime nextDueAt);
}
