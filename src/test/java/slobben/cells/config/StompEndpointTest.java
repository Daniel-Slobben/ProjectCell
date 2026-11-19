package slobben.cells.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * The /ws endpoint as a browser or an attacker sees it: raw STOMP frames over a real websocket, so the interceptor, the
 * message handlers and the closing of sessions are tested as Spring wires them. The world is empty, so every update
 * gets NO_BLOCKS and costs 1 of a budget of 20 that doesn't refill.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"cells.runmode=MANUAL",
        "cells.max-cell-blocks=10", "cells.client-blocks-per-second=0", "cells.allowed-origins=https://world-of-cells.net"})
class StompEndpointTest {

    @LocalServerPort
    private int port;

    @Test
    void servesAClientThatFollowsTheRules() throws Exception {
        // prepare
        Socket socket = connected();

        // execute
        socket.send(subscribe("/user/queue/blocks"), update("{\"minX\":0,\"maxX\":0,\"minY\":0,\"maxY\":0}"));

        // verify
        assertThat(socket.next("MESSAGE")).contains("NO_BLOCKS");
        assertThat(socket.closed).isNotDone();
    }

    @Test
    void closesASessionThatSubscribesToEveryQueue() throws Exception {
        assertClosed(connected(), "Not allowed", subscribe("/queue/**"));
    }

    @Test
    void closesASessionThatSendsIntoAQueue() throws Exception {
        assertClosed(connected(), "Not allowed", frame("SEND", "destination:/queue/blocks-user1", "{\"type\":\"NO_BLOCKS\"}"));
    }

    @Test
    void closesASessionThatWontSendHeartbeats() throws Exception {
        assertClosed(open(null), "Not allowed", frame("CONNECT", "accept-version:1.2\nheart-beat:0,0", ""));
    }

    @Test
    void closesASessionThatSendsAnUnreadableUpdate() throws Exception {
        assertClosed(connected(), "Unreadable update", update("{not json"));
        assertClosed(connected(), "Unreadable update", update("{\"minX\":5,\"maxX\":0,\"minY\":0,\"maxY\":0}"));
    }

    @Test
    void closesASessionOverItsBudget() throws Exception {
        assertClosed(connected(), "Too many blocks", update("{\"minX\":0,\"maxX\":0,\"minY\":0,\"maxY\":0}").repeat(21));
    }

    @Test
    void acceptsOnlyItsOwnOrigin() throws Exception {
        assertThat(open("https://world-of-cells.net").session.isOpen()).isTrue();
        assertThatThrownBy(() -> open("https://evil.example")).isInstanceOf(ExecutionException.class);
    }

    private static void assertClosed(Socket socket, String reason, String frames) throws Exception {
        socket.send(frames);
        assertThat(socket.closed.get(5, SECONDS).getCode()).isEqualTo(CloseStatus.PROTOCOL_ERROR.getCode());
        assertThat(socket.next("ERROR")).contains(reason);
    }

    private Socket connected() throws Exception {
        Socket socket = open(null);
        socket.send(frame("CONNECT", "accept-version:1.2\nheart-beat:5000,0", ""));
        socket.next("CONNECTED");
        return socket;
    }

    private Socket open(String origin) throws Exception {
        Socket socket = new Socket();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null) headers.setOrigin(origin);
        socket.session = new StandardWebSocketClient()
                .execute(socket, headers, URI.create("ws://localhost:%d/ws".formatted(port)))
                .get(5, SECONDS);
        return socket;
    }

    private static String subscribe(String destination) {
        return frame("SUBSCRIBE", "id:0\ndestination:" + destination, "");
    }

    private static String update(String json) {
        return frame("SEND", "destination:/client-update\ncontent-type:application/json", json);
    }

    private static String frame(String command, String headers, String body) {
        return command + "\n" + headers + "\n\n" + body + "\0";
    }

    // the text frames a socket got, and how it was closed
    private static final class Socket extends AbstractWebSocketHandler {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        private WebSocketSession session;

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
            if (message instanceof TextMessage text) frames.add(text.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }

        void send(String frames) throws IOException {
            session.sendMessage(new TextMessage(frames));
        }

        void send(String... frames) throws IOException {
            send(String.join("", frames));
        }

        String next(String command) throws InterruptedException {
            for (String frame = frames.poll(5, SECONDS); frame != null; frame = frames.poll(5, SECONDS)) {
                if (frame.startsWith(command)) return frame;
            }
            return fail("No %s frame", command);
        }
    }
}
