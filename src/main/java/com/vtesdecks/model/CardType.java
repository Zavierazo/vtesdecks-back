package com.vtesdecks.model;

import lombok.Getter;

public enum CardType {
    MASTER("Master", null),//Master doenst have icon
    EQUIPMENT("Equipment", "equipment"),
    ACTION("Action", "action"),
    POWER("Power", "power"),
    EVENT("Event", "event"),
    COMBAT("Combat", "combat"),
    ALLY("Ally", "ally"),
    ACTION_MODIFIER("Action Modifier", "modifier"),
    POLITICAL_ACTION("Political Action", "political"),
    REACTION("Reaction", "reaction"),
    RETAINER("Retainer", "retainer"),
    CONVICTION("Conviction", "conviction"),
    REFLEX("Reflex", "reflex");


    @Getter
    private String name;
    @Getter
    private String icon;
    @Getter
    private String[] alias;

    CardType(String name, String icon, String... alias) {
        this.name = name;
        this.icon = icon;
        this.alias = alias;
    }

    public static CardType getFromName(String name) {
        for (CardType discipline : CardType.values()) {
            if (discipline.getName().equalsIgnoreCase(name)) {
                return discipline;
            } else if (discipline.getAlias() != null) {
                for (String alias : discipline.getAlias()) {
                    if (alias.equalsIgnoreCase(name)) {
                        return discipline;
                    }
                }
            }
        }
        return null;
    }


}
