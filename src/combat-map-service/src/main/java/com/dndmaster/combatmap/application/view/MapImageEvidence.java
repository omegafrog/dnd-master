package com.dndmaster.combatmap.application.view;

import java.util.Base64;
import java.util.Objects;

/** 원본 지도에서 가져온 이미지 근거. 모델 입력과 플레이어 표시용으로만 사용한다. */
public record MapImageEvidence(String contentType, byte[] content) {
    public MapImageEvidence {
        contentType = contentType == null || contentType.isBlank() ? "image/png" : contentType.trim().toLowerCase();
        if (!contentType.startsWith("image/")) throw new IllegalArgumentException("map image content type must be an image");
        content = Objects.requireNonNull(content, "map image content must not be null").clone();
        if (content.length == 0) throw new IllegalArgumentException("map image content must not be empty");
    }

    @Override public byte[] content() { return content.clone(); }

    public String dataUri() {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content);
    }
}
