package com.dndmaster.gmeval.application;

/** Plain Java entry point for smoke runs. Usage: dataset report model. Responses are supplied by the generator port in embedding applications. */
public final class EvalRunnerMain {
    private EvalRunnerMain() {}
    public static void main(String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("usage: EvalRunnerMain <dataset.jsonl> <report.json> <model>");
        throw new UnsupportedOperationException("configure a ResponseGeneratorPort before running the benchmark");
    }
}
