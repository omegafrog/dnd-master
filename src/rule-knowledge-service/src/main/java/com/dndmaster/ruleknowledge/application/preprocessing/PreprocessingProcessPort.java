package com.dndmaster.ruleknowledge.application.preprocessing;

public interface PreprocessingProcessPort {
    PreprocessingRunResult preprocess(PreprocessingRunRequest request);

    PreprocessingRunResult status(PreprocessingStatusRequest request);

    PreprocessingRunResult retryPages(PreprocessingRetryRequest request);
}
