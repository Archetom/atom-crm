package io.github.archetom.crm.application.automation;

/** A bounded composition-root wake-up hook; durable ownership remains with its owner. */
@FunctionalInterface
public interface BackgroundWorkPump {
    void runOnce();
}
