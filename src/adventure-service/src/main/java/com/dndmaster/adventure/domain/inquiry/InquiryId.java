package com.dndmaster.adventure.domain.inquiry;

import java.util.Objects;
import java.util.UUID;

public record InquiryId(UUID value) {
    public InquiryId { Objects.requireNonNull(value, "inquiry id must not be null"); }
    public static InquiryId generate() { return new InquiryId(UUID.randomUUID()); }
}
