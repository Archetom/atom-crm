package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedConnector;
import io.github.archetom.crm.application.vo.CallFactInboxReceiptVO;

/** Private-adapter entry point for the minimal normalized fact delivery receipt. */
public interface CallFactInboxService {
    Result<CallFactInboxReceiptVO> accept(AuthenticatedConnector connector, byte[] deliveryRef, byte[] factDigest);
}
