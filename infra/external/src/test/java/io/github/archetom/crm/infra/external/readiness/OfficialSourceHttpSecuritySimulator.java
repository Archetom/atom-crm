package io.github.archetom.crm.infra.external.readiness;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Test-private B23-O-L3 HTTP/security simulator.
 *
 * <p>The simulator never opens a socket. It gives an adapter replica one synthetic request and
 * one scripted provider response or network failure, persists only per-operation readiness state,
 * and captures redacted projections for the complete O2-11 sink inventory. Provider-native bytes
 * stay ephemeral and are never written to the snapshot store or conformance manifest.</p>
 */
final class OfficialSourceHttpSecuritySimulator {

    static final String FIXTURE_VERSION = "B23-O/2";
    static final String LANE_VERSION = "B23-O-L3-v1";
    static final String SOURCE_PROFILE = "GLEIF_LEI_API_V1";
    static final String CONTRACT_VERSION = "B23-OC-v1";
    static final String ADAPTER_BUILD = "test-build-b23-o-l3-v1";
    static final Instant CHECKED_AT = Instant.parse("2026-07-20T08:00:00Z");
    static final Instant VALID_UNTIL = Instant.parse("2026-07-20T08:05:00Z");

    private static final int MAX_BODY_BYTES = 4_096;
    private static final String EXPECTED_CONTENT_TYPE = "application/vnd.api+json";

    private OfficialSourceHttpSecuritySimulator() {
    }

    enum Tenant {
        ALPHA,
        BETA
    }

    enum Operation {
        SEARCH,
        EXACT
    }

    enum SafeOutcome {
        SUCCESS_EMPTY,
        NO_RESULT,
        LIMITED,
        UNAVAILABLE
    }

    enum SafeReason {
        NONE,
        SOURCE_AUTH_FAILED,
        CONTRACT_DRIFT,
        SOURCE_LIMITED,
        SOURCE_SERVER_ERROR,
        DNS_UNAVAILABLE,
        TLS_UNAVAILABLE,
        REDIRECT_REJECTED,
        CONTENT_TYPE_DRIFT,
        BODY_INVALID,
        BODY_TOO_LARGE,
        SCHEMA_DRIFT,
        IDENTITY_MISMATCH
    }

    enum SnapshotEffect {
        PRESERVED,
        OPERATION_REVOKED
    }

    enum Scenario {
        HTTP_401(
                "O2-09-HTTP-401", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.SOURCE_AUTH_FAILED, SnapshotEffect.OPERATION_REVOKED, 0),
        HTTP_403(
                "O2-09-HTTP-403", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.SOURCE_AUTH_FAILED, SnapshotEffect.OPERATION_REVOKED, 0),
        EXACT_HTTP_404(
                "O2-09-EXACT-404", Operation.EXACT, SafeOutcome.NO_RESULT,
                SafeReason.NONE, SnapshotEffect.PRESERVED, 0),
        SEARCH_HTTP_404(
                "O2-09-SEARCH-404", Operation.SEARCH, SafeOutcome.SUCCESS_EMPTY,
                SafeReason.NONE, SnapshotEffect.PRESERVED, 0),
        HTTP_400(
                "O2-09-HTTP-400", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.CONTRACT_DRIFT, SnapshotEffect.OPERATION_REVOKED, 0),
        HTTP_422(
                "O2-09-HTTP-422", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.CONTRACT_DRIFT, SnapshotEffect.OPERATION_REVOKED, 0),
        HTTP_429(
                "O2-09-HTTP-429", Operation.EXACT, SafeOutcome.LIMITED,
                SafeReason.SOURCE_LIMITED, SnapshotEffect.PRESERVED, 0),
        HTTP_500(
                "O2-09-HTTP-500", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.SOURCE_SERVER_ERROR, SnapshotEffect.PRESERVED, 0),
        HTTP_503(
                "O2-09-HTTP-503", Operation.SEARCH, SafeOutcome.UNAVAILABLE,
                SafeReason.SOURCE_SERVER_ERROR, SnapshotEffect.PRESERVED, 0),
        DNS_FAILURE(
                "O2-09-DNS", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.DNS_UNAVAILABLE, SnapshotEffect.PRESERVED, 0),
        TLS_FAILURE(
                "O2-09-TLS", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.TLS_UNAVAILABLE, SnapshotEffect.PRESERVED, 0),
        REDIRECT(
                "O2-09-REDIRECT", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.REDIRECT_REJECTED, SnapshotEffect.OPERATION_REVOKED, 0),
        CONTENT_TYPE_MISMATCH(
                "O2-09-CONTENT-TYPE", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.CONTENT_TYPE_DRIFT, SnapshotEffect.OPERATION_REVOKED, 0),
        EMPTY_BODY(
                "O2-09-BODY-EMPTY", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.BODY_INVALID, SnapshotEffect.OPERATION_REVOKED, 0),
        OVERSIZED_BODY(
                "O2-09-BODY-OVERSIZED", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.BODY_TOO_LARGE, SnapshotEffect.OPERATION_REVOKED, 0),
        MALFORMED_BODY(
                "O2-09-BODY-MALFORMED", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.BODY_INVALID, SnapshotEffect.OPERATION_REVOKED, 0),
        INVALID_UTF8_BODY(
                "O2-09-BODY-INVALID-UTF8", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.BODY_INVALID, SnapshotEffect.OPERATION_REVOKED, 0),
        SCHEMA_MISMATCH(
                "O2-09-SCHEMA", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.SCHEMA_DRIFT, SnapshotEffect.OPERATION_REVOKED, 0),
        IDENTITY_MISMATCH(
                "O2-09-IDENTITY", Operation.EXACT, SafeOutcome.UNAVAILABLE,
                SafeReason.IDENTITY_MISMATCH, SnapshotEffect.OPERATION_REVOKED, 0),
        /**
         * The control for the failure matrix. Without a response the adapter accepts, nothing
         * reaches the success return in {@code normalize}, the identity comparison is never
         * asked to agree, and an observation count of zero holds for every scenario by
         * construction rather than because the failures produce none.
         */
        HEALTHY_EXACT(
                "O2-09-HEALTHY-EXACT", Operation.EXACT, SafeOutcome.NO_RESULT,
                SafeReason.NONE, SnapshotEffect.PRESERVED, 1);

