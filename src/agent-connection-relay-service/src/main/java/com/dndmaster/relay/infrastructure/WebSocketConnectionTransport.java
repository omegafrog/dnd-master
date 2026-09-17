package com.dndmaster.relay.infrastructure;

import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.dndmaster.relay.application.AgentConnectionTransport;
import com.dndmaster.relay.application.RelayExecutionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
public class WebSocketConnectionTransport implements AgentConnectionTransport {

  private final WebSocketSession session;
  private final Sinks.Many<WebSocketMessage> sink;
  private final ObjectMapper ObjectMapper;

  public WebSocketConnectionTransport(WebSocketSession session, ObjectMapper objectMapper) {
    this.session = session;
    this.sink = Sinks.many().unicast().onBackpressureBuffer();
    ObjectMapper = objectMapper;
  }

  @Override
  public Mono<Void> send(RelayExecutionRequest request) {
    return Mono.fromRunnable(() -> {

      WebSocketMessage message = null;
      try {
        message = session.textMessage(ObjectMapper.writeValueAsString(request));
      } catch (JsonProcessingException e) {
        log.error("request : {}", request.toString());
      }
      sink.tryEmitNext(message);
    });

  }

  public Mono<Void> startSend() {
    return session.send(sink.asFlux());
  }
}
