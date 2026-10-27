package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesWeekdayPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkTaskSeriesWeekdayMapper extends BaseMapper<WorkTaskSeriesWeekdayPO> {
    @Select("""
            <script>
            SELECT * FROM crm_work_task_series_weekday
            WHERE work_task_series_id IN
              <foreach collection="seriesIds" item="seriesId" open="(" separator="," close=")">
                #{seriesId}
              </foreach>
            ORDER BY work_task_series_id, weekday
            </script>
            """)
    List<WorkTaskSeriesWeekdayPO> selectForRecurrenceReconciliation(
            @org.apache.ibatis.annotations.Param("seriesIds") List<Long> seriesIds);
}
