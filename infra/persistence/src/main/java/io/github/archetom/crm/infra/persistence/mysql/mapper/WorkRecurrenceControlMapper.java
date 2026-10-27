package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.WorkRecurrenceControlPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkRecurrenceControlMapper {
    @Select("SELECT * FROM crm_work_recurrence_control WHERE control_key = 'RECURRENCE_TZDB'")
    WorkRecurrenceControlPO select();

    @Select("SELECT * FROM crm_work_recurrence_control WHERE control_key = 'RECURRENCE_TZDB' FOR UPDATE")
    WorkRecurrenceControlPO selectForUpdate();

    @Insert("""
            INSERT IGNORE INTO crm_work_recurrence_control
                (control_key, tzdb_fingerprint, policy_revision, reconciled_time)
            VALUES ('RECURRENCE_TZDB', #{tzdbFingerprint}, #{policyRevision}, #{reconciledTime})
            """)
    int insertIfAbsent(@Param("tzdbFingerprint") String tzdbFingerprint,
                       @Param("policyRevision") String policyRevision,
                       @Param("reconciledTime") LocalDateTime reconciledTime);

    @Update("""
            UPDATE crm_work_recurrence_control
            SET reconciliation_status = 'RECONCILING', target_tzdb_fingerprint = #{tzdbFingerprint},
                target_policy_revision = #{policyRevision}, cursor_tenant_id = NULL, cursor_series_id = NULL,
                updated_time = UTC_TIMESTAMP(6)
            WHERE control_key = 'RECURRENCE_TZDB'
            """)
    int startReconciliation(@Param("tzdbFingerprint") String tzdbFingerprint,
                            @Param("policyRevision") String policyRevision);

    @Update("""
            UPDATE crm_work_recurrence_control
            SET cursor_tenant_id = #{cursorTenantId}, cursor_series_id = #{cursorSeriesId},
                updated_time = UTC_TIMESTAMP(6)
            WHERE control_key = 'RECURRENCE_TZDB' AND reconciliation_status = 'RECONCILING'
            """)
    int updateCursor(@Param("cursorTenantId") long cursorTenantId,
                     @Param("cursorSeriesId") long cursorSeriesId);

    @Update("""
            UPDATE crm_work_recurrence_control
            SET tzdb_fingerprint = #{tzdbFingerprint}, policy_revision = #{policyRevision},
                reconciliation_status = 'READY', target_tzdb_fingerprint = NULL, target_policy_revision = NULL,
                cursor_tenant_id = NULL, cursor_series_id = NULL, reconciled_time = #{reconciledTime},
                updated_time = UTC_TIMESTAMP(6)
            WHERE control_key = 'RECURRENCE_TZDB' AND reconciliation_status = 'RECONCILING'
            """)
    int publishReady(@Param("tzdbFingerprint") String tzdbFingerprint,
                     @Param("policyRevision") String policyRevision,
                     @Param("reconciledTime") LocalDateTime reconciledTime);
}
