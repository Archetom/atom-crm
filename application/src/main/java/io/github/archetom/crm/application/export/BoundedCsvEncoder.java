package io.github.archetom.crm.application.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Pure, bounded UTF-8 CSV encoder shared only by fixed CRM CSV exports. */
public final class BoundedCsvEncoder {

    private final int maxBytes;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public BoundedCsvEncoder(int maxBytes) {
        if (maxBytes < 3) {
            throw new IllegalArgumentException("CSV byte limit is invalid");
        }
        this.maxBytes = maxBytes;
        write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }

    public void row(List<String> values, List<Boolean> formulaSafe) {
        if (values == null || formulaSafe == null || values.size() != formulaSafe.size()) {
            throw new IllegalStateException("CSV row is invalid");
        }
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                write(",");
            }
            write(cell(values.get(index), Boolean.TRUE.equals(formulaSafe.get(index))));
        }
        write("\r\n");
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }

    private String cell(String value, boolean formulaSafe) {
        String normalized = value == null ? "" : value;
        if (formulaSafe && formulaPrefix(normalized)) {
            normalized = "'" + normalized;
        }
        if (normalized.indexOf(',') >= 0 || normalized.indexOf('"') >= 0
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            return '"' + normalized.replace("\"", "\"\"") + '"';
        }
        return normalized;
    }

    private boolean formulaPrefix(String value) {
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (!(Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.FORMAT)) {
                return codePoint == '=' || codePoint == '+' || codePoint == '-'
                        || codePoint == '@';
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private void write(String value) {
        write(value.getBytes(StandardCharsets.UTF_8));
    }

    private void write(byte[] value) {
        if (value.length > maxBytes - output.size()) {
            throw new CsvLimitExceededException();
        }
        output.writeBytes(value);
    }

    public static final class CsvLimitExceededException extends RuntimeException {
    }
}
