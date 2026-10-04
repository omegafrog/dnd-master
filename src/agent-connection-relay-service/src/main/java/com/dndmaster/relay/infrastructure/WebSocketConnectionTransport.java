package com.dndmaster.relay.infrastructure;

import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.dndmaster.relay.application.AgentConnectionTransport;
import com.dndmaster.relay.application.AgentConnectionControlMessage;
import com.dndmaster.relay.application.RelayExecutionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

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
    return sendMessage(request);
  }

  @Override
  public Mono<Void> sendConnectionControl(AgentConnectionControlMessage request) {
    return sendMessage(request);
  }

  private Mono<Void> sendMessage(Object request) {
    return Mono.fromCallable(() -> session.textMessage(ObjectMapper.writeValueAsString(request)))
        .doOnNext(message -> sink.tryEmitNext(message))
        .then();
  }

  public Mono<Void> startSend() {
    return session.send(sink.asFlux());
  }
}
