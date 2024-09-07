package com.vtesdecks.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.beust.jcommander.internal.Lists;
import lombok.Getter;

public enum Sect {
    CAMARILLA("Camarilla"),
    SABBAT("Sabbat"),
    LAIBON("Laibon"),
    INDEPENDENT("Independent", "{I}ndependent"),
    ANARCH("Anarch"),
    IMBUED("Imbued");


    private static final List<String> LIBRARY_STARTS_WITH =
        Lists.newArrayList("requires an ", "requires a ready ", "requires a non-sterile ", "requires a titled ", "requires a ready titled ",
            "requires a ", "requires a {}", "{or ", "requires a{n}{} ");
    @Getter
    private final String name;
    @Getter
    private final String[] alias;

    Sect(String name, String... alias) {
        this.name = name;
        this.alias = alias;
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        names.add(name);
        if (alias != null) {
            Collections.addAll(names, alias);
        }
        return names;
    }

    public static Sect getCryptFromText(String text) {
        String textLower = text.toLowerCase();
        Sect firstSect = null;
        int firstIndex = Integer.MAX_VALUE;
        for (Sect sect : Sect.values()) {
            for (String name : sect.names()) {
                int indexOf = textLower.indexOf(name.toLowerCase());
                if (indexOf != -1 && indexOf < firstIndex) {
                    firstIndex = indexOf;
                    firstSect = sect;
                }
            }
        }
        return firstSect;
    }

    public static List<Sect> getFromLibraryText(String text) {
        String textLower = text.toLowerCase();
        List<Sect> sects = new ArrayList<>();
        for (Sect sect : Sect.values()) {
            for (String name : sect.names()) {
                for (String startsWith : LIBRARY_STARTS_WITH) {
                    int indexOf = textLower.indexOf(startsWith + name.toLowerCase());
                    if (indexOf != -1) {
                        sects.add(sect);
                    }
                }
            }
        }
        return sects;
    }


}
