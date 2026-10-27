package io.github.archetom.crm.infra.external.call;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable, test-only private-adapter oracle for B22 CP/PC/PD/PM. It deliberately has no ARI,
 * SIP, HTTP controller, provider identity, production bean, telephone value or recording URL.
 * Every persisted field is a fixed digest, state, fence, deadline or encrypted synthetic bytes.
 */
class B22PrivateAdapterDurabilityConformanceTest {
    private static final String CONNECTOR_A = "connector-a";
    private static final String CONNECTOR_B = "connector-b";

    @TempDir Path root;

    @Test
    void cpDurablyFencesDirtyReconnectAndPreservesExactTwoLegProofAcrossRestart() throws Exception {
        DurableAdapterState first = new DurableAdapterState(root.resolve("cp"));
        long oldFence = first.claimOwner(CONNECTOR_A);
        String event = digest("synthetic-stasis-start-agent");
        assertTrue(first.appendJournal(CONNECTOR_A, oldFence, event));
        String session = digest("synthetic-session");
        first.recordTwoLegProof(CONNECTOR_A, oldFence, session, digest("agent-leg"), digest("remote-leg"));

        long newFence = first.claimOwner(CONNECTOR_A);
        assertEquals(oldFence + 1, newFence);
        assertFalse(first.appendJournal(CONNECTOR_A, oldFence, digest("stale-frame")));
        assertTrue(first.appendJournal(CONNECTOR_A, newFence, digest("new-owner-frame")));
        assertThrows(IllegalArgumentException.class, () -> first.recordTwoLegProof(CONNECTOR_A, newFence,
                session, digest("agent-leg"), digest("agent-leg")));
        assertThrows(IllegalArgumentException.class, () -> first.recordTwoLegProof(CONNECTOR_B, 1,
                session, digest("agent-leg"), digest("remote-leg")));

        DurableAdapterState restarted = new DurableAdapterState(root.resolve("cp"));
        assertEquals(newFence, restarted.fence(CONNECTOR_A));
        assertTrue(restarted.hasJournal(CONNECTOR_A, event));
        assertTrue(restarted.hasTwoLegProof(CONNECTOR_A, session));
        assertFalse(restarted.hasTwoLegProof(CONNECTOR_B, session));
    }

    @Test
    void pcDurablyMakesDispatchingAmbiguousAndNeverReinvokesAtMostOnceStart() throws Exception {
        DurableAdapterState first = new DurableAdapterState(root.resolve("pc"));
        String command = digest("command-a");
        String commandDigest = digest("command-digest-a");
        assertEquals(CommandState.RECEIVED, first.receiveCommand(CONNECTOR_A, command, commandDigest));
        assertEquals(CommandState.RECEIVED, first.receiveCommand(CONNECTOR_A, command, commandDigest));
        assertThrows(IllegalArgumentException.class, () -> first.receiveCommand(CONNECTOR_A, command, digest("other")));
        first.markDispatching(CONNECTOR_A, command);
        assertEquals(1, first.providerStartAttempts(CONNECTOR_A, command));

        DurableAdapterState restarted = new DurableAdapterState(root.resolve("pc"));
        assertEquals(CommandState.AMBIGUOUS, restarted.recoverAtMostOnce(CONNECTOR_A, command));
        assertEquals(1, restarted.providerStartAttempts(CONNECTOR_A, command));
        assertEquals(CommandState.AMBIGUOUS, restarted.recoverAtMostOnce(CONNECTOR_A, command));
        assertEquals(1, restarted.providerStartAttempts(CONNECTOR_A, command));
        assertEquals(CommandState.RECEIVED, restarted.receiveCommand(CONNECTOR_B, command, commandDigest));
    }

