package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class SpringAiProviderExceptionMapperTest {
    @Test
    void mapsTimeoutAndRateLimitProviderFailuresToExistingRetryContract() {
        assertInstanceOf(
                ProviderTimeoutException.class,
                SpringAiProviderExceptionMapper.map(new RuntimeException(new TimeoutException("provider timeout"))));
        RuntimeException rateLimited = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", HttpHeaders.EMPTY, new byte[0], null);
        assertInstanceOf(ProviderRateLimitException.class, SpringAiProviderExceptionMapper.map(rateLimited));
    }
}
