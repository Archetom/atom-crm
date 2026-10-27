package io.github.archetom.crm.application.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Process-local, identity-free state for the opt-in anonymous MarketingForm FILE lane. */
@Component
public class PublicMarketingFileRuntimeStatus {
    public enum State { UNKNOWN, READY, FILES_NOT_READY, RUNTIME_FAILURE }

    private final AtomicReference<State> state = new AtomicReference<>(State.UNKNOWN);

    /** A readiness snapshot may establish readiness, but never masks a real adapter failure. */
    public void recordReadinessReady() {
        state.updateAndGet(current -> current == State.RUNTIME_FAILURE ? current : State.READY);
    }

    /** Only a completed storage operation can clear a prior runtime failure. */
    public void recordAdapterSuccess() { state.set(State.READY); }

    public void recordFilesNotReady() { state.set(State.FILES_NOT_READY); }
    public void recordRuntimeFailure() { state.set(State.RUNTIME_FAILURE); }
    public State state() { return state.get(); }
}
