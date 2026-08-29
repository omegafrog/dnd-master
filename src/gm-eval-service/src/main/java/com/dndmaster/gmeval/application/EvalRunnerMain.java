package com.dndmaster.gmeval.application;

/** Plain Java entry point for smoke runs. Usage: dataset report model. Responses are supplied by the generator port in embedding applications. */
public final class EvalRunnerMain {
    private EvalRunnerMain() {}
    public static void main(String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("usage: EvalRunnerMain <dataset.jsonl> <report.json> <model>");
        var config = new EvalRunConfiguration("cli-" + System.currentTimeMillis(), "gm-turn-v1", args[2], "cli", "cli", null, "cli-supplied");
        String response = System.getenv().getOrDefault("GM_EVAL_RESPONSE", "The chamber is quiet.");
        new EvalRunner(null, null).run(java.nio.file.Path.of(args[0]), config, (c, ignored) -> new GeneratedResponse(response, "cli"), java.nio.file.Path.of(args[1]));
    }
}
