import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Disposable canary helper: observes only the StasisStart type and never writes event content. */
public final class AsteriskStasisProbe implements WebSocket.Listener {
    private final CompletableFuture<Boolean> observed = new CompletableFuture<>();
    private final StringBuilder fragment = new StringBuilder();
    private int stasisStarts;
    private int bridgeEntries;
    private boolean twoLegsReady;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) System.exit(64);
        String password = System.getenv("B22_ARI_PASSWORD");
        if (password == null || password.isEmpty()) System.exit(65);
        AsteriskStasisProbe listener = new AsteriskStasisProbe();
        String basic = Base64.getEncoder().encodeToString(
                ("b22canary:" + password).getBytes(StandardCharsets.UTF_8));
        WebSocket socket = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .newWebSocketBuilder().header("Authorization", "Basic " + basic)
                .header("Origin", "http://127.0.0.1")
                .buildAsync(URI.create(args[0]), listener).get(5, TimeUnit.SECONDS);
        System.out.println("READY");
        boolean seen = listener.observed.get(12, TimeUnit.SECONDS);
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "complete").get(2, TimeUnit.SECONDS);
        if (!seen) System.exit(66);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        fragment.append(data);
        if (last) {
            if (fragment.indexOf("StasisStart") >= 0) {
                stasisStarts++;
            }
            if (fragment.indexOf("ChannelEnteredBridge") >= 0) {
                bridgeEntries++;
            }
            if (stasisStarts >= 2 && !twoLegsReady) {
                twoLegsReady = true;
                System.out.println("TWO_LEGS_READY");
            }
            if (stasisStarts >= 2 && bridgeEntries >= 2) {
                observed.complete(true);
            }
            fragment.setLength(0);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }
}
