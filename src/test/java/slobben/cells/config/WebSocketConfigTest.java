package slobben.cells.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import slobben.cells.service.workers.ClientService;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private final WebSocketConfig config = new WebSocketConfig();
    private final ClientService clientService = mock(ClientService.class);
    private final Map<String, Object> sessionAttributes = new HashMap<>();

    WebSocketConfigTest() {
        ReflectionTestUtils.setField(config, "clientService", clientService);
    }

    @Test
    void letsTheFrontendThrough() {
        // prepare
        Message<?> connect = connect(5000, 2000);
        Message<?> subscribe = stomp(StompCommand.SUBSCRIBE, "/user/queue/blocks");
        Message<?> update = stomp(StompCommand.SEND, "/client-update");
        Message<?> heartbeat = MessageBuilder.createMessage(new byte[0], StompHeaderAccessor.createForHeartbeat().getMessageHeaders());
        Message<?> disconnect = stomp(StompCommand.DISCONNECT, null);

        // execute and verify
        for (Message<?> message : new Message<?>[]{connect, subscribe, update, heartbeat, disconnect}) {
            assertThat(config.preSend(message, null)).isSameAs(message);
        }
        verifyNoInteractions(clientService);
    }

    @Test
    void closesASessionThatSubscribesToOtherQueues() {
        assertRejected(stomp(StompCommand.SUBSCRIBE, "/queue/**"));
        assertRejected(stomp(StompCommand.SUBSCRIBE, "/queue/blocks-user1"));
    }

    @Test
    void closesASessionThatSubscribesTwice() {
        // prepare
        config.preSend(stomp(StompCommand.SUBSCRIBE, "/user/queue/blocks"), null);

        // execute and verify
        assertRejected(stomp(StompCommand.SUBSCRIBE, "/user/queue/blocks"));
    }

    @Test
    void closesASessionThatSendsToAQueue() {
        assertRejected(stomp(StompCommand.SEND, "/queue/blocks-user1"));
    }

    @Test
    void closesAConnectionThatWontSendHeartbeats() {
        assertRejected(connect(0, 0));
        assertRejected(connect(60_000, 0));
    }

    private void assertRejected(Message<?> message) {
        clearInvocations(clientService);
        assertThat(config.preSend(message, null)).isNull();
        verify(clientService).close(eq("s1"), anyString());
    }

    private Message<?> connect(long clientSends, long clientExpects) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        accessor.setHeartbeat(clientSends, clientExpects);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> stomp(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("s1");
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
