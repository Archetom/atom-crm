package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentGlobalUsagePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AttachmentGlobalUsageMapper extends BaseMapper<AttachmentGlobalUsagePO> {
    @Insert("INSERT IGNORE INTO crm_attachment_global_usage (singleton_id) VALUES (1)")
    int insertIfAbsent();

    @Select("SELECT * FROM crm_attachment_global_usage WHERE singleton_id = 1 FOR UPDATE")
    AttachmentGlobalUsagePO selectForUpdate();

    @Update("""
            UPDATE crm_attachment_global_usage SET ready_bytes = #{readyBytes}, quarantine_bytes = #{quarantineBytes},
              pending_bytes = #{pendingBytes}, updated_time = #{now}, version = version + 1
            WHERE singleton_id = 1 AND version = #{version}
            """)
    int replaceChecked(@Param("version") long version, @Param("readyBytes") long readyBytes,
                       @Param("quarantineBytes") long quarantineBytes, @Param("pendingBytes") long pendingBytes,
                       @Param("now") LocalDateTime now);
}
