package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.io.IOException;
import java.io.InputStream;
import java.net.StandardProtocolFamily;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Private ClamAV clamd INSTREAM client.  It accepts only the exact input stream supplied by the
 * application port: no pathname, URL, scanner detail or native response crosses this boundary.
 */
public final class ClamdInstreamStreamingInspectionEngine implements StreamingInspectionEngine {
    private static final int MAX_REPLY_BYTES = 4 * 1024;
    private final SecureLocalAttachmentProperties.Clamd configuration;
    private final boolean canaryLoopbackAllowed;

    public ClamdInstreamStreamingInspectionEngine(SecureLocalAttachmentProperties.Clamd configuration) {
        this(configuration, false);
    }

    static ClamdInstreamStreamingInspectionEngine forLoopbackCanary(SecureLocalAttachmentProperties.Clamd configuration) {
        return new ClamdInstreamStreamingInspectionEngine(configuration, true);
    }

    private ClamdInstreamStreamingInspectionEngine(SecureLocalAttachmentProperties.Clamd configuration,
                                                    boolean canaryLoopbackAllowed) {
        if (configuration == null || (configuration.getUnixSocket() == null && (!canaryLoopbackAllowed
                || configuration.getLoopbackTcpPort() == null || configuration.getLoopbackTcpPort() < 1
                || configuration.getLoopbackTcpPort() > 65535))
                || configuration.getChunkBytes() == null || configuration.getChunkBytes() < 1024
                || configuration.getChunkBytes() > 1024 * 1024
                || invalid(configuration.getConnectTimeout()) || invalid(configuration.getResponseTimeout())
                || invalid(configuration.getDefinitionEvidenceTtl())) {
            throw new IllegalArgumentException("clamd INSTREAM configuration is incomplete");
        }
        this.configuration = configuration;
        this.canaryLoopbackAllowed = canaryLoopbackAllowed;
    }

    @Override
    public Evidence evidence() {
        try {
            String version = command("zVERSION\0".getBytes(StandardCharsets.US_ASCII));
            // VERSION contains the engine/database generation; the deployment report additionally
            // fences freshness. Some supported clamd releases do not expose SELFCHECK, so a
            // fabricated self-check command must not become an accidental availability gate.
            if (!isHealthyVersion(version)) {
                throw new IOException("clamd definition evidence is unavailable");
            }
            Instant observedAt = Instant.now();
            return new Evidence(canaryLoopbackAllowed ? "CANARY_LOOPBACK_ONLY"
                    : SecureLocalAttachmentConformanceManifest.runtimeAdapterDigest(), digest(version),
                    AttachmentContentScanner.DefinitionBasis.PINNED_DEFINITIONS, observedAt,
                    observedAt.plus(configuration.getDefinitionEvidenceTtl()));
        } catch (IOException exception) {
            throw new IllegalStateException("clamd definition evidence is unavailable", exception);
        }
    }

