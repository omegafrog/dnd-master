package com.dndmaster.aigamemaster.infrastructure.ai;
@FunctionalInterface public interface StructuredResponseParser<T>{T parse(String response) throws ProviderMalformedResponseException;}
