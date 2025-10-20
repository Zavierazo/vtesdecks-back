package com.vtesdecks.model;

import lombok.Getter;

public enum Path {
    CAINE("Caine", "pathcaine"),
    CATHARI("Cathari", "pathcathari"),
    DEATH_AND_THE_SOUL("Death and the Soul", "pathdeath"),
    POWER_AND_THE_INNER_VOICE("Power and the Inner Voice", "pathpower");

    @Getter
    private final String name;
    @Getter
    private final String icon;
    @Getter
    private final String[] alias;

    Path(String name, String icon, String... alias) {
        this.name = name;
        this.icon = icon;
        this.alias = alias;
    }

    public static Path getFromName(String name) {
        for (Path path : Path.values()) {
            if (path.getName().equalsIgnoreCase(name)) {
                return path;
            } else if (path.getAlias() != null) {
                for (String alias : path.getAlias()) {
                    if (alias.equalsIgnoreCase(name)) {
                        return path;
                    }
                }
            }
        }
        return null;
    }


}
