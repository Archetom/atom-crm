package io.github.archetom.crm.shared.consts;

/** Error-type digits used by the public error-code format. */
public final class ErrorTypeConst {

    private ErrorTypeConst() {
    }

    /**
     * system error
     */
    public static final String SYSTEM = "0";

    /**
     * business error
     */
    public static final String BIZ = "1";

    /** Third-party provider error. */
    public static final String THIRD_PARTY = "2";
}
