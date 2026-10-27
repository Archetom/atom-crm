package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataAdoptionSelectionPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EnterpriseDataAdoptionSelectionMapper extends BaseMapper<EnterpriseDataAdoptionSelectionPO> {
    @Delete("""
            <script>
            DELETE FROM crm_enterprise_data_adoption_selection
            WHERE adoption_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int deleteByAdoptionIds(@Param("ids") List<Long> ids);
}
