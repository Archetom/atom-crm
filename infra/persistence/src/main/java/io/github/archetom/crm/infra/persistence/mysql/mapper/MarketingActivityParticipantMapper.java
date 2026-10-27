package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityParticipantPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MarketingActivityParticipantMapper extends BaseMapper<MarketingActivityParticipantPO> {
}
