package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.character.application.CharacterSheetApplicationService;
import com.dndmaster.character.application.auth.PlayerSessionLookupPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterInternalAuthorizationTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void browserOwnerMustMatchTheIntrospectedSession() {
        var service = mock(CharacterSheetApplicationService.class);
        var lookup = mock(PlayerSessionLookupPort.class);
        when(lookup.resolvePlayerId("opaque-session")).thenReturn(java.util.Optional.of(OWNER));
        var controller = controller(service, lookup);

        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.listCharacterSheets(UUID.randomUUID(), null, "Bearer opaque-session"));
    }

    @Test
    void browserOwnerCanReadOnlyItsOwnCharacterList() {
        var service = mock(CharacterSheetApplicationService.class);
        when(service.listSheetsOwnedBy(OWNER)).thenReturn(List.of());
        var lookup = mock(PlayerSessionLookupPort.class);
        when(lookup.resolvePlayerId("opaque-session")).thenReturn(java.util.Optional.of(OWNER));
        var controller = controller(service, lookup);

        assertEquals(List.of(), controller.listCharacterSheets(OWNER, null, "Bearer opaque-session"));
    }

    @Test
    void internalTokenDoesNotNeedAPlayerSession() {
        var service = mock(CharacterSheetApplicationService.class);
        when(service.listSheetsOwnedBy(OWNER)).thenReturn(List.of());
        var controller = new CharacterSheetController(service);
        controller.setRequestGuard(new ApiRequestGuard("service-secret"));

        assertEquals(List.of(), controller.listCharacterSheets(OWNER, "service-secret", null));
    }

    @Test
    void browserCanReadTheEditionCatalogAfterSessionValidation() {
        var lookup = mock(PlayerSessionLookupPort.class);
        when(lookup.resolvePlayerId("opaque-session")).thenReturn(java.util.Optional.of(OWNER));
        var controller = controller(mock(CharacterSheetApplicationService.class), lookup);

        assertEquals("DND_5E_2014", controller.getCharacterRulesCatalog(null, "Bearer opaque-session", "DND_5E_2014").edition());
    }

    private static CharacterSheetController controller(CharacterSheetApplicationService service, PlayerSessionLookupPort lookup) {
        var controller = new CharacterSheetController(service);
        controller.setRequestGuard(new ApiRequestGuard("service-secret"));
        controller.setPlayerSessionLookupPort(lookup);
        return controller;
    }
}