    @Test
    void pdEncryptsSyntheticSensitiveMaterialPartitionsHmacAndExpiresWithoutPlaintextPersistence() throws Exception {
        Path privateRoot = root.resolve("pd");
        DurableAdapterState state = new DurableAdapterState(privateRoot);
        SensitiveEnvelope envelope = new SensitiveEnvelope(privateRoot.resolve("payload"));
        byte[] syntheticAddress = "synthetic-address-only".getBytes(StandardCharsets.US_ASCII);
        Instant deadline = Instant.now().plusSeconds(5);
        String aFingerprint = envelope.store(CONNECTOR_A, syntheticAddress, deadline);
        String bFingerprint = envelope.fingerprint(CONNECTOR_B, syntheticAddress);
        assertNotEquals(aFingerprint, bFingerprint);
        assertArrayEquals(syntheticAddress, envelope.read(CONNECTOR_A, aFingerprint, Instant.now()));
        assertThrows(SecurityException.class, () -> envelope.read(CONNECTOR_B, aFingerprint, Instant.now()));
        assertFalse(new String(Files.readAllBytes(envelope.file(aFingerprint)), StandardCharsets.ISO_8859_1)
                .contains("synthetic-address-only"));
        state.recordRetention(CONNECTOR_A, aFingerprint, deadline);
        assertEquals(1, state.expireRetention(Instant.now().plusSeconds(6), envelope));
        assertFalse(Files.exists(envelope.file(aFingerprint)));
        assertTrue(state.isRetentionExpired(CONNECTOR_A, aFingerprint));
    }

