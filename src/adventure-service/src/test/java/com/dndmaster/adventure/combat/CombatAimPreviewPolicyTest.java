package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.combat.CombatAimPreviewPolicy;
import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatAimPreviewPolicyTest {
    @Test
    void previews_range_path_and_only_public_blockers_using_definition_data() {
        var definition = definition(StructuredSpellDefinition.EffectShape.NONE,
                StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS,
                StructuredSpellDefinition.PropagationRule.NONE, 15, null, null, null);

        var result = CombatAimPreviewPolicy.preview(definition, new CombatAimPreviewPolicy.Cell(1, 1),
                new CombatAimPreviewPolicy.Cell(4, 1), 5, 5, 5,
                List.of(new CombatAimPreviewPolicy.Cell(3, 1)));

        assertEquals(15, result.distanceFeet());
        assertTrue(result.inRange());
        assertEquals(List.of(new CombatAimPreviewPolicy.Cell(1, 1), new CombatAimPreviewPolicy.Cell(2, 1)), result.path());
        assertFalse(result.clearPath());
        assertEquals("PUBLIC_BLOCKER", result.blockReason());
    }

    @Test
    void supports_data_driven_area_shapes_and_long_range_boundary() {
        var definition = definition(StructuredSpellDefinition.EffectShape.SPHERE,
                StructuredSpellDefinition.PathBlockingRule.NONE,
                StructuredSpellDefinition.PropagationRule.AREA_FROM_POINT, 10, null, null, 5);

        var result = CombatAimPreviewPolicy.preview(definition, new CombatAimPreviewPolicy.Cell(0, 0),
                new CombatAimPreviewPolicy.Cell(3, 0), 5, 6, 6, List.of());

        assertTrue(result.inRange());
        assertTrue(result.longRange());
        assertTrue(result.effectCells().contains(new CombatAimPreviewPolicy.Cell(3, 0)));
        assertFalse(result.effectCells().contains(new CombatAimPreviewPolicy.Cell(5, 0)));
    }

    @Test
    void includes_vertical_separation_in_grid_cell_range() {
        var definition = definition(StructuredSpellDefinition.EffectShape.NONE,
                StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS,
                StructuredSpellDefinition.PropagationRule.NONE, 20, null, null, null);

        var preview = CombatAimPreviewPolicy.preview(definition, new CombatAimPreviewPolicy.Cell(0, 0), 0,
                new CombatAimPreviewPolicy.Cell(2, 0), 15, 5, 8, 8,
                List.of(), List.of(new CombatAimPreviewPolicy.Boundary(1, 0, "VERTICAL", "WALL", false)));

        // max(Chebyshev(2, 1), ceil(15 feet / 5 feet per cell)) * 5 feet
        assertEquals(15, preview.distanceFeet());
        assertTrue(preview.inRange());
        assertFalse(preview.clearPath(), "a 2D public barrier remains blocking across elevation changes");
    }

    @Test
    void boundary_blocking_uses_orientation_kind_and_open_state() {
        var definition = definition(StructuredSpellDefinition.EffectShape.NONE,
                StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS,
                StructuredSpellDefinition.PropagationRule.NONE, 20, null, null, null);
        List<BoundaryCase> cases = List.of(
                new BoundaryCase(new CombatAimPreviewPolicy.Boundary(1, 0, "VERTICAL", "WALL", false), false),
                new BoundaryCase(new CombatAimPreviewPolicy.Boundary(1, 0, "VERTICAL", "DOOR", false), false),
                new BoundaryCase(new CombatAimPreviewPolicy.Boundary(1, 0, "VERTICAL", "DOOR", true), true),
                new BoundaryCase(new CombatAimPreviewPolicy.Boundary(0, 1, "HORIZONTAL", "WALL", false), true));

        for (BoundaryCase test : cases) {
            var preview = CombatAimPreviewPolicy.preview(definition, new CombatAimPreviewPolicy.Cell(0, 0), 0,
                    new CombatAimPreviewPolicy.Cell(2, 0), 20, 5, 4, 3, List.of(), List.of(test.boundary()));
            assertEquals(test.clearPath(), preview.clearPath(), test.boundary().toString());
        }
    }

    @Test
    void applies_each_supported_shape_from_its_saved_dimensions() {
        var origin = new CombatAimPreviewPolicy.Cell(0, 0);
        var target = new CombatAimPreviewPolicy.Cell(3, 0);
        List<ShapeCase> cases = List.of(
                new ShapeCase(StructuredSpellDefinition.EffectShape.CONE, 15, null, null, null, 2, 1, true),
                new ShapeCase(StructuredSpellDefinition.EffectShape.LINE, 15, 5, null, null, 2, 1, false),
                new ShapeCase(StructuredSpellDefinition.EffectShape.CUBE, 10, null, null, null, 2, 1, true),
                new ShapeCase(StructuredSpellDefinition.EffectShape.CYLINDER, null, null, 5, 10, 4, 0, true),
                new ShapeCase(StructuredSpellDefinition.EffectShape.WALL, 15, 5, null, 10, 2, 1, false));

        for (ShapeCase test : cases) {
            var definition = definition(test.shape(), StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS,
                    StructuredSpellDefinition.PropagationRule.AREA_FROM_POINT, 30, test.length(), test.width(), test.radius(), test.height());
            var preview = CombatAimPreviewPolicy.preview(definition, origin, target, 5, 8, 8,
                    List.of(new CombatAimPreviewPolicy.Cell(1, 0)));
            assertEquals(test.includesProbe(), preview.effectCells().contains(new CombatAimPreviewPolicy.Cell(test.probeX(), test.probeY())), test.shape().name());
            assertFalse(preview.clearPath(), test.shape().name() + " must still honor the selected direct-path rule");
        }
    }

    @Test
    void unsupported_shape_fails_closed() {
        var definition = definition(StructuredSpellDefinition.EffectShape.UNSUPPORTED,
                StructuredSpellDefinition.PathBlockingRule.SOLID_BLOCKS,
                StructuredSpellDefinition.PropagationRule.AREA_FROM_POINT, 30, null, null, null);
        var result = CombatAimPreviewPolicy.preview(definition, new CombatAimPreviewPolicy.Cell(0, 0),
                new CombatAimPreviewPolicy.Cell(1, 0), 5, 5, 5, List.of());

        assertFalse(result.supported());
        assertEquals(List.of(), result.effectCells());
    }

    private static StructuredSpellDefinition definition(StructuredSpellDefinition.EffectShape shape,
            StructuredSpellDefinition.PathBlockingRule pathRule,
            StructuredSpellDefinition.PropagationRule propagation, int normalRange, Integer length,
            Integer width, Integer radius) {
        return definition(shape, pathRule, propagation, normalRange, length, width, radius, null);
    }

    private static StructuredSpellDefinition definition(StructuredSpellDefinition.EffectShape shape,
            StructuredSpellDefinition.PathBlockingRule pathRule,
            StructuredSpellDefinition.PropagationRule propagation, int normalRange, Integer length,
            Integer width, Integer radius, Integer height) {
        return new StructuredSpellDefinition("aim-test", "테스트 행동", java.util.UUID.randomUUID(), "p.1", "v1", 1,
                "", "", "15ft", "", "", "", "", "", List.of(368), "source", true,
                StructuredSpellDefinition.ReviewStatus.APPROVED, false,
                StructuredSpellDefinition.ActionKind.RANGED_ATTACK,
                new StructuredSpellDefinition.RangeProfile(StructuredSpellDefinition.RangeForm.NORMAL_AND_LONG,
                        normalRange, normalRange * 2),
                new StructuredSpellDefinition.TargetingProfile(StructuredSpellDefinition.TargetMode.POINT,
                        StructuredSpellDefinition.OriginMode.ACTOR_TOKEN, shape, length, width, height, radius,
                        pathRule, propagation), true);
    }

    private record ShapeCase(StructuredSpellDefinition.EffectShape shape, Integer length, Integer width,
            Integer radius, Integer height, int probeX, int probeY, boolean includesProbe) { }
    private record BoundaryCase(CombatAimPreviewPolicy.Boundary boundary, boolean clearPath) { }
}
