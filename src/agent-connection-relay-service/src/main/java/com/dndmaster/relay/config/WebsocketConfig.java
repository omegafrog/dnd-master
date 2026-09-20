package com.dndmaster.relay.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class WebsocketConfig {

  @Bean
  HandlerMapping webSocketMapping(AgentWebSocketHandler handler) {
    Map<String, WebSocketHandler> routes = Map.of("/ws/agent", handler);
    SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
    mapping.setUrlMap(routes);
    mapping.setOrder(-1);
    return mapping;
  }

  @Bean
  WebSocketHandlerAdapter handlerAdapter() {
    return new WebSocketHandlerAdapter();
  }
}
