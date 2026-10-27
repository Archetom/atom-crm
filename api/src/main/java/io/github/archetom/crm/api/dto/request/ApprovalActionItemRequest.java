package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ApprovalActionItemRequest {
    @NotNull
    @Positive
    private Long instanceId;
    @NotNull
    @Min(0)
    private Long version;
    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, implementation = ApprovalAction.class)
    private ApprovalAction action;
    @Positive
    @Schema(description = "Required only when action is TRANSFER; the server revalidates its eligibility.")
    private Long transferActorId;
    @Size(max = 1000)
    private String remark;

    @JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "transferActorId must match action")
    public boolean isTransferTargetConsistent() {
        return action == null || action == ApprovalAction.TRANSFER
                ? transferActorId != null : transferActorId == null;
    }

    public ApprovalActionItemRequest setAction(String action) {
        this.action = action == null ? null : ApprovalAction.valueOf(action);
        return this;
    }

    public ApprovalActionItemRequest setAction(ApprovalAction action) {
        this.action = action;
        return this;
    }
}
