package com.dndmaster.aigamemaster.infrastructure.ai;

/** AI Game Master 입력. 지도 배치 요청은 선택적으로 원본 지도 이미지를 함께 전달한다. */
public record GmPrompt(String text, String imageDataUri) {
    public GmPrompt {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("prompt text is required");
        text = text.trim();
        imageDataUri = imageDataUri == null ? "" : imageDataUri.trim();
        if (!imageDataUri.isBlank() && !imageDataUri.startsWith("data:image/")) {
            throw new IllegalArgumentException("map image must be a data image URI");
        }
    }

    public GmPrompt(String text) {
        this(text, "");
    }

    public boolean hasImage() {
        return !imageDataUri.isBlank();
    }
}
