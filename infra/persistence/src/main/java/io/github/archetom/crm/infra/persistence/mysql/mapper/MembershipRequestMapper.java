package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.MembershipRequestPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MembershipRequestMapper extends BaseMapper<MembershipRequestPO> {
}
