package com.dndmaster.aigamemaster.api;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;import java.util.Objects;import reactor.core.publisher.Flux;
public final class AiStreamingResponseService{private final SpringAiChatAdapter adapter;public AiStreamingResponseService(SpringAiChatAdapter adapter){this.adapter=Objects.requireNonNull(adapter);}public Flux<String> stream(String operationId,String groundedPrompt){return adapter.stream(operationId,groundedPrompt);}}
