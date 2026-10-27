package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowRecordPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StageWorkflowRecordMapper extends BaseMapper<StageWorkflowRecordPO> {
}
