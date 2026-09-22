package com.dndmaster.relay;

import com.dndmaster.relay.application.*;
import com.dndmaster.relay.infrastructure.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RelayConfiguration {
    @Bean Clock relayClock() { return Clock.systemUTC(); }
    @Bean ConnectionLocationRepository connectionLocations(ReactiveStringRedisTemplate redis, Clock clock,
            @Value("${relay.redis-timeout:PT2S}") Duration timeout) { return new RedisConnectionLocationRepository(redis, clock, timeout); }
    @Bean RelayMetrics relayMetrics(MeterRegistry registry, @Value("${relay.instance-id}") String instanceId) { return new MicrometerRelayMetrics(registry, instanceId); }
    @Bean ConnectionLeaseService connectionLeaseService(ConnectionLocationRepository locations) { return new ConnectionLeaseService(locations); }
    @Bean RequestCompletionRegistry requestCompletionRegistry() { return new RequestCompletionRegistry(); }
    @Bean IdentityServicePort identityServicePort(ObjectMapper objectMapper,
            @Value("${relay.identity-access.base-url:http://127.0.0.1:8080/}") URI baseUri,
            @Value("${relay.identity-access.timeout:PT2S}") Duration timeout,
            @Value("${relay.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        return new HttpIdentityServiceAdapter(
                HttpClient.newBuilder().connectTimeout(timeout).build(), baseUri, timeout, objectMapper, internalToken);
    }
    @Bean LocalConnectionManager localConnectionExecutor(ConnectionLeaseService leases, RequestCompletionRegistry completions,
            RelayMetrics metrics, @Value("${relay.execution-timeout:PT3M}") Duration timeout,
            @Value("${relay.max-payload-bytes:16777216}") int maxPayloadBytes) {
        return new LocalConnectionManager(leases, completions, metrics, timeout, maxPayloadBytes);
    }
    @Bean OwnedInstanceClient ownedInstanceClient(WebClient.Builder builder,
            @Value("${relay.peer-token:${RELAY_INTERNAL_SERVICE_TOKEN:${INTERNAL_SERVICE_TOKEN:}}}") String token,
            @Value("${relay.execution-timeout:PT3M}") Duration timeout,
            @Value("${relay.instance-id}") String instanceId) { return new HttpOwnedInstanceClient(builder.build(), token, instanceId, timeout); }
    @Bean ExecutionService relayExecutionService(@Value("${relay.instance-id}") String instanceId, ConnectionLocationRepository locations,
            LocalConnectionExecutor local, OwnedInstanceClient remote, RelayMetrics metrics,
            @Value("${relay.execution-timeout:PT3M}") Duration timeout) {
        return new RelayExecutionDispatcher(instanceId, locations::find, local, remote, metrics, timeout);
    }
}
