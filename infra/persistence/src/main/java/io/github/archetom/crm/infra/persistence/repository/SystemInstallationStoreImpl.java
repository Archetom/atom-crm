package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.installation.SystemInstallationStore;
import io.github.archetom.crm.infra.persistence.mysql.mapper.SystemInstallationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Database-enforced single-winner claim for the public initial-installation operation. */
@Repository
@RequiredArgsConstructor
public class SystemInstallationStoreImpl implements SystemInstallationStore {

    private final SystemInstallationMapper systemInstallationMapper;

    @Override
    public boolean installationRequired() {
        return !systemInstallationMapper.isInstalled();
    }

    @Override
    public boolean claimInitialInstallation() {
        try {
            return systemInstallationMapper.claimInitialInstallation() == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
