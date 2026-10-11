package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record StructuredSpellDefinition(
        String id,
        String name,
        java.util.UUID sourceDocumentId,
        String sourceLocator,
        String sourceVersion,
        long extractionVersion,
        String level,
        String castingTime,
        String rangeArea,
        String components,
        String duration,
        String school,
        String attackSave,
        String damageEffect,
        List<Integer> ownerPlanNumbers,
        String ownerEvidence,
        boolean executable,
        ReviewStatus reviewStatus,
        boolean required,
        ActionKind actionKind,
        RangeProfile rangeProfile,
        TargetingProfile targetingProfile,
        boolean active) {
    public StructuredSpellDefinition(String id, String name, java.util.UUID sourceDocumentId, String sourceLocator,
            String sourceVersion, long extractionVersion, String level, String castingTime, String rangeArea,
            String components, String duration, String school, String attackSave, String damageEffect,
            List<Integer> ownerPlanNumbers, String ownerEvidence, boolean executable, ReviewStatus reviewStatus) {
        this(id, name, sourceDocumentId, sourceLocator, sourceVersion, extractionVersion, level, castingTime,
                rangeArea, components, duration, school, attackSave, damageEffect, ownerPlanNumbers, ownerEvidence,
                executable, reviewStatus, false, ActionKind.SPELL, null, null, true);
    }
    public StructuredSpellDefinition(String id, String name, java.util.UUID sourceDocumentId, String sourceLocator,
            String sourceVersion, long extractionVersion, String level, String castingTime, String rangeArea,
            String components, String duration, String school, String attackSave, String damageEffect,
            List<Integer> ownerPlanNumbers, String ownerEvidence, boolean executable, ReviewStatus reviewStatus,
            boolean required) {
        this(id, name, sourceDocumentId, sourceLocator, sourceVersion, extractionVersion, level, castingTime,
                rangeArea, components, duration, school, attackSave, damageEffect, ownerPlanNumbers, ownerEvidence,
                executable, reviewStatus, required, ActionKind.SPELL, null, null, true);
    }
    public StructuredSpellDefinition {
        id = required(id, "spell id");
        name = required(name, "spell name");
        sourceDocumentId = Objects.requireNonNull(sourceDocumentId, "spell source document id must not be null");
        sourceLocator = required(sourceLocator, "spell source locator");
        sourceVersion = required(sourceVersion, "spell source version");
        if (extractionVersion <= 0) throw new IllegalArgumentException("spell extraction version must be positive");
        rangeArea = required(rangeArea, "spell range or area");
        if (actionKind == null || actionKind == ActionKind.SPELL) {
            level = required(level, "spell level");
            castingTime = required(castingTime, "spell casting time");
            components = required(components, "spell components");
            duration = required(duration, "spell duration");
            school = required(school, "spell school");
            attackSave = required(attackSave, "spell attack or save");
            damageEffect = required(damageEffect, "spell effect");
        }
        ownerPlanNumbers = List.copyOf(Objects.requireNonNull(ownerPlanNumbers, "spell owners must not be null"));
        if (ownerPlanNumbers.stream().anyMatch(number -> number < 368 || number > 372)) {
            throw new IllegalArgumentException("spell owner must be an approved execution plan");
        }
        ownerEvidence = required(ownerEvidence, "spell owner evidence");
        reviewStatus = Objects.requireNonNull(reviewStatus, "spell review status must not be null");
        actionKind = Objects.requireNonNullElse(actionKind, ActionKind.SPELL);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public enum ReviewStatus { PENDING, APPROVED }

    public StructuredSpellDefinition edit(String level, String castingTime, String rangeArea, String components,
            String duration, String school, String attackSave, String damageEffect) {
        return new StructuredSpellDefinition(id, name, sourceDocumentId, sourceLocator, sourceVersion,
                extractionVersion, level, castingTime, rangeArea, components, duration, school, attackSave,
                damageEffect, ownerPlanNumbers, ownerEvidence, executable, ReviewStatus.PENDING, required,
                actionKind, rangeProfile, targetingProfile, active);
    }

    public boolean userAdded() { return id.startsWith("user-action-"); }

    public StructuredSpellDefinition approve() {
        return new StructuredSpellDefinition(id, name, sourceDocumentId, sourceLocator, sourceVersion,
                extractionVersion, level, castingTime, rangeArea, components, duration, school, attackSave,
                damageEffect, ownerPlanNumbers, ownerEvidence, executable, ReviewStatus.APPROVED, required,
                actionKind, rangeProfile, targetingProfile, active);
    }

    public StructuredSpellDefinition withSourceLocator(String locator) {
        if (sourceLocator.equals(locator)) return this;
        return new StructuredSpellDefinition(id, name, sourceDocumentId, locator, sourceVersion, extractionVersion,
                level, castingTime, rangeArea, components, duration, school, attackSave, damageEffect,
                ownerPlanNumbers, ownerEvidence, executable, reviewStatus, required, actionKind, rangeProfile,
                targetingProfile, active);
    }

    public StructuredSpellDefinition withRequired(boolean required) {
        return new StructuredSpellDefinition(id, name, sourceDocumentId, sourceLocator, sourceVersion, extractionVersion,
                level, castingTime, rangeArea, components, duration, school, attackSave, damageEffect,
                ownerPlanNumbers, ownerEvidence, executable, ReviewStatus.PENDING, required, actionKind,
                rangeProfile, targetingProfile, active);
    }

    public StructuredSpellDefinition reviewStructure(boolean required, ActionKind actionKind,
            RangeProfile rangeProfile, TargetingProfile targetingProfile, boolean active) {
        return new StructuredSpellDefinition(id, name, sourceDocumentId, sourceLocator, sourceVersion, extractionVersion,
                level, castingTime, rangeArea, components, duration, school, attackSave, damageEffect,
                ownerPlanNumbers, ownerEvidence, executable, ReviewStatus.PENDING, required, actionKind,
                rangeProfile, targetingProfile, active);
    }

    public enum ActionKind { SPELL, RANGED_ATTACK, THROWN }
    public enum RangeForm { SINGLE, NORMAL_AND_LONG, UNSUPPORTED }
    public enum TargetMode { SINGLE_TARGET, MULTIPLE_TARGETS, POINT, DIRECTION, SELF, UNSUPPORTED }
    public enum OriginMode { ACTOR_TOKEN, TARGET, CURSOR_POINT, CURSOR_DIRECTION, UNSUPPORTED }
    public enum EffectShape { NONE, CONE, LINE, CUBE, SPHERE, CYLINDER, WALL, UNSUPPORTED }
    public enum PathBlockingRule { SOLID_BLOCKS, CLEAR_PATH_REQUIRED, NONE, UNSUPPORTED }
    public enum PropagationRule { DIRECT, AREA_FROM_POINT, CONTIGUOUS, NONE, UNSUPPORTED }
    public record RangeProfile(RangeForm form, Integer normalRangeFeet, Integer longRangeFeet) {
        public RangeProfile {
            form = Objects.requireNonNull(form);
            if (normalRangeFeet != null && normalRangeFeet < 0 || longRangeFeet != null && longRangeFeet < 0)
                throw new IllegalArgumentException("range must not be negative");
            if (form == RangeForm.SINGLE && (normalRangeFeet == null || longRangeFeet != null)
                    || form == RangeForm.NORMAL_AND_LONG && (normalRangeFeet == null || longRangeFeet == null))
                throw new IllegalArgumentException("range values do not match range form");
        }
        public boolean supported() {
            return switch (form) {
                case SINGLE -> normalRangeFeet != null && normalRangeFeet > 0;
                case NORMAL_AND_LONG -> normalRangeFeet != null && normalRangeFeet > 0
                        && longRangeFeet != null && longRangeFeet > normalRangeFeet;
                case UNSUPPORTED -> false;
            };
        }
    }
    public record TargetingProfile(TargetMode mode, OriginMode origin, EffectShape shape,
            Integer lengthFeet, Integer widthFeet, Integer heightFeet, Integer radiusFeet,
            PathBlockingRule pathBlocking, PropagationRule propagation) {
        public TargetingProfile {
            mode = Objects.requireNonNull(mode);
            origin = Objects.requireNonNull(origin);
            shape = Objects.requireNonNull(shape);
            pathBlocking = Objects.requireNonNull(pathBlocking);
            propagation = Objects.requireNonNull(propagation);
            if (java.util.Arrays.asList(lengthFeet, widthFeet, heightFeet, radiusFeet).stream()
                    .filter(Objects::nonNull).anyMatch(value -> value < 0))
                throw new IllegalArgumentException("target dimensions must not be negative");
        }
        public boolean supported() {
            if (mode == TargetMode.UNSUPPORTED || origin == OriginMode.UNSUPPORTED || shape == EffectShape.UNSUPPORTED
                    || pathBlocking == PathBlockingRule.UNSUPPORTED || propagation == PropagationRule.UNSUPPORTED) return false;
            return switch (shape) {
                case NONE -> true;
                case CONE, CUBE -> positive(lengthFeet);
                case LINE -> positive(lengthFeet) && positive(widthFeet);
                case SPHERE -> positive(radiusFeet);
                case CYLINDER -> positive(radiusFeet) && positive(heightFeet);
                case WALL -> positive(lengthFeet) && positive(widthFeet) && positive(heightFeet);
                case UNSUPPORTED -> false;
            };
        }

        private static boolean positive(Integer value) { return value != null && value > 0; }
    }
}
