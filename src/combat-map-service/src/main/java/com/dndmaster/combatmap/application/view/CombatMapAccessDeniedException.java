package com.dndmaster.combatmap.application.view;
@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
public final class CombatMapAccessDeniedException extends RuntimeException{public CombatMapAccessDeniedException(){super("combat map access denied");}}