        private final String scenarioId;
        private final Operation operation;
        private final SafeOutcome expectedOutcome;
        private final SafeReason expectedReason;
        private final SnapshotEffect expectedSnapshotEffect;
        private final int expectedObservationCount;

        Scenario(
                String scenarioId,
                Operation operation,
                SafeOutcome expectedOutcome,
                SafeReason expectedReason,
                SnapshotEffect expectedSnapshotEffect,
                int expectedObservationCount) {
            this.scenarioId = scenarioId;
            this.operation = operation;
            this.expectedOutcome = expectedOutcome;
            this.expectedReason = expectedReason;
            this.expectedSnapshotEffect = expectedSnapshotEffect;
            this.expectedObservationCount = expectedObservationCount;
        }

        String scenarioId() {
            return scenarioId;
        }

        Operation operation() {
            return operation;
        }

        SafeOutcome expectedOutcome() {
            return expectedOutcome;
        }

        SafeReason expectedReason() {
            return expectedReason;
        }

        SnapshotEffect expectedSnapshotEffect() {
            return expectedSnapshotEffect;
        }

        int expectedObservationCount() {
            return expectedObservationCount;
        }
    }

    enum SensitiveSink {
        NATIVE_DTO,
        ORDINARY_OBJECT,
        HTTP_OUTPUT,
        EXCEPTION,
        LOG,
        TRACE,
        METRIC,
        EVENT,
        ACTIVITY,
        CACHE,
        REPORT,
        TEST_ARTIFACT,
        CONFORMANCE_MANIFEST
    }

    enum SinkDisposition {
        REDACTED_DIAGNOSTIC,
        SAFE_RESULT_ONLY,
        SAFE_ERROR_ONLY,
        SAFE_TELEMETRY_ONLY,
        NO_WRITE,
        SAFE_MANIFEST_ONLY
    }

    record OperationResult(
            SafeOutcome outcome,
            SafeReason reason,
            SnapshotEffect snapshotEffect,
            int observationCount
    ) {

        OperationResult {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(snapshotEffect, "snapshotEffect");
            if (observationCount < 0) {
                throw new IllegalArgumentException("observation count must not be negative");
            }
        }
    }

    record ScenarioEvidence(
            String scenarioId,
            boolean passed,
            SafeReason safeReason,
            SafeOutcome safeOutcome,
            SnapshotEffect snapshotEffect,
            int providerCallCount,
            int retryCount,
            int redirectFollowCount,
            int partitionCounter,
            int leakHitCount
    ) {

        ScenarioEvidence {
            if (scenarioId == null || scenarioId.isBlank()
                    || safeReason == null || safeOutcome == null || snapshotEffect == null
                    || providerCallCount < 0 || retryCount < 0 || redirectFollowCount < 0
                    || partitionCounter < 0 || leakHitCount < 0) {
                throw new IllegalArgumentException("scenario evidence is invalid");
            }
        }
    }

    record SinkEvidence(
            SensitiveSink sink,
            SinkDisposition disposition,
            int inspectedCaptureCount,
            int leakHitCount
    ) {

        SinkEvidence {
            if (sink == null || disposition == null
                    || inspectedCaptureCount < 0 || leakHitCount < 0) {
                throw new IllegalArgumentException("sink evidence is invalid");
            }
        }
    }

    record ExecutionEvidence(
            Scenario scenario,
            OperationResult result,
            int providerCallCount,
            int retryCount,
            int redirectFollowCount,
            int partitionCounter,
            boolean currentOperationReadyAfter,
            boolean otherOperationReadyAfter,
            boolean otherTenantOperationReadyAfter,
            CapturedSinks capturedSinks
    ) {

        ExecutionEvidence {
            if (scenario == null || result == null || providerCallCount < 0
                    || retryCount < 0 || redirectFollowCount < 0 || partitionCounter < 0
                    || capturedSinks == null) {
                throw new IllegalArgumentException("execution evidence is invalid");
            }
        }
    }

    static final class SyntheticCanaries {

        static final String EXACT_IDENTITY = "0000SYNTHETICL3FIX90";
        static final String OTHER_IDENTITY = "0000SYNTHETICL3FIX18";
        static final String QUERY = "Synthetic L3 Organization Query";
        static final String REGISTERED_NAME = "Synthetic L3 Organization Ltd";
        static final String ADDRESS = "23 Synthetic Registry Road";
        static final String API_KEY = "l3-api-key-sensitive-canary";
        static final String ACCOUNT = "l3-provider-account-sensitive-canary";
        static final String EGRESS_IP = "192.0.2.23";
        static final String RAW_HEADER = "l3-provider-header-sensitive-canary";
        static final String RECORD_URL = "https://provider.invalid/record/sensitive-canary";
        static final String ETAG = "l3-provider-etag-sensitive-canary";
        static final String RAW_ERROR = "l3-provider-error-sensitive-canary";
        static final String RAW_BODY = "l3-provider-body-sensitive-canary";

