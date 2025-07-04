package com.vtesdecks.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public enum LibraryTaint {
    POSITIVE_INTERCEPT("+Intercept / -Stealth", "-[0-9]+ stealth(?! \\(d\\))(?! \\w)(?! action)|\\+[0-9]+ intercept|gets -([0-9]|x)+ stealth|stealth to 0"),
    NEGATIVE_INTERCEPT("+Stealth / -Intercept", "\\+[0-9]+ stealth(?! \\(d\\))(?! \\w)(?! action)|-[0-9]+ intercept"),
    BLEED("+Bleed", "\\+\\d bleed|to cause your prey to burn \\d pool"),
    STRENGTH("+Strength", "\\+\\d strength"),
    VOTES("+Votes / Title", "\\+. vote|additional vote|represent the .* title"),
    HAND_SIZE("+Hand Size", "\\+\\d hand size"),
    DODGE("Dodge"),
    MANEUVER("Maneuver"),
    ADDITIONAL_STRIKE("Additional Strike"),
    AGGRAVATED("Aggravated", "(?:[^non-])aggravated"),
    PREVENT("Prevent", "(?:[^un])prevent(?:[^able])"),
    PRESS("Press"),
    COMBAT_ENDS("Combat Ends"),
    CHANGE_TARGET("Change target", "change the target of the bleed|is now bleeding your predator's predator|is now bleeding the chosen|is now bleeding that methuselah|is now bleeding his or her prey"),
    COMBAT("Enter combat", "enter combat"),
    REDUCE_BLEED("Reduce a Bleed"),
    UNLOCK("Unlock", "(?!not )unlock(?! phase|ed)|wakes"),
    WAKE("Wake"),
    BLACK_HAND("Black Hand"),
    SERAPH("Seraph"),
    INFERNAL("Infernal"),
    RED_LIST("Red List"),
    FLIGHT("Flight"),
    CREATE_VAMPIRE("Create vampire", "becomes a.*(\\d[ -]|same.*)capacity"),
    BURN_OPTION("Burn Option"),
    BANNED("Banned"),
    REFLEX("Reflex"),
    TWD("Used by TWD"),
    ADD_BLOOD("Add Blood", "add .* blood (from the blood bank )?to .* in your uncontrolled region|move .* blood (from the blood bank )?to .* in your uncontrolled region|draw .* from your crypt .* add .* blood .* to it"),
    MULTI_TYPE("Multi-Type"),
    MULTI_DISCIPLINE("Multi-Discipline");

    @Getter
    private final String name;
    @Getter
    private final Pattern regex;
    @Getter
    private final Integer[] ids;

    LibraryTaint(String name) {
        this(name, null);
    }

    LibraryTaint(String name, Integer... ids) {
        this(name, null, ids);
    }

    LibraryTaint(String name, String regex, Integer... ids) {
        this.name = name;
        if (regex != null) {
            this.regex = Pattern.compile(regex);
        } else {
            this.regex = null;
        }
        if (ids != null) {
            this.ids = ids;
        } else {
            this.ids = new Integer[0];
        }
    }

    public static List<LibraryTaint> getFromText(Integer libraryId, String text) {
        List<LibraryTaint> taints = new ArrayList<>();
        for (LibraryTaint taint : LibraryTaint.values()) {
            if (taint.getRegex() != null) {
                if (taint.getRegex().matcher(text.toLowerCase()).find()) {
                    taints.add(taint);
                }
            } else if (text.toLowerCase().contains(taint.getName().toLowerCase())) {
                taints.add(taint);
            }
            for (Integer id : taint.getIds()) {
                if (id.equals(libraryId)) {
                    taints.add(taint);
                }
            }
        }
        return taints;
    }
}
