package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Minimal mapper for the singleton first-installation marker. */
@Mapper
public interface SystemInstallationMapper {

    /**
     * A system that owns any tenant has been initialized, whether that happened through this
     * public route or through an external identity system that never creates a login user.
     * Tenant rows are only ever inserted, so this answer only moves from false to true and the
     * public setup window cannot reopen.
     */
    @Select("""
            SELECT EXISTS(SELECT 1 FROM system_installation WHERE singleton_key = 1)
                OR EXISTS(SELECT 1 FROM organization_tenant)
            """)
    boolean isInstalled();

    /**
     * Claims the single setup slot. The same tenant-existence condition guards the insert, so a
     * caller that races the status read still loses once any tenant exists.
     */
    @Insert("""
            INSERT INTO system_installation (singleton_key, initialized_tenant_id)
            SELECT 1, 1
            WHERE NOT EXISTS (SELECT 1 FROM organization_tenant)
            """)
    int claimInitialInstallation();
}