        private SyntheticCanaries() {
        }

        static Set<String> all() {
            return Set.of(EXACT_IDENTITY, OTHER_IDENTITY, QUERY, REGISTERED_NAME, ADDRESS,
                    API_KEY, ACCOUNT, EGRESS_IP, RAW_HEADER, RECORD_URL, ETAG,
                    RAW_ERROR, RAW_BODY);
        }
    }

    static final class World {

        private final DurableOperationSnapshotStore snapshotStore;
        private final SyntheticHttpSource source = new SyntheticHttpSource();

        World(Path directory) {
            Objects.requireNonNull(directory, "directory");
            snapshotStore = new DurableOperationSnapshotStore(
                    directory.resolve("operation-readiness.properties"));
            snapshotStore.initialize();
        }

        AdapterReplica replica(String replicaId) {
            return new AdapterReplica(replicaId, snapshotStore, source);
        }

        boolean isReady(Tenant tenant, Operation operation) {
            return snapshotStore.isReady(tenant, operation);
        }

        int providerCallCount() {
            return source.providerCallCount();
        }

        int redirectFollowCount() {
            return source.redirectFollowCount();
        }

        /**
         * Fault injection for the negative controls. O2-09 claims the adapter neither follows a
         * redirect nor retries the provider; without a way to do both, the counters that carry
         * those claims report zero whatever the adapter does.
         */
        void injectRedirectFollowing() {
            source.injectRedirectFollowing();
        }

        void injectProviderRetry() {
            source.injectProviderRetry();
        }
    }

    static final class AdapterReplica {

        private final String replicaId;
        private final DurableOperationSnapshotStore snapshotStore;
        private final SyntheticHttpSource source;

        AdapterReplica(
                String replicaId,
                DurableOperationSnapshotStore snapshotStore,
                SyntheticHttpSource source) {
            if (replicaId == null || replicaId.isBlank()
                    || snapshotStore == null || source == null) {
                throw new IllegalArgumentException("adapter replica is invalid");
            }
            this.replicaId = replicaId;
            this.snapshotStore = snapshotStore;
            this.source = source;
        }

        ExecutionEvidence execute(Tenant tenant, Scenario scenario) {
            Objects.requireNonNull(tenant, "tenant");
            Objects.requireNonNull(scenario, "scenario");
            Operation operation = scenario.operation();
            CapturedSinks sinks = CapturedSinks.completeInventory();
            if (!snapshotStore.isReady(tenant, operation)) {
                OperationResult notReady = new OperationResult(
                        SafeOutcome.UNAVAILABLE, SafeReason.CONTRACT_DRIFT,
                        SnapshotEffect.PRESERVED, 0);
                captureSafeSinks(sinks, null, null, notReady, null);
                return new ExecutionEvidence(scenario, notReady, 0, 0, 0, 0,
                        false, snapshotStore.isReady(tenant, otherOperation(operation)),
                        snapshotStore.isReady(otherTenant(tenant), operation), sinks);
            }

            int callsBefore = source.providerCallCount();
            int followsBefore = source.redirectFollowCount();
            int partitionCallsBefore = source.partitionCalls(tenant);
            NativeHttpRequest request = NativeHttpRequest.synthetic(operation);
            RawHttpResponse response = null;
            SafeSourceFailureException safeFailure = null;
            OperationResult result;
            try {
                response = source.exchange(tenant, operation, scenario, request, replicaId);
                result = normalize(operation, request, response);
            } catch (SyntheticNetworkException exception) {
                safeFailure = switch (exception.failure()) {
                    case DNS -> safeFailure(
                            SafeReason.DNS_UNAVAILABLE, SnapshotEffect.PRESERVED);
                    case TLS -> safeFailure(
                            SafeReason.TLS_UNAVAILABLE, SnapshotEffect.PRESERVED);
                };
                result = resultFrom(safeFailure);
            } catch (SafeSourceFailureException exception) {
                if (source.retriesOnce()
                        && exception.reason() == SafeReason.SOURCE_SERVER_ERROR) {
                    response = source.exchange(
                            tenant, operation, scenario, request, replicaId);
                }
                safeFailure = exception;
                result = resultFrom(exception);
            }

            if (result.snapshotEffect() == SnapshotEffect.OPERATION_REVOKED) {
                snapshotStore.revoke(tenant, operation);
            }
            captureSafeSinks(sinks, request, response, result, safeFailure);
            int providerCalls = source.providerCallCount() - callsBefore;
            int redirectFollows = source.redirectFollowCount() - followsBefore;
            int partitionCounter = source.partitionCalls(tenant) - partitionCallsBefore;
            int retries = Math.max(0, providerCalls - 1);
            return new ExecutionEvidence(scenario, result, providerCalls, retries,
                    redirectFollows, partitionCounter,
                    snapshotStore.isReady(tenant, operation),
                    snapshotStore.isReady(tenant, otherOperation(operation)),
                    snapshotStore.isReady(otherTenant(tenant), operation), sinks);
        }

        private Operation otherOperation(Operation operation) {
            return operation == Operation.EXACT ? Operation.SEARCH : Operation.EXACT;
        }

