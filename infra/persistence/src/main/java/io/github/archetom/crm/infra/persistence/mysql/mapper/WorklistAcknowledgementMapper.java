package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.infra.persistence.mysql.po.WorklistAcknowledgementPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorklistAcknowledgementMapper extends BaseMapper<WorklistAcknowledgementPO> {
    List<WorklistAcknowledgementPO> selectAcknowledged(
            @Param("tenantId") Long tenantId,
            @Param("actorId") Long actorId,
            @Param("items") Collection<WorklistItemFingerprint> items);
}
