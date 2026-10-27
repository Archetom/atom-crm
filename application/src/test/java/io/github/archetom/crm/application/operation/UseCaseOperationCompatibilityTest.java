package io.github.archetom.crm.application.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UseCaseOperationCompatibilityTest {
    @Test
    void enterpriseDataScenesKeepTheirPublishedCodes() {
        assertEquals("7300", UseCaseOperation.ENTERPRISE_DATA_CAPABILITY_GET.code());
        assertEquals("7301", UseCaseOperation.ENTERPRISE_DATA_NAME_SEARCH.code());
        assertEquals("7302", UseCaseOperation.ENTERPRISE_DATA_IDENTIFIER_LOOKUP.code());
        assertEquals("7303", UseCaseOperation.ENTERPRISE_DATA_ADOPTION_PREVIEW.code());
        assertEquals("7304", UseCaseOperation.ENTERPRISE_DATA_ADOPT.code());
        assertEquals("7305", UseCaseOperation.ENTERPRISE_DATA_ADOPTION_RETENTION.code());
    }

    @Test
    void callFactInboxSceneKeepsItsPublishedCode() {
        assertEquals("7207", UseCaseOperation.CALL_FACT_INBOX_ACCEPT.code());
    }

    @Test
    void leadIntakeClientScenesKeepTheirPublishedCodes() {
        assertEquals("6220", UseCaseOperation.LEAD_INTAKE_CLIENT_CREATE.code());
        assertEquals("6221", UseCaseOperation.LEAD_INTAKE_CLIENT_DEACTIVATE.code());
        assertEquals("6222", UseCaseOperation.LEAD_INTAKE_CLIENT_LIST.code());
    }
}
