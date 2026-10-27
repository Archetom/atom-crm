package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.infra.persistence.mysql.po.MemberProfilePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface MemberProfileMapper extends BaseMapper<MemberProfilePO> {

    IPage<MemberProfilePO> selectActiveProfilePage(
            Page<MemberProfilePO> page,
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword);

    IPage<MemberProfilePO> selectActiveDepartmentProfilePage(
            Page<MemberProfilePO> page,
            @Param("tenantId") Long tenantId,
            @Param("departmentId") Long departmentId,
            @Param("keyword") String keyword);

    IPage<MemberProfilePO> selectActiveDepartmentProfilesPage(
            Page<MemberProfilePO> page,
            @Param("tenantId") Long tenantId,
            @Param("departmentIds") Collection<Long> departmentIds,
            @Param("keyword") String keyword);
}