    @Override
    public AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence evidence) throws Exception {
        if (input == null || evidence == null) {
            throw new IllegalArgumentException("clamd inspection subject is missing");
        }
        Evidence scanEvidence = evidence();
        try (SocketChannel channel = connect()) {
            long deadline = deadline(configuration.getResponseTimeout());
            write(channel, "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII), deadline);
            byte[] chunk = new byte[configuration.getChunkBytes()];
            long total = 0;
            for (int count; (count = input.read(chunk)) != -1;) {
                if (count == 0) continue;
                total = Math.addExact(total, count);
                if (total > evidence.actualBytes()) {
                    return failed(scanEvidence, "SCANNER_SUBJECT_MISMATCH");
                }
                write(channel, ByteBuffer.allocate(Integer.BYTES).putInt(count).array(), deadline);
                write(channel, chunk, 0, count, deadline);
            }
            if (total != evidence.actualBytes()) {
                return failed(scanEvidence, "SCANNER_SUBJECT_MISMATCH");
            }
            write(channel, new byte[Integer.BYTES], deadline);
            String reply = readReply(channel, deadline);
            if (reply.endsWith(" OK")) {
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.NO_THREAT_DETECTED,
                        scanEvidence.definitionBasis(), scanEvidence.definitionGeneration(), null);
            }
            if (reply.contains(" FOUND")) {
                return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.THREAT_DETECTED,
                        scanEvidence.definitionBasis(), scanEvidence.definitionGeneration(), null);
            }
            return failed(scanEvidence, classify(reply));
        } catch (IOException | ArithmeticException exception) {
            return failed(scanEvidence, "SCANNER_UNAVAILABLE");
        }
    }

    private SocketChannel connect() throws IOException {
        SocketChannel channel;
        if (configuration.getUnixSocket() == null) {
            if (!canaryLoopbackAllowed) throw new IOException("clamd TCP is not a production transport");
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            connect(channel, new InetSocketAddress("127.0.0.1", configuration.getLoopbackTcpPort()));
            return channel;
        }
        channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        channel.configureBlocking(false);
        connect(channel, UnixDomainSocketAddress.of(configuration.getUnixSocket()));
        return channel;
    }

    private String command(byte[] request) throws IOException {
        try (SocketChannel channel = connect()) {
            long deadline = deadline(configuration.getResponseTimeout());
            write(channel, request, deadline);
            return readReply(channel, deadline);
        }
    }

    private void connect(SocketChannel channel, java.net.SocketAddress address) throws IOException {
        if (!channel.connect(address)) {
            await(channel, SelectionKey.OP_CONNECT, deadline(configuration.getConnectTimeout()));
            if (!channel.finishConnect()) throw new IOException("clamd connection is incomplete");
        }
    }

    private static void write(SocketChannel channel, byte[] bytes, long deadline) throws IOException {
        write(channel, bytes, 0, bytes.length, deadline);
    }

    private static void write(SocketChannel channel, byte[] bytes, int offset, int length, long deadline) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length);
        while (buffer.hasRemaining()) {
            if (channel.write(buffer) == 0) await(channel, SelectionKey.OP_WRITE, deadline);
        }
    }

    private static String readReply(SocketChannel channel, long deadline) throws IOException {
        byte[] reply = new byte[MAX_REPLY_BYTES];
        ByteBuffer one = ByteBuffer.allocate(1);
        int end = 0;
        for (; end < reply.length; end++) {
            int count = channel.read(one);
            if (count == 0) {
                await(channel, SelectionKey.OP_READ, deadline);
                end--;
                continue;
            }
            if (count == -1) throw new IOException("clamd reply is incomplete");
            one.flip();
            int value = Byte.toUnsignedInt(one.get());
            one.clear();
            if (value == 0 || value == '\n') break;
            reply[end] = (byte) value;
        }
        if (end == 0 || end == reply.length) throw new IOException("clamd reply is invalid");
        return new String(reply, 0, end, StandardCharsets.US_ASCII);
    }

    private static void await(SocketChannel channel, int operation, long deadline) throws IOException {
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0) {
            try (Selector selector = Selector.open()) {
                channel.register(selector, operation);
                long millis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
                if (selector.select(millis) > 0) return;
            }
        }
        throw new IOException("clamd operation timed out");
    }

    private static long deadline(Duration timeout) {
        try { return Math.addExact(System.nanoTime(), timeout.toNanos()); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("clamd timeout is too large", exception); }
    }

    private static boolean isHealthyVersion(String reply) { return reply.contains("ClamAV"); }
    private static AttachmentContentScanner.ScanResult failed(Evidence evidence, String reason) {
        return new AttachmentContentScanner.ScanResult(AttachmentContentScanner.ScanVerdict.FAILED,
                evidence.definitionBasis(), evidence.definitionGeneration(), reason);
    }
    private static String classify(String reply) {
        String upper = reply.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains("SIZE LIMIT") || upper.contains("STREAMMAXLENGTH")) return "SCANNER_LIMIT";
        return "SCANNER_FAILURE";
    }
    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new IllegalStateException("clamd evidence digest is unavailable", exception); }
    }
    private static boolean invalid(Duration value) { return value == null || value.isNegative() || value.isZero(); }
}
