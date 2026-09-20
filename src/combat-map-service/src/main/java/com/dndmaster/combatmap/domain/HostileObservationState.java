package com.dndmaster.combatmap.domain;

import java.util.Objects;

/** 적이 특정 플레이어 토큰을 마지막으로 인지한 상태의 지도 정본이다. */
public record HostileObservationState(TokenId hostileTokenId, TokenId playerTokenId,
        HostileObservationStatus status) {
    public HostileObservationState {
        Objects.requireNonNull(hostileTokenId, "hostile token id must not be null");
        Objects.requireNonNull(playerTokenId, "player token id must not be null");
        Objects.requireNonNull(status, "hostile observation status must not be null");
    }
}
