package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowCommentPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StageWorkflowCommentMapper extends BaseMapper<StageWorkflowCommentPO> {
}
