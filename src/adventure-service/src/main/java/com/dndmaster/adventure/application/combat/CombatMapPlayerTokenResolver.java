package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import java.util.Objects;
import java.util.UUID;

/** Resolves the player-facing map token to the party member allowed to move it. */
public final class CombatMapPlayerTokenResolver {
    private CombatMapPlayerTokenResolver() { }

    public static AdventurePartyMember resolve(Adventure adventure, UUID ownerPlayerId, UUID tokenId,
            CombatMapViewPort mapViewPort) {
        Objects.requireNonNull(adventure, "adventure must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(mapViewPort, "combat map view port must not be null");
        if (tokenId == null) {
            return adventure.party().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("map action requires a party member"));
        }
        AdventurePartyMember canonical = adventure.party().stream()
                .filter(candidate -> matchesCanonicalToken(candidate, tokenId))
                .findFirst().orElse(null);
        if (canonical != null) return canonical;

        boolean isOwnedPlayerToken = mapViewPort.playerView(adventure.id().value(), ownerPlayerId)
                .map(view -> view.tokens().stream().anyMatch(token -> tokenId.equals(token.id()) && "PLAYER".equals(token.type())))
                .orElse(false);
        if (isOwnedPlayerToken && adventure.party().size() == 1) {
            return adventure.party().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("map action requires a party member"));
        }
        if (isOwnedPlayerToken) {
            throw new IllegalArgumentException("legacy player token cannot be mapped to a party member");
        }
        throw new IllegalArgumentException("map action token does not belong to the party");
    }

    private static boolean matchesCanonicalToken(AdventurePartyMember member, UUID tokenId) {
        UUID sheetId = member.characterSheetId().value();
        return sheetId.equals(tokenId)
                || UUID.nameUUIDFromBytes(("player-" + sheetId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(tokenId);
    }
}
