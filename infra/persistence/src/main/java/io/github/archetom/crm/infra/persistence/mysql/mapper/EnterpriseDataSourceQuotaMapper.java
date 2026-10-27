package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataSourceQuotaPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface EnterpriseDataSourceQuotaMapper extends BaseMapper<EnterpriseDataSourceQuotaPO> {
    @Insert("""
            INSERT IGNORE INTO crm_enterprise_data_source_quota
              (source_key, window_started_at, request_count, updated_time)
            VALUES (#{sourceKey}, #{windowStartedAt}, 0, #{now})
            """)
    int insertIfAbsent(@Param("sourceKey") String sourceKey,
                       @Param("windowStartedAt") LocalDateTime windowStartedAt,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_enterprise_data_source_quota
            SET request_count = CASE
                    WHEN window_started_at < #{windowStartedAt} THEN 1
                    ELSE request_count + 1
                END,
                window_started_at = CASE
                    WHEN window_started_at < #{windowStartedAt} THEN #{windowStartedAt}
                    ELSE window_started_at
                END,
                updated_time = #{now}, version = version + 1
            WHERE source_key = #{sourceKey}
              AND (window_started_at < #{windowStartedAt} OR request_count < #{maximumRequests})
            """)
    int acquireIfAvailable(@Param("sourceKey") String sourceKey,
                           @Param("windowStartedAt") LocalDateTime windowStartedAt,
                           @Param("maximumRequests") int maximumRequests,
                           @Param("now") LocalDateTime now);
}
