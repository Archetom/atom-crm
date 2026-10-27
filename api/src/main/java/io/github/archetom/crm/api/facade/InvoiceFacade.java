package io.github.archetom.crm.api.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.InvoiceBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchResetRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceIssueRequest;
import io.github.archetom.crm.api.dto.request.InvoiceOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.InvoiceUpdateRequest;
import io.github.archetom.crm.api.dto.response.InvoiceProfileResponse;
import io.github.archetom.crm.api.dto.response.InvoiceResponse;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;

import java.util.List;

public interface InvoiceFacade {
    Result<Pager<InvoiceResponse>> query(
            AuthenticatedCaller caller, InvoiceQueryRequest request);
    Result<InvoiceResponse> get(AuthenticatedCaller caller, Long invoiceId);
    Result<InvoiceResponse> create(
            AuthenticatedCaller caller, InvoiceCreateRequest request);
    Result<InvoiceResponse> update(
            AuthenticatedCaller caller, Long invoiceId, InvoiceUpdateRequest request);
    Result<InvoiceResponse> updateOwner(
            AuthenticatedCaller caller, Long invoiceId,
            InvoiceOwnerUpdateRequest request);
    Result<Void> updateOwners(AuthenticatedCaller caller, InvoiceBatchOwnerUpdateRequest request);
    Result<InvoiceResponse> issue(
            AuthenticatedCaller caller, Long invoiceId, InvoiceIssueRequest request);
    Result<InvoiceResponse> reset(
            AuthenticatedCaller caller, Long invoiceId, Long version);
    Result<Void> resetBatch(AuthenticatedCaller caller, InvoiceBatchResetRequest request);
    Result<Void> delete(AuthenticatedCaller caller, Long invoiceId, Long version);
    Result<Void> deleteBatch(AuthenticatedCaller caller, InvoiceBatchDeleteRequest request);
    Result<List<InvoiceProfileResponse>> profiles(
            AuthenticatedCaller caller, Long customerId);
    Result<InvoiceProfileResponse> createProfile(
            AuthenticatedCaller caller, InvoiceProfileRequest request);
    Result<InvoiceProfileResponse> updateProfile(
            AuthenticatedCaller caller, Long profileId, InvoiceProfileUpdateRequest request);
    Result<Void> deleteProfile(
            AuthenticatedCaller caller, Long profileId, Long version);
}
