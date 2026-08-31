package com.dndmaster.adventure.domain.scenario;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** The source-grounded strategy used to determine a saving throw DC. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FixedSaveDc.class, name = "FIXED"),
        @JsonSubTypes.Type(value = CasterSpellSaveDc.class, name = "CASTER_SPELL_SAVE_DC")
})
public sealed interface SaveDc permits FixedSaveDc, CasterSpellSaveDc {
    static SaveDc fixed(Integer value) {
        return value == null ? null : new FixedSaveDc(value);
    }
}
