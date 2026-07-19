package com.dndmaster.aigamemaster.infrastructure.ai;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.web.client.HttpClientErrorException;

final class SpringAiProviderExceptionMapper {
    private SpringAiProviderExceptionMapper() {
    }

    static RuntimeException map(RuntimeException exception) {
        if (exception instanceof ProviderTimeoutException || exception instanceof ProviderRateLimitException) {
            return exception;
        }
        if (hasCause(exception, TimeoutException.class) || hasCause(exception, SocketTimeoutException.class)) {
            return new ProviderTimeoutException(exception);
        }
        if (exception instanceof HttpClientErrorException.TooManyRequests) {
            return new ProviderRateLimitException();
        }
        return exception;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> expectedType) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
