package io.github.archetom.crm.application.automation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rejects a partial or ambiguous first-party automation composition at startup. */
@Component
@Slf4j
public class AutomationHandlerRegistry {
    private final Map<AutomationKind, AutomationHandler> handlers;

    public AutomationHandlerRegistry(List<AutomationHandler> registeredHandlers) {
        EnumMap<AutomationKind, AutomationHandler> indexed = new EnumMap<>(AutomationKind.class);
        for (AutomationHandler handler : registeredHandlers) {
            if (handler == null || handler.kind() == null || indexed.putIfAbsent(handler.kind(), handler) != null) {
                throw new IllegalStateException("automation handler registration is invalid");
            }
        }
        if (indexed.size() != AutomationKind.values().length) {
            throw new IllegalStateException("automation handler catalog is incomplete");
        }
        handlers = Map.copyOf(indexed);
    }

    public AutomationHandler require(AutomationKind kind) {
        AutomationHandler handler = handlers.get(kind);
        if (handler == null) {
            throw new IllegalStateException("automation handler is not registered");
        }
        return handler;
    }

    /** A not-ready kind must not occupy the shared one-run automation pump. */
    public Set<AutomationKind> kindsAcceptingNewRuns() {
        EnumSet<AutomationKind> result = EnumSet.noneOf(AutomationKind.class);
        handlers.forEach((kind, handler) -> {
            try {
                if (handler.acceptsNewRuns()) result.add(kind);
            } catch (RuntimeException exception) {
                // A readiness probe is advisory and must fail closed for only its kind.
                log.warn("Automation handler readiness probe failed: kind={}", kind);
            }
        });
        return Set.copyOf(result);
    }
}
