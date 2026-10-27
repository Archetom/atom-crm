package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CallFactInboxPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallFactInboxMapper extends BaseMapper<CallFactInboxPO> { }
