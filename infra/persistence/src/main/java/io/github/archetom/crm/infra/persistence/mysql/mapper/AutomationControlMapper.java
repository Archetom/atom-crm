package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AutomationControlPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** One durable deployment revision prevents an older mixed-version replica from claiming work. */
@Mapper
public interface AutomationControlMapper {
    @Select("SELECT * FROM crm_automation_control WHERE control_key = 'POOL_CADENCE'")
    AutomationControlPO select();

    @Select("SELECT * FROM crm_automation_control WHERE control_key = 'POOL_CADENCE' FOR UPDATE")
    AutomationControlPO selectForUpdate();

    @Insert("""
            INSERT IGNORE INTO crm_automation_control (control_key, cadence_revision, reconciled_time)
            VALUES ('POOL_CADENCE', #{cadenceRevision}, #{reconciledTime})
            """)
    int insertIfAbsent(@Param("cadenceRevision") String cadenceRevision,
                       @Param("reconciledTime") LocalDateTime reconciledTime);

    @Update("""
            UPDATE crm_automation_control
            SET cadence_revision = #{cadenceRevision}, reconciled_time = #{reconciledTime},
                updated_time = UTC_TIMESTAMP(6)
            WHERE control_key = 'POOL_CADENCE'
            """)
    int updateRevision(@Param("cadenceRevision") String cadenceRevision,
                       @Param("reconciledTime") LocalDateTime reconciledTime);
}
