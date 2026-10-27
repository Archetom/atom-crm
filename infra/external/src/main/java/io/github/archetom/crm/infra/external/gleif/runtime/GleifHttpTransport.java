package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Bounded native HTTP transport; it never logs request URIs or response bodies. */
interface GleifHttpTransport {
    Response get(URI uri, Duration timeout);

    record Response(int status, byte[] body) {
        public Response {
            body = body == null ? new byte[0] : body.clone();
        }
    }

    final class JavaHttpTransport implements GleifHttpTransport {
        private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
        private final HttpClient client;

        JavaHttpTransport(HttpClient client) { this.client = client; }

        @Override
        public Response get(URI uri, Duration timeout) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .header("Accept", "application/vnd.api+json")
                        .GET().build();
                HttpResponse<byte[]> response = client.send(request,
                        HttpResponse.BodyHandlers.ofByteArray());
                if (response.body().length > MAX_RESPONSE_BYTES) {
                    throw unavailable();
                }
                return new Response(response.statusCode(), response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw unavailable();
            } catch (IOException | RuntimeException exception) {
                throw unavailable();
            }
        }

        private EnterpriseDataSourceException unavailable() {
            return new EnterpriseDataSourceException(EnterpriseDataSourceError.UNAVAILABLE);
        }
    }
}
