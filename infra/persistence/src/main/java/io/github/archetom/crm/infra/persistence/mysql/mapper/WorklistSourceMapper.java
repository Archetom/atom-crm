package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.WorklistSourceRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorklistSourceMapper {
    List<WorklistSourceRecordPO> selectDue(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("source") String source,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("untilExclusive") LocalDateTime untilExclusive,
            @Param("afterTime") LocalDateTime afterTime,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);
}
