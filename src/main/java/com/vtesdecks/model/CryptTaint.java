package com.vtesdecks.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public enum CryptTaint {
    INTERCEPT("+Intercept", "\\+\\d intercept"),
    STEALTH("+Stealth", "\\+\\d stealth"),
    BLEED("+Bleed", "\\+\\d bleed"),
    STRENGTH("+Strength", "\\+\\d strength"),
    HAND_SIZE("+Hand Size", "\\+\\d hand size"),
    TITLED("Titled"),
    MANEUVER("Maneuver"),
    ADDITIONAL_STRIKE("Additional Strike"),
    AGGRAVATED("Aggravated", "(?:[^non-])aggravated"),
    PREVENT("Prevent", "(?:[^un])prevent(?:[^able])"),
    PRESS("Press", "gets (.*)?optional press"),
    COMBAT("Enter combat", "(can|may)( .* to)? enter combat"),
    UNLOCK("Unlock", "(?!not )unlock(?! phase|ed)|wakes"),
    BLACK_HAND("Black Hand"),
    SERAPH("Seraph"),
    INFERNAL("Infernal"),
    RED_LIST("Red List"),
    FLIGHT("Flight"),
    ADVANCED("Advanced"),
    BANNED("Banned"),
    SLAVE("Slave"),
    TWD("Used by TWD"),
    ADD_BLOOD("Add Blood", "add .* blood (from the blood bank )?to .* in your uncontrolled region");

    @Getter
    private String name;
    @Getter
    private Pattern regex;

    CryptTaint(String name) {
        this(name, null);
    }

    CryptTaint(String name, String regex) {
        this.name = name;
        if (regex != null) {
            this.regex = Pattern.compile(regex);
        }
    }

    public static List<CryptTaint> getFromText(String text) {
        List<CryptTaint> taints = new ArrayList<>();
        for (CryptTaint taint : CryptTaint.values()) {
            if (text.toLowerCase().contains(taint.getName().toLowerCase())) {
                taints.add(taint);
            }
            if (taint.getRegex() != null && taint.getRegex().matcher(text.toLowerCase()).find()) {
                taints.add(taint);
            }
        }
        return taints;
    }
}
