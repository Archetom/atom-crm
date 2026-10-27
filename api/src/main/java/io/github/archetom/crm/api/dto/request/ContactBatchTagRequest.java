package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ContactBatchTagRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@Valid ContactVersionRequest> contacts;
    @Size(max = 50)
    private List<@Size(max = 50) String> addedTags;
    @Size(max = 50)
    private List<@Size(max = 50) String> removedTags;
}
