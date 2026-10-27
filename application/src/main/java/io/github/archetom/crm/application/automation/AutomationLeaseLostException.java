package io.github.archetom.crm.application.automation;

/** The claimed run can no longer commit local business state. */
public class AutomationLeaseLostException extends RuntimeException {
    public AutomationLeaseLostException() {
        super("automation lease is no longer current");
    }
}
