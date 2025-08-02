package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.vtesdecks.util.Utils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class Crypt {
    public static final Attribute<Crypt, Integer> ID_ATTRIBUTE = QueryFactory.attribute(Crypt.class, Integer.class, "id", Crypt::getId);
    public static final Attribute<Crypt, String> NAME_ATTRIBUTE =
            QueryFactory.attribute(Crypt.class, String.class, "name", (Crypt crypt) -> Utils.normalizeLackeyName(StringUtils.lowerCase(crypt.getName())));
    public static final Attribute<Crypt, String> TEXT_ATTRIBUTE =
            QueryFactory.attribute(Crypt.class, String.class, "text", (Crypt crypt) -> Utils.normalizeLackeyName(StringUtils.lowerCase(crypt.getText())));
    public static final Attribute<Crypt, LocalDateTime> LAST_UPDATE_ATTRIBUTE = QueryFactory.attribute(Crypt.class, LocalDateTime.class, "last_update", Crypt::getLastUpdate);

    private Integer id;
    private String name;
    private String aka;
    private String type;
    private String clan;
    private boolean adv;
    private Integer group;
    private Integer capacity;
    private String text;
    private List<String> sets;
    private String title;
    private String banned;
    private String artist;
    //Extra
    private Map<String, I18n> i18n;
    private String image;
    private String cropImage;
    private String clanIcon;
    private Set<String> disciplines;
    private Set<String> superiorDisciplines;
    private Set<String> disciplineIcons;
    private String sect;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private boolean printOnDemand;
    private LocalDateTime lastUpdate;
}
