package com.dndmaster.adventure.domain.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Group membership and formation stored in Adventure's durable world state. */
public record ExplorationState(List<ExplorationGroup> groups, ExplorationCycle cycle) {
    public ExplorationState(List<ExplorationGroup> groups) { this(groups, null); }

    @JsonCreator public ExplorationState(@JsonProperty("groups") List<ExplorationGroup> groups,
            @JsonProperty("cycle") ExplorationCycle cycle) {
        this.groups = List.copyOf(Objects.requireNonNull(groups, "groups must not be null"));
        this.cycle = cycle;
        Set<UUID> groupIds = new HashSet<>(), members = new HashSet<>(), tokens = new HashSet<>();
        for (ExplorationGroup group : this.groups) {
            if (!groupIds.add(group.id()) || !tokens.add(group.mapTokenId()))
                throw new IllegalArgumentException("group and token ids must be unique");
            for (ExplorationGroup.Member member : group.members()) if (!members.add(member.characterSheetId()))
                throw new IllegalArgumentException("a character may belong to only one exploration group");
        }
    }

    public ExplorationState withCycle(ExplorationCycle cycle) { return new ExplorationState(groups, cycle); }

    public ExplorationState split(UUID groupId, UUID newGroupId, UUID newMapTokenId,
            List<ExplorationGroup.Member> movedMembers) {
        ExplorationGroup source = groups.stream().filter(group -> group.id().equals(groupId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        List<ExplorationGroup.Member> moved = List.copyOf(movedMembers);
        if (moved.isEmpty() || !source.members().containsAll(moved) || moved.size() == source.members().size())
            throw new IllegalArgumentException("split must move a non-empty proper subset of group members");
        List<ExplorationGroup.Member> remaining = source.members().stream().filter(member -> !moved.contains(member)).toList();
        List<ExplorationGroup> next = new ArrayList<>(groups);
        next.remove(source);
        next.add(new ExplorationGroup(source.id(), source.mapTokenId(), remaining));
        next.add(new ExplorationGroup(newGroupId, newMapTokenId, moved));
        return new ExplorationState(next);
    }

    public ExplorationState merge(UUID targetGroupId, UUID sourceGroupId, UUID mergedTokenId,
            List<ExplorationGroup.Member> formation) {
        if (targetGroupId.equals(sourceGroupId)) throw new IllegalArgumentException("cannot merge a group with itself");
        ExplorationGroup target = group(targetGroupId), source = group(sourceGroupId);
        if (!formation.stream().map(ExplorationGroup.Member::characterSheetId).collect(java.util.stream.Collectors.toSet())
                .equals(java.util.stream.Stream.concat(target.members().stream(), source.members().stream())
                        .map(ExplorationGroup.Member::characterSheetId).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("merged formation must assign every group member exactly once");
        List<ExplorationGroup> next = groups.stream().filter(group -> !group.id().equals(targetGroupId)
                && !group.id().equals(sourceGroupId)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        next.add(new ExplorationGroup(targetGroupId, mergedTokenId, formation));
        return new ExplorationState(next);
    }

    private ExplorationGroup group(UUID id) {
        return groups.stream().filter(group -> group.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
    }
}
