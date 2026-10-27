package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentDetector;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** B24a's bounded-magic detector for the fixed PDF/PNG/JPEG public catalog. */
public final class BoundedMagicAttachmentContentDetector implements AttachmentContentDetector {
    @Override
    public Detection detect(AttachmentReadHandle handle, AttachmentContentEvidence evidence) throws Exception {
        byte[] prefix = new byte[16];
        int length = 0;
        try (InputStream input = handle.inputStream()) {
            // InputStream is allowed to return a short successful read even when more bytes
            // are immediately available. Fill the bounded prefix before judging its magic.
            while (length < prefix.length) {
                int count = input.read(prefix, length, prefix.length - length);
                if (count < 0) break;
                if (count == 0) continue;
                length += count;
            }
        }
        if (length < 3) return new Detection(DetectionState.INDETERMINATE, null,
                EvidenceLevel.BOUNDED_MAGIC, "PREFIX_TOO_SHORT");
        String mediaType = null;
        if (length >= 5 && prefix[0] == '%' && prefix[1] == 'P' && prefix[2] == 'D'
                && prefix[3] == 'F' && prefix[4] == '-') mediaType = "application/pdf";
        else if (length >= 8 && (prefix[0] & 0xFF) == 0x89 && prefix[1] == 'P' && prefix[2] == 'N'
                && prefix[3] == 'G' && (prefix[4] & 0xFF) == 0x0D && (prefix[5] & 0xFF) == 0x0A
                && (prefix[6] & 0xFF) == 0x1A && (prefix[7] & 0xFF) == 0x0A) mediaType = "image/png";
        else if ((prefix[0] & 0xFF) == 0xFF && (prefix[1] & 0xFF) == 0xD8 && (prefix[2] & 0xFF) == 0xFF)
            mediaType = "image/jpeg";
        else if (csvPrefix(prefix, length)) mediaType = "text/csv";
        return mediaType == null ? new Detection(DetectionState.INDETERMINATE, null,
                EvidenceLevel.BOUNDED_MAGIC, "UNKNOWN_MAGIC") : new Detection(DetectionState.IDENTIFIED,
                mediaType, EvidenceLevel.BOUNDED_MAGIC, null);
    }

    /** CSV has no file magic; accept only a short, strict UTF-8, comma-delimited text prefix. */
    private static boolean csvPrefix(byte[] prefix, int length) {
        if (length < 4) return false;
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(prefix, 0, length)).toString();
            if (value.indexOf(',') < 0 || value.indexOf('\u0000') >= 0) return false;
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (Character.isISOControl(current) && current != '\r' && current != '\n' && current != '\t') {
                    return false;
                }
            }
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }
}
