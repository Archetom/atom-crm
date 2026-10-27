package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskReminderOccurrencePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkTaskReminderOccurrenceMapper extends BaseMapper<WorkTaskReminderOccurrencePO> {

    @Select("SELECT UTC_TIMESTAMP(6)")
    LocalDateTime currentUtc();

    @Select("""
            SELECT * FROM crm_work_task_reminder_occurrence
            WHERE (status IN ('READY', 'RETRY_WAIT')
                   AND (available_at <= #{now} OR deadline_at <= #{now} OR attempt_count >= #{maxAttempts}))
               OR (status = 'LEASED' AND lease_until <= #{now})
            ORDER BY available_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    WorkTaskReminderOccurrencePO selectClaimableForUpdate(@Param("now") LocalDateTime now,
                                                           @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_work_task_reminder_occurrence
            SET status = 'LEASED', attempt_count = attempt_count + 1,
                lease_owner = #{leaseOwner}, lease_fence = lease_fence + 1, lease_until = #{leaseUntil}
            WHERE id = #{id} AND status IN ('READY', 'RETRY_WAIT') AND available_at <= #{now}
              AND deadline_at > #{now} AND attempt_count < #{maxAttempts}
            """)
    int claimReady(@Param("id") long id, @Param("leaseOwner") String leaseOwner,
                   @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now,
                   @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_work_task_reminder_occurrence
            SET status = 'RETRY_WAIT', available_at = #{now}, lease_owner = NULL, lease_until = NULL
            WHERE id = #{id} AND status = 'LEASED' AND lease_until <= #{now}
            """)
    int requeueExpiredLease(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_work_task_reminder_occurrence
            SET status = 'DEAD', safe_reason = 'RETRY_EXHAUSTED', lease_owner = NULL,
                lease_until = NULL, terminal_time = #{now}
            WHERE id = #{id} AND status IN ('READY', 'RETRY_WAIT', 'LEASED')
              AND (status <> 'LEASED' OR lease_until <= #{now})
              AND (deadline_at <= #{now} OR attempt_count >= #{maxAttempts})
            """)
    int deadBeforeClaim(@Param("id") long id, @Param("now") LocalDateTime now,
                        @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_work_task_reminder_occurrence
            SET status = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{now}
                              THEN 'DEAD' ELSE 'RETRY_WAIT' END,
                available_at = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{now}
                                    THEN available_at ELSE #{retryAt} END,
                safe_reason = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{now}
                                   THEN 'RETRY_EXHAUSTED' ELSE 'RETRY' END,
                lease_owner = NULL, lease_until = NULL,
                terminal_time = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{now}
                                     THEN #{now} ELSE NULL END
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'LEASED'
              AND lease_owner = #{leaseOwner} AND lease_fence = #{leaseFence}
              AND lease_until > #{now}
            """)
    int recoverFailure(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                       @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts,
                       @Param("retryAt") LocalDateTime retryAt);

    @Delete("""
            DELETE FROM crm_work_task_reminder_occurrence
            WHERE status IN ('EMITTED', 'CANCELLED', 'DEAD')
              AND terminal_time < #{beforeExclusive}
              AND NOT EXISTS (
                  SELECT 1 FROM crm_notification notification
                  WHERE notification.tenant_id = crm_work_task_reminder_occurrence.tenant_id
                    AND notification.reminder_occurrence_id = crm_work_task_reminder_occurrence.id
              )
              AND EXISTS (
                  SELECT 1 FROM crm_work_task task
                  WHERE task.tenant_id = crm_work_task_reminder_occurrence.tenant_id
                    AND task.id = crm_work_task_reminder_occurrence.work_task_id
                    AND (task.status NOT IN ('TODO', 'IN_PROGRESS')
                         OR task.notification_projection_version
                            <> crm_work_task_reminder_occurrence.notification_projection_version)
              )
            ORDER BY terminal_time ASC, id ASC LIMIT #{limit}
            """)
    int purgeTerminal(@Param("beforeExclusive") LocalDateTime beforeExclusive,
                      @Param("limit") int limit);
}
