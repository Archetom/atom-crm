package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.UserPO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for tenant-scoped User persistence. */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /** Query a bounded page with an explicit tenant predicate. */
    IPage<UserPO> selectUserPage(
            Page<UserPO> page,
            @Param("username") String username,
            @Param("email") String email,
            @Param("status") String status,
            @Param("tenantId") Long tenantId
    );
}
