package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.ActivityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivitySubjectSummaryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ActivityMapper extends BaseMapper<ActivityPO> {

    /**
     * The newest real interaction per subject, for the "last activity" column of a record list.
     *
     * <p>Server-generated entries are excluded. Recording a follow-up also appends a
     * {@code SYSTEM_AUDIT} entry to this same table, and that entry carries the write time rather
     * than the time the interaction happened — so it outranks the follow-up it describes and every
     * list row would otherwise report "Follow-up activity created" stamped with the server clock.
     * The timeline still shows those entries; this summary is about when the customer was last
     * actually contacted.</p>
     */
    @Select("""
            <script>
            SELECT latest.subject_id AS subjectId, latest.occurred_time AS occurredTime,
                   latest.content AS content
            FROM (
                SELECT subject_id, occurred_time, content,
                       ROW_NUMBER() OVER (
                           PARTITION BY subject_id
                           ORDER BY occurred_time DESC, id DESC
                       ) AS rowNumber
                FROM crm_activity
                WHERE tenant_id = #{tenantId}
                  AND subject_type = #{subjectType}
                  AND status = 'ACTIVE'
                  AND kind &lt;&gt; 'SYSTEM'
                  AND subject_id IN
                  <foreach collection="subjectIds" item="subjectId" open="(" separator="," close=")">
                      #{subjectId}
                  </foreach>
            ) latest
            WHERE latest.rowNumber = 1
            </script>
            """)
    List<ActivitySubjectSummaryPO> selectLatestBySubjects(
            @Param("tenantId") Long tenantId,
            @Param("subjectType") String subjectType,
            @Param("subjectIds") Collection<Long> subjectIds);
}
