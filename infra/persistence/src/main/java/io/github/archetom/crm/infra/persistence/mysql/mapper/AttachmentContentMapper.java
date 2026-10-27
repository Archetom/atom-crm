package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentContentPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttachmentContentMapper extends BaseMapper<AttachmentContentPO> {
}
