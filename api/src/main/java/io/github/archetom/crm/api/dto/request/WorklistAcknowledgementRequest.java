package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class WorklistAcknowledgementRequest {
    @NotNull
    private LocalDate date;
    @Size(max = 40)
    private String kind;
    @Size(max = 100)
    private List<@Valid WorklistItemAcknowledgementRequest> items = List.of();
}