        private Tenant otherTenant(Tenant tenant) {
            return tenant == Tenant.ALPHA ? Tenant.BETA : Tenant.ALPHA;
        }

        private OperationResult normalize(
                Operation operation,
                NativeHttpRequest request,
                RawHttpResponse response) {
            int status = response.status();
            if (status == 401 || status == 403) {
                throw safeFailure(
                        SafeReason.SOURCE_AUTH_FAILED, SnapshotEffect.OPERATION_REVOKED);
            }
            if (status == 404) {
                return operation == Operation.EXACT
                        ? new OperationResult(SafeOutcome.NO_RESULT, SafeReason.NONE,
                                SnapshotEffect.PRESERVED, 0)
                        : new OperationResult(SafeOutcome.SUCCESS_EMPTY, SafeReason.NONE,
                                SnapshotEffect.PRESERVED, 0);
            }
            if (status == 400 || status == 422) {
                throw safeFailure(SafeReason.CONTRACT_DRIFT,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            if (status == 429) {
                return new OperationResult(SafeOutcome.LIMITED, SafeReason.SOURCE_LIMITED,
                        SnapshotEffect.PRESERVED, 0);
            }
            if (status >= 300 && status < 400) {
                if (source.followsRedirects()) {
                    source.followRedirect(response.redirectLocation());
                }
                throw safeFailure(SafeReason.REDIRECT_REJECTED,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            if (status >= 500 && status < 600) {
                throw safeFailure(SafeReason.SOURCE_SERVER_ERROR, SnapshotEffect.PRESERVED);
            }
            if (status != 200) {
                throw safeFailure(SafeReason.CONTRACT_DRIFT,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            if (!EXPECTED_CONTENT_TYPE.equalsIgnoreCase(response.contentType())) {
                throw safeFailure(SafeReason.CONTENT_TYPE_DRIFT,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            if (response.body().length == 0) {
                throw safeFailure(SafeReason.BODY_INVALID,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            if (response.body().length > MAX_BODY_BYTES) {
                throw safeFailure(SafeReason.BODY_TOO_LARGE,
                        SnapshotEffect.OPERATION_REVOKED);
            }

            String body = decodeBody(response.body());
            if (!body.startsWith("{") || !body.endsWith("}")) {
                throw safeFailure(SafeReason.BODY_INVALID,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            String identity = exactJsonString(body, "identity");
            String registeredName = exactJsonString(body, "registeredName");
            String address = exactJsonString(body, "address");
            if (!body.contains("\"data\":{") || identity == null
                    || registeredName == null || address == null) {
                throw safeFailure(SafeReason.SCHEMA_DRIFT,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            if (operation == Operation.EXACT && !request.selector().equals(identity)) {
                throw safeFailure(SafeReason.IDENTITY_MISMATCH,
                        SnapshotEffect.OPERATION_REVOKED);
            }
            return new OperationResult(
                    operation == Operation.SEARCH
                            ? SafeOutcome.SUCCESS_EMPTY : SafeOutcome.NO_RESULT,
                    SafeReason.NONE, SnapshotEffect.PRESERVED, 1);
        }

        private String decodeBody(byte[] body) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(body))
                        .toString();
            } catch (CharacterCodingException exception) {
                throw safeFailure(SafeReason.BODY_INVALID,
                        SnapshotEffect.OPERATION_REVOKED);
            }
        }

        private String exactJsonString(String body, String field) {
            String marker = "\"" + field + "\":\"";
            int start = body.indexOf(marker);
            if (start < 0) {
                return null;
            }
            int valueStart = start + marker.length();
            int end = body.indexOf('"', valueStart);
            if (end < valueStart) {
                return null;
            }
            return body.substring(valueStart, end);
        }

        private OperationResult resultFrom(SafeSourceFailureException exception) {
            return new OperationResult(SafeOutcome.UNAVAILABLE, exception.reason(),
                    exception.snapshotEffect(), 0);
        }

        private SafeSourceFailureException safeFailure(
                SafeReason reason, SnapshotEffect snapshotEffect) {
            return new SafeSourceFailureException(reason, snapshotEffect);
        }

        private void captureSafeSinks(
                CapturedSinks sinks,
                NativeHttpRequest request,
                RawHttpResponse response,
                OperationResult result,
                SafeSourceFailureException failure) {
            String nativeDiagnostic = (request == null ? "request=none" : request.toString())
                    + ";" + (response == null ? "response=none" : response.toString());
            sinks.record(SensitiveSink.NATIVE_DTO, nativeDiagnostic);
            sinks.record(SensitiveSink.ORDINARY_OBJECT, result.toString());
            sinks.record(SensitiveSink.HTTP_OUTPUT, publicHttpOutput(result.outcome()));
            sinks.record(SensitiveSink.EXCEPTION,
                    failure == null ? "exception=none" : failure.toString());
            sinks.record(SensitiveSink.LOG,
                    "official_source outcome=" + result.outcome()
                            + " reason=" + result.reason());
            sinks.record(SensitiveSink.TRACE,
                    "official_source.operation result=" + result.outcome());
            sinks.record(SensitiveSink.METRIC,
                    "official_source_result_total{result=" + result.outcome() + "}=1");
            sinks.record(SensitiveSink.EVENT, "NO_WRITE");
            sinks.record(SensitiveSink.ACTIVITY, "NO_WRITE");
            sinks.record(SensitiveSink.CACHE, "NO_WRITE");
            sinks.record(SensitiveSink.REPORT, "NO_WRITE");
            sinks.record(SensitiveSink.TEST_ARTIFACT, "SAFE_MANIFEST_ONLY");
            sinks.record(SensitiveSink.CONFORMANCE_MANIFEST, "SAFE_FIELDS_ONLY");
        }

        private String publicHttpOutput(SafeOutcome outcome) {
            return switch (outcome) {
                case SUCCESS_EMPTY -> "{\"items\":[]}";
                case NO_RESULT -> "{\"error\":\"ENTERPRISE_DATA_NOT_FOUND\"}";
                case LIMITED -> "{\"error\":\"ENTERPRISE_DATA_LIMITED\"}";
                case UNAVAILABLE -> "{\"error\":\"ENTERPRISE_DATA_UNAVAILABLE\"}";
            };
        }
    }

    record NativeHttpRequest(
            Operation operation,
            String selector,
            String query,
            String credential,
            String account,
            String egressIp
    ) {

        static NativeHttpRequest synthetic(Operation operation) {
            return new NativeHttpRequest(operation, SyntheticCanaries.EXACT_IDENTITY,
                    SyntheticCanaries.QUERY, SyntheticCanaries.API_KEY,
                    SyntheticCanaries.ACCOUNT, SyntheticCanaries.EGRESS_IP);
        }

        @Override
        public String toString() {
            return "NativeHttpRequest[redacted]";
        }
    }

    record RawHttpResponse(
            int status,
            String contentType,
            Map<String, String> headers,
            byte[] body,
            String redirectLocation
    ) {

        RawHttpResponse {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        @Override
        public String toString() {
            return "RawHttpResponse[status=" + status + ", content=redacted]";
        }
    }

    static final class SyntheticHttpSource {

        private int providerCallCount;
        private int redirectFollowCount;
        private boolean followRedirects;
        private boolean retryOnce;
        private final Map<Tenant, Integer> partitionCalls = new EnumMap<>(Tenant.class);

        void injectRedirectFollowing() {
            followRedirects = true;
        }

        void injectProviderRetry() {
            retryOnce = true;
        }

        boolean followsRedirects() {
            return followRedirects;
        }

        boolean retriesOnce() {
            return retryOnce;
        }

        /**
         * Counted separately from the provider call total so that a followed redirect and a
         * retried call each fail their own assertion instead of one masking the other.
         */
        void followRedirect(String location) {
            if (location == null || location.isBlank()) {
                throw new IllegalStateException("redirect carried no location to follow");
            }
            redirectFollowCount++;
        }

        RawHttpResponse exchange(
                Tenant tenant,
                Operation operation,
                Scenario scenario,
                NativeHttpRequest request,
                String replicaId) {
            Objects.requireNonNull(tenant, "tenant");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(scenario, "scenario");
            Objects.requireNonNull(request, "request");
            if (replicaId == null || replicaId.isBlank()) {
                throw new IllegalArgumentException("replica id is invalid");
            }
            providerCallCount++;
            partitionCalls.merge(tenant, 1, Integer::sum);
            return switch (scenario) {
                case DNS_FAILURE -> throw new SyntheticNetworkException(NetworkFailure.DNS);
                case TLS_FAILURE -> throw new SyntheticNetworkException(NetworkFailure.TLS);
                case HTTP_401 -> response(401, EXPECTED_CONTENT_TYPE, errorBody());
                case HTTP_403 -> response(403, EXPECTED_CONTENT_TYPE, errorBody());
                case EXACT_HTTP_404, SEARCH_HTTP_404 ->
                        response(404, EXPECTED_CONTENT_TYPE, errorBody());
                case HTTP_400 -> response(400, EXPECTED_CONTENT_TYPE, errorBody());
                case HTTP_422 -> response(422, EXPECTED_CONTENT_TYPE, errorBody());
                case HTTP_429 -> response(429, EXPECTED_CONTENT_TYPE, errorBody());
                case HTTP_500 -> response(500, EXPECTED_CONTENT_TYPE, errorBody());
                case HTTP_503 -> response(503, EXPECTED_CONTENT_TYPE, errorBody());
                case REDIRECT -> new RawHttpResponse(302, EXPECTED_CONTENT_TYPE,
                        sensitiveHeaders(), new byte[0], SyntheticCanaries.RECORD_URL);
                case CONTENT_TYPE_MISMATCH ->
                        response(200, "text/html", validBody(SyntheticCanaries.EXACT_IDENTITY));
                case EMPTY_BODY -> response(200, EXPECTED_CONTENT_TYPE, new byte[0]);
                case OVERSIZED_BODY ->
                        response(200, EXPECTED_CONTENT_TYPE, new byte[MAX_BODY_BYTES + 1]);
                case MALFORMED_BODY -> response(200, EXPECTED_CONTENT_TYPE,
                        ("not-json-" + SyntheticCanaries.RAW_BODY)
                                .getBytes(StandardCharsets.UTF_8));
                case INVALID_UTF8_BODY -> response(200, EXPECTED_CONTENT_TYPE,
                        new byte[]{(byte) 0xC3, (byte) 0x28});
                case SCHEMA_MISMATCH -> response(200, EXPECTED_CONTENT_TYPE,
                        ("{\"future\":\"" + SyntheticCanaries.RAW_BODY + "\"}")
                                .getBytes(StandardCharsets.UTF_8));
                case IDENTITY_MISMATCH -> response(200, EXPECTED_CONTENT_TYPE,
                        validBody(SyntheticCanaries.OTHER_IDENTITY));
                case HEALTHY_EXACT -> response(200, EXPECTED_CONTENT_TYPE,
                        validBody(SyntheticCanaries.EXACT_IDENTITY));
            };
        }

        int providerCallCount() {
            return providerCallCount;
        }

        int redirectFollowCount() {
            return redirectFollowCount;
        }

        int partitionCalls(Tenant tenant) {
            return partitionCalls.getOrDefault(tenant, 0);
        }

        private RawHttpResponse response(int status, String contentType, byte[] body) {
            return new RawHttpResponse(status, contentType, sensitiveHeaders(), body, null);
        }

        private Map<String, String> sensitiveHeaders() {
            return Map.of(
                    "ETag", SyntheticCanaries.ETAG,
                    "X-Provider-Diagnostic", SyntheticCanaries.RAW_HEADER);
        }

        private byte[] validBody(String identity) {
            return ("{\"data\":{\"identity\":\"" + identity
                    + "\",\"registeredName\":\"" + SyntheticCanaries.REGISTERED_NAME
                    + "\",\"address\":\"" + SyntheticCanaries.ADDRESS
                    + "\",\"excluded\":\"" + SyntheticCanaries.RAW_BODY + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        private byte[] errorBody() {
            return ("{\"error\":\"" + SyntheticCanaries.RAW_ERROR + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    enum NetworkFailure {
        DNS,
        TLS
    }

    static final class SyntheticNetworkException extends RuntimeException {

        private final NetworkFailure failure;

        SyntheticNetworkException(NetworkFailure failure) {
            super("synthetic network operation failed");
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        NetworkFailure failure() {
            return failure;
        }
    }

    static final class SafeSourceFailureException extends RuntimeException {

        private final SafeReason reason;
        private final SnapshotEffect snapshotEffect;

        SafeSourceFailureException(SafeReason reason, SnapshotEffect snapshotEffect) {
            super("official source operation failed safely");
            this.reason = Objects.requireNonNull(reason, "reason");
            this.snapshotEffect = Objects.requireNonNull(snapshotEffect, "snapshotEffect");
        }

        SafeReason reason() {
            return reason;
        }

        SnapshotEffect snapshotEffect() {
            return snapshotEffect;
        }

        @Override
        public String toString() {
            return "SafeSourceFailureException[reason=" + reason + "]";
        }
    }

    static final class CapturedSinks {

        private final Map<SensitiveSink, String> captures =
                new EnumMap<>(SensitiveSink.class);

        static CapturedSinks completeInventory() {
            CapturedSinks result = new CapturedSinks();
            EnumSet.allOf(SensitiveSink.class).forEach(sink -> result.captures.put(sink, ""));
            return result;
        }

        void record(SensitiveSink sink, String value) {
            captures.put(Objects.requireNonNull(sink, "sink"),
                    Objects.requireNonNull(value, "value"));
        }

        Set<SensitiveSink> sinks() {
            return Set.copyOf(captures.keySet());
        }

        String capture(SensitiveSink sink) {
            return captures.get(sink);
        }
    }

    static final class LeakOracle {

        private LeakOracle() {
        }

        static int hitCount(String value, Set<String> forbiddenValues) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            int hits = 0;
            for (String forbidden : forbiddenValues) {
                int from = 0;
                while (from < value.length()) {
                    int index = value.indexOf(forbidden, from);
                    if (index < 0) {
                        break;
                    }
                    hits++;
                    from = index + forbidden.length();
                }
            }
            return hits;
        }

        static int hitCount(byte[] value, Set<String> forbiddenValues) {
            return hitCount(new String(value, StandardCharsets.UTF_8), forbiddenValues);
        }
    }

    static final class EvidenceCollector {

        private final List<ScenarioEvidence> scenarios = new ArrayList<>();
        private final Map<SensitiveSink, Integer> captureCounts =
                new EnumMap<>(SensitiveSink.class);
        private final Map<SensitiveSink, Integer> leakHits =
                new EnumMap<>(SensitiveSink.class);

        EvidenceCollector() {
            EnumSet.allOf(SensitiveSink.class).forEach(sink -> {
                captureCounts.put(sink, 0);
                leakHits.put(sink, 0);
            });
        }

        void record(ExecutionEvidence execution) {
            Set<String> canaries = SyntheticCanaries.all();
            int scenarioLeakHits = 0;
            if (!execution.capturedSinks().sinks()
                    .equals(EnumSet.allOf(SensitiveSink.class))) {
                throw new IllegalArgumentException("sink inventory is incomplete");
            }
            for (SensitiveSink sink : SensitiveSink.values()) {
                int hits = LeakOracle.hitCount(
                        execution.capturedSinks().capture(sink), canaries);
                captureCounts.merge(sink, 1, Integer::sum);
                leakHits.merge(sink, hits, Integer::sum);
                scenarioLeakHits += hits;
            }
            Scenario scenario = execution.scenario();
            OperationResult result = execution.result();
            boolean passed = result.outcome() == scenario.expectedOutcome()
                    && result.reason() == scenario.expectedReason()
                    && result.snapshotEffect() == scenario.expectedSnapshotEffect()
                    && execution.providerCallCount() == 1
                    && execution.retryCount() == 0
                    && execution.redirectFollowCount() == 0
                    && execution.partitionCounter() == 1
                    && execution.currentOperationReadyAfter()
                            == (scenario.expectedSnapshotEffect() == SnapshotEffect.PRESERVED)
                    && execution.otherOperationReadyAfter()
                    && execution.otherTenantOperationReadyAfter()
                    && result.observationCount() == scenario.expectedObservationCount()
                    && scenarioLeakHits == 0;
            scenarios.add(new ScenarioEvidence(scenario.scenarioId(), passed,
                    result.reason(), result.outcome(), result.snapshotEffect(),
                    execution.providerCallCount(), execution.retryCount(),
                    execution.redirectFollowCount(), execution.partitionCounter(),
                    scenarioLeakHits));
        }

        SecurityConformanceReport report() {
            List<SinkEvidence> sinks = new ArrayList<>();
            for (SensitiveSink sink : SensitiveSink.values()) {
                sinks.add(new SinkEvidence(sink, disposition(sink),
                        captureCounts.get(sink), leakHits.get(sink)));
            }
            return new SecurityConformanceReport(List.copyOf(scenarios), List.copyOf(sinks));
        }

        private SinkDisposition disposition(SensitiveSink sink) {
            return switch (sink) {
                case NATIVE_DTO -> SinkDisposition.REDACTED_DIAGNOSTIC;
                case ORDINARY_OBJECT -> SinkDisposition.SAFE_RESULT_ONLY;
                case HTTP_OUTPUT, EXCEPTION -> SinkDisposition.SAFE_ERROR_ONLY;
                case LOG, TRACE, METRIC -> SinkDisposition.SAFE_TELEMETRY_ONLY;
                case EVENT, ACTIVITY, CACHE, REPORT -> SinkDisposition.NO_WRITE;
                case TEST_ARTIFACT, CONFORMANCE_MANIFEST ->
                        SinkDisposition.SAFE_MANIFEST_ONLY;
            };
        }
    }

    record SecurityConformanceReport(
            List<ScenarioEvidence> scenarios,
            List<SinkEvidence> sinks
    ) {

        SecurityConformanceReport {
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
            sinks = sinks == null ? List.of() : List.copyOf(sinks);
            Set<String> expectedScenarios = EnumSet.allOf(Scenario.class).stream()
                    .map(Scenario::scenarioId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> actualScenarios = scenarios.stream()
                    .map(ScenarioEvidence::scenarioId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<SensitiveSink> actualSinks = sinks.stream()
                    .map(SinkEvidence::sink)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!actualScenarios.equals(expectedScenarios)
                    || scenarios.size() != expectedScenarios.size()
                    || scenarios.stream().anyMatch(evidence -> !evidence.passed()
                            || evidence.leakHitCount() != 0)
                    || !actualSinks.equals(EnumSet.allOf(SensitiveSink.class))
                    || sinks.size() != SensitiveSink.values().length
                    || sinks.stream().anyMatch(evidence -> evidence.leakHitCount() != 0)) {
                throw new IllegalArgumentException(
                        "security conformance evidence is incomplete or unsafe");
            }
        }
    }

    static final class SafeManifestWriter {

        private SafeManifestWriter() {
        }

        static Path write(Path path, SecurityConformanceReport report) {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(report, "report");
            Map<String, String> manifest = manifest(report);
            StringBuilder output = new StringBuilder();
            manifest.forEach((key, value) -> output
                    .append(key)
                    .append('=')
                    .append(value)
                    .append('\n'));
            if (LeakOracle.hitCount(output.toString(), SyntheticCanaries.all()) != 0) {
                throw new IllegalArgumentException("manifest contains sensitive fixture values");
            }
            try {
                Files.createDirectories(path.toAbsolutePath().normalize().getParent());
                Files.writeString(path, output, StandardCharsets.UTF_8);
                return path;
            } catch (IOException exception) {
                throw new IllegalStateException("safe manifest could not be written", exception);
            }
        }

        static Properties read(Path path) {
            try {
                Properties properties = new Properties();
                properties.load(new StringReader(Files.readString(path, StandardCharsets.UTF_8)));
                return properties;
            } catch (IOException exception) {
                throw new IllegalStateException("safe manifest could not be read", exception);
            }
        }

        private static Map<String, String> manifest(SecurityConformanceReport report) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("manifest.schema.version", "B23-O-L3-security-manifest-v1");
            values.put("fixture.version", FIXTURE_VERSION);
            values.put("lane.version", LANE_VERSION);
            values.put("adapter.contract.version", CONTRACT_VERSION);
            values.put("adapter.build", ADAPTER_BUILD);
            values.put("deployment.class", "TEST_ONLY");
            values.put("source.profile", SOURCE_PROFILE);
            values.put("binding.policy.schema.version", "B23-O-binding-v1");
            values.put("production.ready", "false");
            values.put("live.evidence.present", "false");
            values.put("real.values.persisted", "false");
            values.put("provider.documentation.digest",
                    SafeDigest.of("b23-o-l3-provider-documentation-v1"));
            values.put("provider.schema.digest",
                    SafeDigest.of("b23-o-l3-provider-schema-v1"));
            values.put("qualification.mapper.version", "gleif-qualification-v1");
            values.put("qualification.catalog.digest",
                    SafeDigest.of("b23-o-l3-qualification-catalog-v1"));
            values.put("registrar.mapper.version", "not-proven");
            values.put("registrar.catalog.digest",
                    SafeDigest.of("b23-o-l3-registrar-catalog-absent-v1"));
            values.put("supported.operations", "SEARCH,EXACT");
            values.put("supported.jurisdictions", "GB");
            values.put("supported.identifier.types", "LEI");
            values.put("candidate.limit", "20");
            values.put("quota.scope", "SHARED_EGRESS_IDENTITY");
            values.put("quota.window.seconds", "60");
            values.put("quota.provider.ceiling", "60");
            values.put("quota.local.budget", "10");
            values.put("quota.exact.reserve", "2");
            values.put("quota.control.reserve", "1");
            values.put("quota.replica.count", "2");
            values.put("quota.admission.profile.digest",
                    SafeDigest.of("b23-o-l3-admission-profile-v1"));
            values.put("rights.evidence.digest",
                    SafeDigest.of("b23-o-l3-synthetic-rights-evidence-v1"));
            values.put("rights.reviewed.at", CHECKED_AT.toString());
            values.put("rights.valid.until", VALID_UNTIL.toString());
            values.put("usage.mode", "EPHEMERAL_ONLY");
            values.put("checked.at", CHECKED_AT.toString());
            values.put("valid.until", VALID_UNTIL.toString());
            values.put("runner.environment.identity.digest",
                    SafeDigest.of("b23-o-l3-offline-runner-v1"));

            values.put("scenario.count", Integer.toString(report.scenarios().size()));
            for (int index = 0; index < report.scenarios().size(); index++) {
                ScenarioEvidence evidence = report.scenarios().get(index);
                String prefix = "scenario." + index + ".";
                values.put(prefix + "id", evidence.scenarioId());
                values.put(prefix + "passed", Boolean.toString(evidence.passed()));
                values.put(prefix + "safe.reason", evidence.safeReason().name());
                values.put(prefix + "safe.outcome", evidence.safeOutcome().name());
                values.put(prefix + "snapshot.effect", evidence.snapshotEffect().name());
                values.put(prefix + "provider.call.count",
                        Integer.toString(evidence.providerCallCount()));
                values.put(prefix + "retry.count", Integer.toString(evidence.retryCount()));
                values.put(prefix + "redirect.follow.count",
                        Integer.toString(evidence.redirectFollowCount()));
                values.put(prefix + "partition.counter",
                        Integer.toString(evidence.partitionCounter()));
                values.put(prefix + "leak.hit.count",
                        Integer.toString(evidence.leakHitCount()));
            }

            values.put("sink.count", Integer.toString(report.sinks().size()));
            for (int index = 0; index < report.sinks().size(); index++) {
                SinkEvidence evidence = report.sinks().get(index);
                String prefix = "sink." + index + ".";
                values.put(prefix + "name", evidence.sink().name());
                values.put(prefix + "disposition", evidence.disposition().name());
                values.put(prefix + "inspected.capture.count",
                        Integer.toString(evidence.inspectedCaptureCount()));
                values.put(prefix + "leak.hit.count",
                        Integer.toString(evidence.leakHitCount()));
            }
            return values;
        }
    }

    static final class SafeDigest {

        private SafeDigest() {
        }

        static String of(String safeConfigurationIdentity) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(safeConfigurationIdentity.getBytes(StandardCharsets.UTF_8));
                return "sha256:" + java.util.HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }

    private static final class DurableOperationSnapshotStore {

        private static final Map<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

        private final Path path;

        private DurableOperationSnapshotStore(Path path) {
            this.path = path.toAbsolutePath().normalize();
        }

        void initialize() {
            withLock(() -> {
                if (Files.exists(path)) {
                    return null;
                }
                Map<String, String> state = new LinkedHashMap<>();
                for (Tenant tenant : Tenant.values()) {
                    for (Operation operation : Operation.values()) {
                        state.put(key(tenant, operation), "READY");
                    }
                }
                write(state);
                return null;
            });
        }

        boolean isReady(Tenant tenant, Operation operation) {
            return withLock(() -> "READY".equals(read().get(key(tenant, operation))));
        }

        void revoke(Tenant tenant, Operation operation) {
            withLock(() -> {
                Map<String, String> state = read();
                state.put(key(tenant, operation), "REVOKED");
                write(state);
                return null;
            });
        }

        private String key(Tenant tenant, Operation operation) {
            return "snapshot." + tenant.name() + "." + operation.name();
        }

        private Map<String, String> read() {
            try {
                Map<String, String> state = new LinkedHashMap<>();
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    int separator = line.indexOf('=');
                    if (separator < 1) {
                        throw new IllegalStateException("snapshot store is malformed");
                    }
                    state.put(line.substring(0, separator), line.substring(separator + 1));
                }
                return state;
            } catch (IOException exception) {
                throw new IllegalStateException("snapshot store is unavailable", exception);
            }
        }

        private void write(Map<String, String> state) {
            try {
                Files.createDirectories(path.getParent());
                Path temporary = Files.createTempFile(
                        path.getParent(), path.getFileName().toString(), ".commit");
                try {
                    StringBuilder serialized = new StringBuilder();
                    state.forEach((key, value) -> serialized
                            .append(key).append('=').append(value).append('\n'));
                    Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException exception) {
                        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("snapshot store could not commit", exception);
            }
        }

        private <T> T withLock(java.util.function.Supplier<T> action) {
            ReentrantLock lock = JVM_LOCKS.computeIfAbsent(path,
                    ignored -> new ReentrantLock());
            lock.lock();
            try {
                return action.get();
            } finally {
                lock.unlock();
            }
        }
    }
}
