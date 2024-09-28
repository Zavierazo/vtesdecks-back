package com.vtesdecks.model;

import lombok.Getter;

public enum Discipline {
    ABOMBWE("Abombwe", "abombwe", "abo"),
    ANIMALISM("Animalism", "animalism", "ani"),
    AUSPEX("Auspex", "auspex", "aus"),
    CELERITY("Celerity", "celerity", "cel"),
    CHIMERSTRY("Chimerstry", "chimerstry", "chi"),
    DAIMOINON("Daimoinon", "daimoinon", "dai"),
    DEFENSE("Defense", "defense", "def"),
    DEMENTATION("Dementation", "dementation", "dem"),
    DOMINATE("Dominate", "dominate", "dom"),
    FLIGHT("Flight", "flight"),
    FORTITUDE("Fortitude", "fortitude", "for"),
    INNOCENCE("Innocence", "innocence", "inn"),
    JUDGMENT("Judgment", "judgment", "jud"),
    MALEFICIA("Maleficia", "maleficia", "mal"),
    MARTYRDOM("Martyrdom", "martyrdom", "mar"),
    MELPOMINEE("Melpominee", "melpominee", "mel"),
    MYTHERCERIA("Mytherceria", "mytherceria", "myt"),
    NECROMANCY("Necromancy", "necromancy", "nec"),
    OBEAH("Obeah", "obeah", "obe"),
    OBFUSCATE("Obfuscate", "obfuscate", "obf"),
    OBLIVION("Oblivion", "oblivion", "obl"),
    OBTENEBRATION("Obtenebration", "obtenebration", "obt"),
    POTENCE("Potence", "potence", "pot"),
    PRESENCE("Presence", "presence", "pre"),
    PROTEAN("Protean", "protean", "pro"),
    QUIETUS("Quietus", "quietus", "qui"),
    REDEMPTION("Redemption", "redemption", "red"),
    SANGUINUS("Sanguinus", "sanguinus", "san"),
    SERPENTIS("Serpentis", "serpentis", "ser"),
    SPIRITUS("Spiritus", "spiritus", "spi"),
    STRIGA("Striga", "striga", "str"),
    TEMPORIS("Temporis", "temporis", "tem"),
    THANATOSIS("Thanatosis", "thanatosis", "thn"),
    BLOOD_SORCERY("Blood Sorcery", "bloodsorcery", "tha"),
    VALEREN("Valeren", "valeren", "val"),
    VENGEANCE("Vengeance", "vengeance", "ven"),
    VICISSITUDE("Vicissitude", "vicissitude", "vic"),
    VISCERATIKA("Visceratika", "visceratika", "vis"),
    VISION("Vision", "vision", "Imbuedvis", "viz");


    @Getter
    private String name;
    @Getter
    private String icon;
    @Getter
    private String[] alias;

    Discipline(String name, String icon, String... alias) {
        this.name = name;
        this.icon = icon;
        this.alias = alias;
    }

    public static Discipline getFromName(String name) {
        for (Discipline discipline : Discipline.values()) {
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
