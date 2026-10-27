package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AutomationRunPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AutomationRunMapper {
    @Insert("""
            INSERT INTO crm_automation_run
                (tenant_id, kind, generation, due_at, evaluation_time, status, available_at,
                 attempt, lease_fence, deadline_at, affected_count)
            VALUES (#{tenantId}, #{kind}, #{generation}, #{dueAt}, #{evaluationTime}, 'READY', #{availableAt},
                    0, 0, #{deadlineAt}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AutomationRunPO run);

    @Select("""
            <script>
            SELECT run.*
            FROM crm_automation_run run
            JOIN crm_automation_schedule schedule
              ON schedule.tenant_id = run.tenant_id AND schedule.kind = run.kind
            WHERE schedule.status = 'ACTIVE' AND schedule.cadence_revision = #{cadenceRevision}
              AND schedule.generation = run.generation
              AND run.kind IN
              <foreach collection="kinds" item="kind" open="(" separator="," close=")">
                #{kind}
              </foreach>
              AND ((run.status IN ('READY', 'RETRY_WAIT')
                    AND (run.available_at &lt;= #{now} OR run.deadline_at &lt;= #{now}
                         OR run.attempt >= #{maxAttempts}))
                   OR (run.status = 'LEASED' AND run.lease_expires_at &lt;= #{now}))
            ORDER BY run.available_at ASC, run.id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            </script>
            """)
    AutomationRunPO selectClaimableForUpdate(@Param("now") LocalDateTime now,
                                              @Param("cadenceRevision") String cadenceRevision,
                                              @Param("maxAttempts") int maxAttempts,
                                              @Param("kinds") java.util.Collection<String> kinds);

    @Update("""
            UPDATE crm_automation_run
            SET status = 'LEASED', attempt = attempt + 1, lease_owner = #{leaseOwner},
                lease_expires_at = #{leaseExpiresAt}, lease_fence = lease_fence + 1,
                updated_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND deadline_at > #{now} AND attempt < #{maxAttempts}
              AND (status IN ('READY', 'RETRY_WAIT')
                   OR (status = 'LEASED' AND lease_expires_at <= #{now}))
            """)
    int lease(@Param("id") long id, @Param("leaseOwner") String leaseOwner,
              @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
              @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_automation_run
            SET status = 'DEAD', safe_reason = 'RETRY_EXHAUSTED', lease_owner = NULL,
                lease_expires_at = NULL, completed_time = #{completedTime}, updated_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND status IN ('READY', 'LEASED', 'RETRY_WAIT')
              AND (deadline_at <= #{now} OR attempt >= #{maxAttempts})
            """)
    int deadBeforeClaim(@Param("id") long id, @Param("now") LocalDateTime now,
                        @Param("maxAttempts") int maxAttempts,
                        @Param("completedTime") LocalDateTime completedTime);

    @Select("""
            SELECT run.*
            FROM crm_automation_run run
            WHERE run.id = #{id} AND run.tenant_id = #{tenantId} AND run.kind = #{kind}
              AND run.generation = #{generation} AND run.status = 'LEASED'
              AND run.lease_owner = #{leaseOwner} AND run.lease_fence = #{leaseFence}
              AND run.lease_expires_at > #{now}
            FOR UPDATE
            """)
    AutomationRunPO selectCurrentLeaseForUpdate(
            @Param("id") long id, @Param("tenantId") long tenantId, @Param("kind") String kind,
            @Param("generation") long generation, @Param("leaseOwner") String leaseOwner,
            @Param("leaseFence") long leaseFence, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_automation_run
            SET status = 'SUCCEEDED', result_code = #{resultCode}, affected_count = #{affectedCount},
                lease_owner = NULL, lease_expires_at = NULL, completed_time = #{completedTime},
                updated_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND tenant_id = #{tenantId} AND kind = #{kind}
              AND generation = #{generation} AND status = 'LEASED'
              AND lease_owner = #{leaseOwner} AND lease_fence = #{leaseFence}
              AND lease_expires_at > #{completedTime} AND deadline_at > #{completedTime}
            """)
    int succeed(@Param("id") long id, @Param("tenantId") long tenantId, @Param("kind") String kind,
                @Param("generation") long generation, @Param("leaseOwner") String leaseOwner,
                @Param("leaseFence") long leaseFence, @Param("resultCode") String resultCode,
                @Param("affectedCount") int affectedCount, @Param("completedTime") LocalDateTime completedTime);

    @Select("SELECT * FROM crm_automation_run WHERE id = #{id} FOR UPDATE")
    AutomationRunPO selectByIdForUpdate(@Param("id") long id);

    @Update("""
            UPDATE crm_automation_run
            SET status = #{status}, available_at = #{availableAt}, safe_reason = #{safeReason},
                lease_owner = NULL, lease_expires_at = NULL, completed_time = #{completedTime},
                updated_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND status = 'LEASED' AND lease_owner = #{leaseOwner}
              AND lease_fence = #{leaseFence}
              AND lease_expires_at > #{now}
            """)
    int recover(@Param("id") long id, @Param("leaseOwner") String leaseOwner,
                @Param("leaseFence") long leaseFence, @Param("status") String status,
                @Param("availableAt") LocalDateTime availableAt, @Param("safeReason") String safeReason,
                @Param("completedTime") LocalDateTime completedTime, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_automation_run
            SET status = 'CANCELLED', safe_reason = 'SCHEDULE_CHANGED', lease_owner = NULL,
                lease_expires_at = NULL, completed_time = #{completedTime}, updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND kind = #{kind}
              AND status IN ('READY', 'LEASED', 'RETRY_WAIT')
            """)
    int cancelActive(@Param("tenantId") long tenantId, @Param("kind") String kind,
                     @Param("completedTime") LocalDateTime completedTime);

    @Update("""
            UPDATE crm_automation_run
            SET status = 'CANCELLED', safe_reason = 'SCHEDULE_CHANGED', lease_owner = NULL,
                lease_expires_at = NULL, completed_time = #{completedTime}, updated_time = UTC_TIMESTAMP(6)
            WHERE tenant_id = #{tenantId} AND kind = #{kind} AND id <> #{preservedRunId}
              AND status IN ('READY', 'LEASED', 'RETRY_WAIT')
            """)
    int cancelActiveExcept(@Param("tenantId") long tenantId, @Param("kind") String kind,
                           @Param("preservedRunId") long preservedRunId,
                           @Param("completedTime") LocalDateTime completedTime);

    @Select("""
            SELECT run.kind, COUNT(*) AS count
            FROM crm_automation_run run
            JOIN crm_automation_schedule schedule
              ON schedule.tenant_id = run.tenant_id AND schedule.kind = run.kind
            WHERE schedule.cadence_revision <> #{cadenceRevision}
              AND run.status IN ('READY', 'LEASED', 'RETRY_WAIT')
            GROUP BY run.kind
            """)
    java.util.List<io.github.archetom.crm.infra.persistence.mysql.po.AutomationMetricPO>
    selectActiveCountsForRevisionDrift(@Param("cadenceRevision") String cadenceRevision);

    @Update("""
            UPDATE crm_automation_run run
            JOIN crm_automation_schedule schedule
              ON schedule.tenant_id = run.tenant_id AND schedule.kind = run.kind
            SET run.status = 'CANCELLED', run.safe_reason = 'SCHEDULE_CHANGED', run.lease_owner = NULL,
                run.lease_expires_at = NULL, run.completed_time = #{completedTime},
                run.updated_time = UTC_TIMESTAMP(6)
            WHERE schedule.cadence_revision = #{cadenceRevision}
              AND run.generation <> schedule.generation
              AND run.status IN ('READY', 'LEASED', 'RETRY_WAIT')
            """)
    int cancelStaleScheduleRuns(@Param("cadenceRevision") String cadenceRevision,
                                @Param("completedTime") LocalDateTime completedTime);

    @Select("""
            SELECT kind, status, COUNT(*) AS count
            FROM crm_automation_run
            GROUP BY kind, status
            """)
    java.util.List<io.github.archetom.crm.infra.persistence.mysql.po.AutomationMetricPO> selectStateCounts();

    @Select("""
            SELECT kind, MAX(completed_time) AS latest_success_utc
            FROM crm_automation_run
            WHERE status = 'SUCCEEDED'
            GROUP BY kind
            """)
    java.util.List<io.github.archetom.crm.infra.persistence.mysql.po.AutomationMetricPO> selectLatestSuccessUtc();

    @Delete("""
            DELETE FROM crm_automation_run
            WHERE status IN ('SUCCEEDED', 'CANCELLED', 'DEAD') AND completed_time < #{before}
            ORDER BY completed_time ASC, id ASC
            LIMIT #{limit}
            """)
    int deleteTerminalBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