    @Test
    void pmSupportsOneNativeRangeCancelableReadAndDurableConfirmedRemoval() throws Exception {
        Path privateRoot = root.resolve("pm");
        SensitiveEnvelope envelope = new SensitiveEnvelope(privateRoot.resolve("recordings"));
        byte[] syntheticMedia = "synthetic-wav-bytes".getBytes(StandardCharsets.US_ASCII);
        String ref = envelope.store(CONNECTOR_A, syntheticMedia, Instant.now().plusSeconds(30));
        RecordingProxy proxy = new RecordingProxy(envelope, CONNECTOR_A, ref);

        try (RecordingProxy.Handle full = proxy.open(null, Instant.now())) {
            assertArrayEquals(syntheticMedia, full.input().readAllBytes());
            full.cancel();
        }
        try (RecordingProxy.Handle range = proxy.open(new ByteRange(2, 5), Instant.now())) {
            assertArrayEquals("nthet".getBytes(StandardCharsets.US_ASCII), range.input().readAllBytes());
        }
        int opens = proxy.opens();
        assertThrows(IllegalArgumentException.class, () -> proxy.open(new ByteRange(99, 1), Instant.now()));
        assertEquals(opens, proxy.opens());
        proxy.requestRemove();
        assertFalse(proxy.confirmRemoved());
        envelope.delete(ref);
        assertTrue(proxy.confirmRemoved());
        assertThrows(IllegalStateException.class, () -> proxy.open(null, Instant.now()));
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private enum CommandState { RECEIVED, DISPATCHING, AMBIGUOUS }

    private static final class DurableAdapterState {
        private final Path directory;
        private final Path state;
        private final Map<String, Long> fences = new HashMap<>();
        private final Map<String, Command> commands = new HashMap<>();
        private final Map<String, Boolean> journal = new HashMap<>();
        private final Map<String, Proof> proofs = new HashMap<>();
        private final Map<String, Instant> retention = new HashMap<>();

        private DurableAdapterState(Path directory) throws Exception {
            this.directory = directory;
            this.state = directory.resolve("state");
            Files.createDirectories(directory);
            if (Files.exists(state)) load();
        }

        long claimOwner(String connector) throws Exception {
            long fence = fences.getOrDefault(connector, 0L) + 1;
            fences.put(connector, fence); persist(); return fence;
        }
        long fence(String connector) { return fences.getOrDefault(connector, 0L); }
        boolean appendJournal(String connector, long fence, String eventDigest) throws Exception {
            if (fence(connector) != fence) return false;
            journal.put(connector + ':' + eventDigest, Boolean.TRUE); persist(); return true;
        }
        boolean hasJournal(String connector, String eventDigest) { return journal.containsKey(connector + ':' + eventDigest); }
        void recordTwoLegProof(String connector, long fence, String session, String agentLeg, String remoteLeg) throws Exception {
            if (fence(connector) != fence || agentLeg.equals(remoteLeg)) {
                throw new IllegalArgumentException("two-leg proof is invalid");
            }
            proofs.put(connector + ':' + session, new Proof(agentLeg, remoteLeg)); persist();
        }
        boolean hasTwoLegProof(String connector, String session) { return proofs.containsKey(connector + ':' + session); }
        CommandState receiveCommand(String connector, String command, String commandDigest) throws Exception {
            String key = connector + ':' + command;
            Command existing = commands.get(key);
            if (existing != null) {
                if (!existing.digest.equals(commandDigest)) throw new IllegalArgumentException("command digest conflict");
                return existing.state;
            }
            commands.put(key, new Command(commandDigest, CommandState.RECEIVED, 0)); persist(); return CommandState.RECEIVED;
        }
        void markDispatching(String connector, String command) throws Exception {
            Command existing = command(connector, command);
            if (existing.state != CommandState.RECEIVED) throw new IllegalStateException("command cannot dispatch");
            commands.put(connector + ':' + command, new Command(existing.digest, CommandState.DISPATCHING, existing.starts + 1)); persist();
        }
        CommandState recoverAtMostOnce(String connector, String command) throws Exception {
            Command existing = command(connector, command);
            if (existing.state == CommandState.DISPATCHING) {
                existing = new Command(existing.digest, CommandState.AMBIGUOUS, existing.starts);
                commands.put(connector + ':' + command, existing); persist();
            }
            return existing.state;
        }
        int providerStartAttempts(String connector, String command) { return command(connector, command).starts; }
        void recordRetention(String connector, String ref, Instant deadline) throws Exception {
            retention.put(connector + ':' + ref, deadline); persist();
        }
        int expireRetention(Instant now, SensitiveEnvelope envelope) throws Exception {
            int count = 0;
            for (Map.Entry<String, Instant> entry : new ArrayList<>(retention.entrySet())) {
                if (!entry.getValue().isAfter(now)) { envelope.delete(entry.getKey().substring(entry.getKey().indexOf(':') + 1)); count++; }
            }
            retention.entrySet().removeIf(entry -> !entry.getValue().isAfter(now)); persist(); return count;
        }
        boolean isRetentionExpired(String connector, String ref) { return !retention.containsKey(connector + ':' + ref); }
        private Command command(String connector, String command) {
            Command value = commands.get(connector + ':' + command);
            if (value == null) throw new IllegalStateException("command is unavailable"); return value;
        }
        private void load() throws Exception {
            for (String line : Files.readAllLines(state, StandardCharsets.US_ASCII)) {
                String[] values = line.split("\\|", -1);
                if (values.length == 0) continue;
                if ("F".equals(values[0])) fences.put(values[1], Long.parseLong(values[2]));
                else if ("J".equals(values[0])) journal.put(values[1], Boolean.TRUE);
                else if ("P".equals(values[0])) proofs.put(values[1], new Proof(values[2], values[3]));
                else if ("C".equals(values[0])) commands.put(values[1], new Command(values[2], CommandState.valueOf(values[3]), Integer.parseInt(values[4])));
                else if ("R".equals(values[0])) retention.put(values[1], Instant.parse(values[2]));
                else throw new IOException("durable adapter state is invalid");
            }
        }
        private void persist() throws Exception {
            List<String> lines = new ArrayList<>();
            fences.forEach((key, value) -> lines.add("F|" + key + '|' + value));
            journal.keySet().forEach(key -> lines.add("J|" + key));
            proofs.forEach((key, value) -> lines.add("P|" + key + '|' + value.agentLeg + '|' + value.remoteLeg));
            commands.forEach((key, value) -> lines.add("C|" + key + '|' + value.digest + '|' + value.state + '|' + value.starts));
            retention.forEach((key, value) -> lines.add("R|" + key + '|' + value));
            Files.write(state, lines, StandardCharsets.US_ASCII, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = java.nio.channels.FileChannel.open(state, StandardOpenOption.WRITE)) { channel.force(true); }
        }
        private record Command(String digest, CommandState state, int starts) { }
        private record Proof(String agentLeg, String remoteLeg) { }
    }

    private static final class SensitiveEnvelope {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final Path directory;
        private final Map<String, byte[]> keys = new HashMap<>();
        private SensitiveEnvelope(Path directory) throws Exception { this.directory = directory; Files.createDirectories(directory); }
        String store(String connector, byte[] value, Instant deadline) throws Exception {
            String ref = fingerprint(connector, value);
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(connector), new GCMParameterSpec(128, nonce));
            cipher.updateAAD((connector + '|' + deadline).getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(value);
            Files.write(file(ref), concat(nonce, deadline.toString().getBytes(StandardCharsets.US_ASCII), new byte[] {0}, encrypted),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return ref;
        }
        byte[] read(String connector, String ref, Instant now) throws Exception {
            try {
                byte[] raw = Files.readAllBytes(file(ref));
                int marker = index(raw, (byte) 0, 12);
                Instant deadline = Instant.parse(new String(raw, 12, marker - 12, StandardCharsets.US_ASCII));
                if (!deadline.isAfter(now)) throw new SecurityException("payload expired");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(connector), new GCMParameterSpec(128, copy(raw, 0, 12)));
                cipher.updateAAD((connector + '|' + deadline).getBytes(StandardCharsets.US_ASCII));
                return cipher.doFinal(copy(raw, marker + 1, raw.length - marker - 1));
            } catch (SecurityException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new SecurityException("payload is unavailable", exception);
            }
        }
        String fingerprint(String connector, byte[] value) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(key(connector));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value));
        }
        Path file(String ref) { return directory.resolve(ref); }
        void delete(String ref) throws IOException { Files.deleteIfExists(file(ref)); }
        private SecretKeySpec key(String connector) {
            return new SecretKeySpec(keys.computeIfAbsent(connector, value -> {
                try { return MessageDigest.getInstance("SHA-256").digest(("test-only-key|" + value).getBytes(StandardCharsets.US_ASCII)); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }), "AES");
        }
        private static int index(byte[] bytes, byte value, int offset) {
            for (int i = offset; i < bytes.length; i++) if (bytes[i] == value) return i;
            throw new IllegalArgumentException("envelope is invalid");
        }
        private static byte[] copy(byte[] value, int offset, int length) {
            byte[] result = new byte[length]; System.arraycopy(value, offset, result, 0, length); return result;
        }
        private static byte[] concat(byte[]... values) {
            int length = 0; for (byte[] value : values) length += value.length;
            byte[] result = new byte[length]; int offset = 0;
            for (byte[] value : values) { System.arraycopy(value, 0, result, offset, value.length); offset += value.length; }
            return result;
        }
    }

    private static final class RecordingProxy {
        private final SensitiveEnvelope envelope; private final String connector; private final String ref;
        private boolean removalRequested; private int opens;
        private RecordingProxy(SensitiveEnvelope envelope, String connector, String ref) { this.envelope = envelope; this.connector = connector; this.ref = ref; }
        Handle open(ByteRange range, Instant now) throws Exception {
            if (removalRequested) throw new IllegalStateException("recording is removing");
            byte[] all = envelope.read(connector, ref, now);
            int offset = range == null ? 0 : range.offset;
            int length = range == null ? all.length : range.length;
            if (offset < 0 || length < 1 || offset > all.length - length) throw new IllegalArgumentException("range is invalid");
            opens++; byte[] selected = new byte[length]; System.arraycopy(all, offset, selected, 0, length);
            return new Handle(new ByteArrayInputStream(selected));
        }
        int opens() { return opens; }
        void requestRemove() { removalRequested = true; }
        boolean confirmRemoved() throws IOException { return removalRequested && !Files.exists(envelope.file(ref)); }
        private static final class Handle implements AutoCloseable {
            private final ByteArrayInputStream input; private boolean cancelled;
            private Handle(ByteArrayInputStream input) { this.input = input; }
            ByteArrayInputStream input() { if (cancelled) throw new IllegalStateException("stream cancelled"); return input; }
            void cancel() throws IOException { cancelled = true; input.close(); }
            @Override public void close() throws IOException { input.close(); }
        }
    }
    private record ByteRange(int offset, int length) { }
}
