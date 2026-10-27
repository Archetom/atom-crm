package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.application.vo.InvoiceProfileVO;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface InvoiceQueryService {
    Result<Pager<InvoiceVO>> query(
            AuthenticatedCaller caller, InvoiceQueryRequest request);
    Result<InvoiceVO> get(AuthenticatedCaller caller, Long invoiceId);
    Result<List<InvoiceProfileVO>> profiles(
            AuthenticatedCaller caller, Long customerId);
}
