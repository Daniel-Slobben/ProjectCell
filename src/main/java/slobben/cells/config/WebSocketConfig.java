package slobben.cells.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import slobben.cells.service.workers.ClientService;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, ChannelInterceptor {

    // Every tick already sends each client a message, so the server's heartbeat only tells clients to expect
    // one every 2 s. Clients send theirs every 5 s; a session silent for 3 of those is closed.
    private static final long[] HEARTBEAT = {2000, 5000};

    // Spring's own scheduler for the broker; lazy, because this configuration helps create it.
    @Autowired
    @Lazy
    private TaskScheduler messageBrokerTaskScheduler;

    @Autowired
    @Lazy
    private ClientService clientService;

    @Value("${cells.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(HEARTBEAT)
                .setTaskScheduler(messageBrokerTaskScheduler);
        registry.setPreservePublishOrder(true);
        registry.setCacheLimit(100_000);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(200000);
        registration.setSendBufferSizeLimit(16 * 1024 * 1024);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(this);
    }

    // A client may only connect with heartbeats, subscribe once to its own queue and send view updates. Anything
    // else is dropped and the session closed. Without this a client could subscribe to every other session's queue
    // and send frames into it. Not by throwing: with ordered receives that only logs a stack trace.
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // heartbeats have no command
        if (stomp == null || stomp.getCommand() == null) return message;
        boolean allowed = switch (stomp.getCommand()) {
            case CONNECT, STOMP -> stomp.getHeartbeat()[0] > 0 && stomp.getHeartbeat()[0] <= HEARTBEAT[1];
            case SUBSCRIBE -> "/user/queue/blocks".equals(stomp.getDestination())
                    && stomp.getSessionAttributes().putIfAbsent("subscribed", true) == null;
            case SEND -> "/client-update".equals(stomp.getDestination());
            case UNSUBSCRIBE, DISCONNECT -> true;
            default -> false;
        };
        if (allowed) return message;
        clientService.close(stomp.getSessionId(), "Not allowed: %s %s".formatted(stomp.getCommand(), stomp.getDestination()));
        return null;
    }
}
