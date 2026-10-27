package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.PasswordLoginChallengePO;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for one-use password-login challenges. */
@Mapper
public interface PasswordLoginChallengeMapper extends BaseMapper<PasswordLoginChallengePO> {
}
