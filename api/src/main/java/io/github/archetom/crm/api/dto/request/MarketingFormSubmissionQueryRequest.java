package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class MarketingFormSubmissionQueryRequest {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_PAGE = 1_000_000;
    public static final int MAX_SIZE = 100;

    @Min(1)
    @Max(MAX_PAGE)
    private Integer page = DEFAULT_PAGE;

    @Min(1)
    @Max(MAX_SIZE)
    private Integer size = DEFAULT_SIZE;

    private LocalDate from;
    private LocalDate to;
}
