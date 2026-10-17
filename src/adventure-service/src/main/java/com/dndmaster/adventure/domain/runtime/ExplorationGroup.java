package com.dndmaster.adventure.domain.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A party unit with one exploration token and member-specific formation. */
public record ExplorationGroup(UUID id, UUID mapTokenId, List<Member> members) {
    public enum Formation { FRONT, MIDDLE, REAR }

    public record Member(UUID characterSheetId, Formation formation, int speed) {
        @JsonCreator public Member(@JsonProperty("characterSheetId") UUID characterSheetId,
                @JsonProperty("formation") Formation formation, @JsonProperty("speed") int speed) {
            this.characterSheetId = Objects.requireNonNull(characterSheetId, "character sheet id must not be null");
            this.formation = Objects.requireNonNull(formation, "formation must not be null");
            if (speed < 0) throw new IllegalArgumentException("speed must not be negative");
            this.speed = speed;
        }
    }

    @JsonCreator public ExplorationGroup(@JsonProperty("id") UUID id,
            @JsonProperty("mapTokenId") UUID mapTokenId, @JsonProperty("members") List<Member> members) {
        this.id = Objects.requireNonNull(id, "group id must not be null");
        this.mapTokenId = Objects.requireNonNull(mapTokenId, "map token id must not be null");
        this.members = List.copyOf(Objects.requireNonNull(members, "members must not be null"));
        if (this.members.isEmpty()) throw new IllegalArgumentException("group must have at least one member");
        Set<UUID> ids = new HashSet<>();
        if (this.members.stream().anyMatch(member -> !ids.add(member.characterSheetId())))
            throw new IllegalArgumentException("group members must be unique");
    }

    @JsonProperty(value = "speed", access = JsonProperty.Access.READ_ONLY)
    public int speed() { return members.stream().mapToInt(Member::speed).min().orElseThrow(); }
}
