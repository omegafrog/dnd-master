package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.character.domain.CharacterMutationRejectedException;
import com.dndmaster.character.domain.RuleViolation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CharacterMutationExceptionHandlerTest {
    @Test
    void returnsStructuredViolationContractForGmConsumers() {
        var violation = new RuleViolation(
                "DRUID_METAL_ARMOR_RESTRICTION",
                "CHARACTER_RULE",
                "ERROR",
                "드루이드는 금속 갑옷을 장착할 수 없습니다.",
                Map.of("armor", "플레이트", "material", "METAL"));

        var response = new CharacterMutationExceptionHandler()
                .handle(new CharacterMutationRejectedException(List.of(violation)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("REJECTED", response.getBody().status());
        assertEquals("DRUID_METAL_ARMOR_RESTRICTION", response.getBody().violations().getFirst().code());
        assertEquals("플레이트", response.getBody().violations().getFirst().parameters().get("armor"));
    }
}
