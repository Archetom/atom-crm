package io.github.archetom.crm.api.dto.request;


import lombok.Data;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Shared bounded pagination input for public query requests. */
@Data
@Accessors(chain = true)
public class QueryRequest {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_PAGE = 1_000_000;
    public static final int MAX_SIZE = 200;

    /** One-based page number. */
    @Min(value = 1, message = "Page must be at least 1")
    @Max(value = MAX_PAGE, message = "Page exceeds the supported limit")
    protected Integer page = DEFAULT_PAGE;

    /** Maximum number of records returned in one page. */
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = MAX_SIZE, message = "Page size must not exceed 200")
    protected Integer size = DEFAULT_SIZE;
}
