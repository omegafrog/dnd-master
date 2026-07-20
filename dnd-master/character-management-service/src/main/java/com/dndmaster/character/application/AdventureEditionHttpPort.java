package com.dndmaster.character.application;

import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.SheetEdition;

public interface AdventureEditionHttpPort {
    SheetEdition getAppliedEdition(AdventureId adventureId);
}
