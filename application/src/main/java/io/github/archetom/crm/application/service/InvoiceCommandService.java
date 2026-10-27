package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchResetRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceIssueRequest;
import io.github.archetom.crm.api.dto.request.InvoiceOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceUpdateRequest;
import io.github.archetom.crm.application.vo.InvoiceProfileVO;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.common.result.Result;

public interface InvoiceCommandService {
    Result<InvoiceVO> create(AuthenticatedCaller caller, InvoiceCreateRequest request);
    Result<InvoiceVO> update(
            AuthenticatedCaller caller, Long invoiceId, InvoiceUpdateRequest request);
    Result<InvoiceVO> updateOwner(
            AuthenticatedCaller caller, Long invoiceId, InvoiceOwnerUpdateRequest request);
    Result<Void> updateOwners(AuthenticatedCaller caller, InvoiceBatchOwnerUpdateRequest request);
    Result<InvoiceVO> issue(
            AuthenticatedCaller caller, Long invoiceId, InvoiceIssueRequest request);
    Result<InvoiceVO> reset(AuthenticatedCaller caller, Long invoiceId, Long version);
    Result<Void> resetBatch(AuthenticatedCaller caller, InvoiceBatchResetRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long invoiceId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, InvoiceBatchDeleteRequest request);
    Result<InvoiceProfileVO> createProfile(
            AuthenticatedCaller caller, InvoiceProfileRequest request);
    Result<InvoiceProfileVO> updateProfile(
            AuthenticatedCaller caller, Long profileId, InvoiceProfileUpdateRequest request);
    Result<Void> deleteProfile(
            AuthenticatedCaller caller, Long profileId, Long version);
}
