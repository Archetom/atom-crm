package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.github.archetom.crm.api.json.ReturnVisitUpdateRequestSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@JsonSerialize(using = ReturnVisitUpdateRequestSerializer.class)
public class ReturnVisitUpdateRequest {
    @NotNull @PositiveOrZero
    private Long version;
    @Schema(nullable = true, description = "Optional contact relation; send null to clear it")
    @Positive
    private Long contactId;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private boolean contactIdSpecified;
    @Schema(nullable = true, description = "Optional contract relation; send null to clear it")
    @Positive
    private Long contractId;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private boolean contractIdSpecified;
    @NotNull
    private LocalDateTime visitTime;
    @Schema(nullable = true, allowableValues = {"IN_PERSON", "PHONE", "SMS", "EMAIL", "WECHAT"},
            description = "Optional return-visit form; send null to clear it")
    private String visitForm;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private boolean visitFormSpecified;
    @Schema(nullable = true, allowableValues = {"VERY_SATISFIED", "SATISFIED", "NEUTRAL", "DISSATISFIED", "VERY_DISSATISFIED"},
            description = "Optional customer satisfaction; send null to clear it")
    private String satisfaction;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private boolean satisfactionSpecified;
    @Size(max = 2000)
    private String remark;
    @Size(max = 200)
    private Map<@Size(min = 1, max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Map<@Size(min = 1, max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;

    @JsonSetter("contactId")
    public ReturnVisitUpdateRequest setContactId(Long contactId) {
        this.contactId = contactId;
        this.contactIdSpecified = true;
        return this;
    }

    @JsonSetter("contractId")
    public ReturnVisitUpdateRequest setContractId(Long contractId) {
        this.contractId = contractId;
        this.contractIdSpecified = true;
        return this;
    }

    @JsonSetter("visitForm")
    public ReturnVisitUpdateRequest setVisitForm(String visitForm) {
        this.visitForm = visitForm;
        this.visitFormSpecified = true;
        return this;
    }

    @JsonSetter("satisfaction")
    public ReturnVisitUpdateRequest setSatisfaction(String satisfaction) {
        this.satisfaction = satisfaction;
        this.satisfactionSpecified = true;
        return this;
    }

    @JsonIgnore
    public boolean contactIdSpecified() {
        return contactIdSpecified;
    }

    @JsonIgnore
    public boolean contractIdSpecified() {
        return contractIdSpecified;
    }

    @JsonIgnore
    public boolean visitFormSpecified() {
        return visitFormSpecified;
    }

    @JsonIgnore
    public boolean satisfactionSpecified() {
        return satisfactionSpecified;
    }
}
