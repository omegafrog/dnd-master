package com.dndmaster.relay;

import com.dndmaster.relay.application.*;
import com.dndmaster.relay.infrastructure.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RelayConfiguration {
    @Bean Clock relayClock() { return Clock.systemUTC(); }
    @Bean ConnectionLocationRepository connectionLocations(ReactiveStringRedisTemplate redis, Clock clock) { return new RedisConnectionLocationRepository(redis, clock); }
    @Bean RelayMetrics relayMetrics(MeterRegistry registry, @Value("${relay.instance-id}") String instanceId) { return new MicrometerRelayMetrics(registry, instanceId); }
    @Bean ConnectionLeaseService connectionLeaseService(ConnectionLocationRepository locations, RelayMetrics metrics) { return new ConnectionLeaseService(locations, metrics); }
    @Bean LocalConnectionExecutor localConnectionExecutor() {
        return request -> reactor.core.publisher.Mono.just(RelayExecutionResult.failure(request.requestId(), RelayFailureType.NO_CONNECTION));
    }
    @Bean OwnedInstanceClient ownedInstanceClient(WebClient.Builder builder,
            @Value("${relay.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String token,
            @Value("${relay.execution-timeout:PT3M}") Duration timeout) { return new HttpOwnedInstanceClient(builder.build(), token, timeout); }
    @Bean ExecutionService relayExecutionService(@Value("${relay.instance-id}") String instanceId, ConnectionLocationRepository locations,
            LocalConnectionExecutor local, OwnedInstanceClient remote, RelayMetrics metrics,
            @Value("${relay.execution-timeout:PT3M}") Duration timeout) {
        return new RelayExecutionDispatcher(instanceId, locations::find, local, remote, metrics, timeout);
    }
}
