package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceReferencePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalSubjectStatusPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstancePO> {
    IPage<ApprovalInstanceReferencePO> selectApprovalScanPage(
            Page<ApprovalInstanceReferencePO> page,
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("bucket") String bucket,
            @Param("status") String status,
            @Param("objectType") String objectType,
            @Param("subjectId") Long subjectId,
            @Param("keyword") String keyword);

    List<ApprovalSubjectStatusPO> selectLatestSubjectStatuses(
            @Param("tenantId") Long tenantId,
            @Param("objectType") String objectType,
            @Param("subjectIds") Collection<Long> subjectIds);
}
