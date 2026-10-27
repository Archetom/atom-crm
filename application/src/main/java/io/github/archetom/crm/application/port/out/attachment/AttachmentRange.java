package io.github.archetom.crm.application.port.out.attachment;

/** Already-normalized one-range selection. */
public record AttachmentRange(long offset, long length, long total) {
    public AttachmentRange {
        if (offset < 0 || length <= 0 || total <= 0 || offset > total - length) {
            throw new IllegalArgumentException("attachment range is invalid");
        }
    }
}
