-- Scenario Model Runtime is the only canonical adventure runtime.  Historical
-- Flyway migrations remain immutable; this migration removes their legacy
-- structures from live databases without a compatibility path.
DROP TABLE IF EXISTS adventure_story_plan_current CASCADE;
DROP TABLE IF EXISTS adventure_story_plan_revision CASCADE;
DROP TABLE IF EXISTS adventure_story_plan_history CASCADE;
DROP TABLE IF EXISTS adventure_story_plan CASCADE;
DROP TABLE IF EXISTS tactical_scene_preparation_job CASCADE;
DROP TABLE IF EXISTS adventure_active_tactical_map CASCADE;
DROP TABLE IF EXISTS gm_context_checkpoint CASCADE;
DROP TABLE IF EXISTS gm_context_checkpoint_current CASCADE;
DROP TABLE IF EXISTS committed_world_fact CASCADE;
DROP TABLE IF EXISTS adventure_clock CASCADE;
