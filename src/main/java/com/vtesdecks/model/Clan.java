package com.vtesdecks.model;

import lombok.Getter;

public enum Clan {
    ABOMINATION("Abomination", "abo", "abominations"),
    AHRIMANE("Ahrimane", "ahrimanes", "ahrimanes"),
    AKUNANSE("Akunanse", "akunanse"),
    ASSAMITE("Assamite", "assamite"),
    AVENGER("Avenger", "avenger"),
    BAALI("Baali", "baali"),
    BLOOD_BROTHER("Blood Brother", "bloodbrothers", "bloodbrothers"),
    BRUJAH("Brujah", "brujah"),
    BRUJAH_ANTITRIBU("Brujah antitribu", "brujahanti", "brujahantitribu"),
    CAITIFF("Caitiff", "caitiff"),
    DAUGHTER_OF_COCOPHONY("Daughter of Cacophony", "daughters", "daughtersofcacophony"),
    Defender("Defender", "defender"),
    FOLLOWER_OF_SET("Follower of Set", "fos", "followersofset"),
    GANGREL("Gangrel", "gangrel"),
    GANGREL_ANTITRIBU("Gangrel antitribu", "gangrelanti", "gangrelantitribu"),
    GARGOYLE("Gargoyle", "gargoyle", "gargoyles"),
    GIOVANNI("Giovanni", "giovanni"),
    GURUHI("Guruhi", "guruhi"),
    HARBINGER_OF_SKULLS("Harbinger of Skulls", "harbingers", "harbingersofskulls"),
    INNOCENT("Innocent", "innocent"),
    ISHTARRI("Ishtarri", "ishtarri"),
    JUDGE("Judge", "judge"),
    KIASYD("Kiasyd", "kiasyd"),
    LASOMBRA("Lasombra", "lasombra"),
    MALKAVIAN("Malkavian", "malkavian"),
    MALKAVIAN_ANTITRIBU("Malkavian antitribu", "malkaviananti", "malkavianantitribu"),
    MARTYR("Martyr", "martyr"),
    NAGARAJA("Nagaraja", "nagaraja"),
    NOSFERATU("Nosferatu", "nosferatu"),
    NOSFERATU_ANTITRIBU("Nosferatu antitribu", "nosferatuanti", "nosferatuantitribu"),
    OSEBO("Osebo", "osebo"),
    PANDER("Pander", "pander"),
    RAVNOS("Ravnos", "ravnos"),
    REDEEMER("Redeemer", "redeemer"),
    SALUBRI("Salubri", "salubri"),
    SALUBRI_ANTITRIBU("Salubri antitribu", "salubrianti", "salubriantitribu"),
    SAMEDI("Samedi", "samedi"),
    TOREADOR("Toreador", "toreador"),
    TOREADOR_ANTITRIBU("Toreador antitribu", "toreadoranti", "toreadorantitribu"),
    TREMERE("Tremere", "tremere"),
    TREMERE_ANTITRIBU("Tremere antitribu", "tremereanti", "tremereantitribu"),
    TRUE_BRUJAH("True Brujah", "truebrujah", "truebrujah"),
    TZIMISCE("Tzimisce", "tzimisce"),
    VENTRUE("Ventrue", "ventrue"),
    VENTRUE_ANTITRIBU("Ventrue antitribu", "ventrueanti", "ventrueantitribu"),
    VISIONARY("Visionary", "visionary");

    @Getter
    private String name;
    @Getter
    private String icon;
    @Getter
    private String[] alias;

    Clan(String name, String icon, String... alias) {
        this.name = name;
        this.icon = icon;
        this.alias = alias;
    }

    public static Clan getFromName(String name) {
        for (Clan clan : Clan.values()) {
            if (clan.getName().equalsIgnoreCase(name)) {
                return clan;
            } else if (clan.getAlias() != null) {
                for (String alias : clan.getAlias()) {
                    if (alias.equalsIgnoreCase(name)) {
                        return clan;
                    }
                }
            }
        }
        return null;
    }

}
