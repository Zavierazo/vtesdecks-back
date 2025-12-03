package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.vtesdecks.util.Utils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
public class Library extends Card {
    public static final Attribute<Library, Integer> ID_ATTRIBUTE = QueryFactory.attribute(Library.class, Integer.class, "id", Library::getId);
    public static final Attribute<Library, String> NAME_ATTRIBUTE = QueryFactory.attribute(Library.class, String.class, "name", (Library library) -> Utils.normalizeName(StringUtils.lowerCase(library.getName())));
    public static final Attribute<Library, String> TEXT_ATTRIBUTE = QueryFactory.attribute(Library.class, String.class, "text", (Library library) -> Utils.normalizeName(StringUtils.lowerCase(library.getText())));
    public static final Attribute<Library, Integer> TYPE_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(Library.class, Integer.class, "typeNumber", (Library library) -> library.getTypes().size());
    public static final Attribute<Library, String> TYPE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Library, String>(true) {
        public Iterable<String> getNullableValues(Library library, QueryOptions queryOptions) {
            return library.getTypes();
        }
    };
    public static final Attribute<Library, Integer> CLAN_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(Library.class, Integer.class, "clanNumber", (Library library) -> library.getClans().size());
    public static final Attribute<Library, String> CLAN_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Library, String>(true) {
        public Iterable<String> getNullableValues(Library library, QueryOptions queryOptions) {
            return library.getClans();
        }
    };
    public static final Attribute<Library, Integer> DISCIPLINE_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(Library.class, Integer.class, "disciplineNumber", (Library crypt) -> crypt.getDisciplines().size());
    public static final Attribute<Library, String> DISCIPLINE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Library, String>(true) {
        public Iterable<String> getNullableValues(Library library, QueryOptions queryOptions) {
            return library.getDisciplines();
        }
    };
    public static final Attribute<Library, LocalDateTime> LAST_UPDATE_ATTRIBUTE = QueryFactory.attribute(Library.class, LocalDateTime.class, "last_update", Library::getLastUpdate);

    private Integer id;
    private String name;
    private String aka;
    private String type;
    private Set<String> clans;
    private String path;
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
    private boolean trifle;
    private Set<String> disciplines;
    private Set<String> types;
    private Set<String> typeIcons;
    private Set<String> clanIcons;
    private String pathIcon;
    private Set<String> disciplineIcons;
    private Set<String> sects;
    private Set<String> titles;
}
