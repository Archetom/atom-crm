package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.PasswordResetTokenPO;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for hash-only password-reset capability records. */
@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenPO> {
}
