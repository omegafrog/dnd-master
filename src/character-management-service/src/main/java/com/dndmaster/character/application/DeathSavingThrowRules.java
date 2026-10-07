package com.dndmaster.character.application;

/** D&D 5e 2014 death saving throw outcome. */
public final class DeathSavingThrowRules {
    public record Result(int successes, int failures, boolean stable, boolean dead, boolean regainedHitPoint) {}

    private DeathSavingThrowRules() {}

    public static Result resolve(int roll, int successes, int failures) {
        if (roll < 1 || roll > 20) throw new IllegalArgumentException("death saving throw must be a d20 result");
        if (successes < 0 || successes > 2 || failures < 0 || failures > 2) {
            throw new IllegalArgumentException("death saving throw progress is invalid");
        }
        if (roll == 20) return new Result(0, 0, false, false, true);
        if (roll == 1) failures += 2;
        else if (roll >= 10) successes++;
        else failures++;
        if (failures >= 3) return new Result(0, 3, false, true, false);
        if (successes >= 3) return new Result(0, 0, true, false, false);
        return new Result(successes, failures, false, false, false);
    }
}
