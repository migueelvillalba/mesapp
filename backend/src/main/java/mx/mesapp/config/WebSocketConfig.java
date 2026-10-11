package mx.mesapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.Map;
import java.util.List;

@Configuration @EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String[] allowedOrigins;
    public WebSocketConfig(@org.springframework.beans.factory.annotation.Value("${mesapp.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://192.168.*:*,http://10.*:*,http://172.*:*}") String origins) { this.allowedOrigins=origins.split(","); }
    @Override public void configureMessageBroker(MessageBrokerRegistry config) { config.enableSimpleBroker("/topic"); config.setApplicationDestinationPrefixes("/app"); }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String,Object> attributes) {
                if (!(request instanceof ServletServerHttpRequest servlet)) return false;
                var session=servlet.getServletRequest().getSession(false);
                if(session==null || session.getAttribute("employeeId")==null) return false;
                attributes.put("employeeId",session.getAttribute("employeeId"));attributes.put("role",session.getAttribute("role"));return true;
            }
            @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception exception) { }
        });
    }
    @Override public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message,MessageChannel channel) {
                StompHeaderAccessor accessor=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
                if(accessor!=null && accessor.getCommand()==StompCommand.SUBSCRIBE && "/topic/kitchen".equals(accessor.getDestination())) {
                    Object role=accessor.getSessionAttributes()==null?null:accessor.getSessionAttributes().get("role");
                    if(!List.of("COCINA","MESERO","ADMINISTRADOR").contains(role)) throw new IllegalArgumentException("Este rol no puede consultar la cola de cocina");
                }
                return message;
            }
        });
    }
}
