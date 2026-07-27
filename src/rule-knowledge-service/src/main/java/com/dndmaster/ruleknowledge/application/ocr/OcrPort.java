package com.dndmaster.ruleknowledge.application.ocr;

public interface OcrPort {
    OcrResult recognize(OcrRequest request);
}
