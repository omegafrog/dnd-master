package com.dndmaster.character.domain;

public final class StructuredSheetRequiredException extends RuntimeException {
    public StructuredSheetRequiredException() { super("character changes require STRUCTURED_SHEET input"); }
}
