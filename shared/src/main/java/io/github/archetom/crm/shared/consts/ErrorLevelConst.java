package io.github.archetom.crm.shared.consts;


/** Severity digits used by the public error-code format. */
public final class ErrorLevelConst {

    private ErrorLevelConst() {
    }
    /**
     * INFO
     */
    public static final String INFO = "1";

    /**
     * WARN
     */
    public static final String WARN = "3";

    /**
     * ERROR
     */
    public static final String ERROR = "5";

    /** Fatal error. */
    public static final String FATAL = "7";
}
