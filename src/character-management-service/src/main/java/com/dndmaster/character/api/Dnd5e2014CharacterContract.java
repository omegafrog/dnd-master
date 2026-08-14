package com.dndmaster.character.api;

import java.util.List;

/** Canonical D&D 5e 2014 Basic Rules values shared by the catalog and sheet validator. */
final class Dnd5e2014CharacterContract {
    static final List<String> RACES = List.of("드워프", "엘프", "하플링", "인간");
    static final List<String> CLASSES = List.of(
            "바바리안", "바드", "클레릭", "드루이드", "파이터", "몽크", "팔라딘", "레인저", "로그", "소서러", "워락", "위저드");
    static final List<String> BACKGROUNDS = List.of(
            "수행사제", "사기꾼", "범죄자", "연예인", "민중 영웅", "길드 장인", "은둔자", "귀족", "이방인", "현자", "선원", "군인", "부랑아");

    private Dnd5e2014CharacterContract() {}
}
