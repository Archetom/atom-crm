package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.OaRequestVO;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.model.ApprovalInstanceStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.Map;

public final class OaRequestAssembler {

    private OaRequestAssembler() {
    }

    public static OaRequestVO toVO(
            OaRequest request,
            ApprovalInstanceStatus approvalStatus,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return new OaRequestVO(request.getId().getValue(),
                request.getFormId().getValue(), request.getFormVersion(),
                request.getApprovalInstanceId().getValue(), approvalStatus.name(),
                WorkflowSnapshotAssembler.visible(request.getFacts(), accesses),
                request.getVersion());
    }
}
