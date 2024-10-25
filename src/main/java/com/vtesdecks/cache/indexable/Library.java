package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.QueryFactory;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class Library {
    public static final Attribute<Library, Integer> ID_ATTRIBUTE = QueryFactory.attribute(Library.class, Integer.class, "id", Library::getId);
    public static final Attribute<Library, String> NAME_ATTRIBUTE = QueryFactory.attribute(Library.class, String.class, "name", (Library library) -> StringUtils.stripAccents(StringUtils.lowerCase(library.getName())));
    public static final Attribute<Library, String> TEXT_ATTRIBUTE = QueryFactory.attribute(Library.class, String.class, "text", (Library library) -> StringUtils.stripAccents(StringUtils.lowerCase(library.getText())));
    public static final Attribute<Library, LocalDateTime> LAST_UPDATE_ATTRIBUTE = QueryFactory.attribute(Library.class, LocalDateTime.class, "last_update", Library::getLastUpdate);

    private Integer id;
    private String name;
    private String aka;
    private String type;
    private Set<String> clans;
    private Set<String> clanIcons;
    private Integer poolCost;
    private Integer bloodCost;
    private Integer convictionCost;
    private boolean burn;
    private String text;
    private String flavor;
    private List<String> sets;
    private String requirement;
    private String banned;
    private String artist;
    private String capacity;
    //Extra
    private Map<String, I18n> i18n;
    private String image;
    private String cropImage;
    private boolean trifle;
    private Set<String> disciplines;
    private Set<String> typeIcons;
    private Set<String> disciplineIcons;
    private Set<String> sects;
    private Set<String> titles;
    private Set<String> taints;
    private Long deckPopularity;
    private Long cardPopularity;
    private boolean printOnDemand;
    private LocalDateTime lastUpdate;
}
