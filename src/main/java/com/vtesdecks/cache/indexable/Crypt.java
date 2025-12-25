package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.MultiValueNullableAttribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.vtesdecks.util.Utils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class Crypt extends Card {
    public static final Attribute<Crypt, Integer> ID_ATTRIBUTE = QueryFactory.attribute(Crypt.class, Integer.class, "id", Crypt::getId);
    public static final Attribute<Crypt, String> NAME_ATTRIBUTE =
            QueryFactory.attribute(Crypt.class, String.class, "name", (Crypt crypt) -> Utils.normalizeName(StringUtils.lowerCase(crypt.getName())));
    public static final Attribute<Crypt, String> TEXT_ATTRIBUTE =
            QueryFactory.attribute(Crypt.class, String.class, "text", (Crypt crypt) -> Utils.normalizeName(StringUtils.lowerCase(crypt.getText())));
    public static final Attribute<Crypt, String> TYPE_ATTRIBUTE = QueryFactory.nullableAttribute(Crypt.class, String.class, "type", Crypt::getType);
    public static final Attribute<Crypt, String> CLAN_ATTRIBUTE = QueryFactory.nullableAttribute(Crypt.class, String.class, "clan", Crypt::getClan);
    public static final Attribute<Crypt, Integer> DISCIPLINE_NUMBER_ATTRIBUTE = QueryFactory.nullableAttribute(Crypt.class, Integer.class, "disciplineNumber", (Crypt crypt) -> crypt.getDisciplines().size());
    public static final Attribute<Crypt, String> DISCIPLINE_MULTI_ATTRIBUTE = new MultiValueNullableAttribute<Crypt, String>(true) {
        public Iterable<String> getNullableValues(Crypt crypt, QueryOptions queryOptions) {
            return crypt.getDisciplines();
        }
    };
    public static final Attribute<Crypt, LocalDateTime> LAST_UPDATE_ATTRIBUTE = QueryFactory.attribute(Crypt.class, LocalDateTime.class, "last_update", Crypt::getLastUpdate);

    private String type;
    private String clan;
    private String path;
    private boolean adv;
    private Integer group;
    private Integer capacity;
    private String text;
    private String title;
    private String banned;
    private String artist;
    //Extra
    private String clanIcon;
    private String pathIcon;
    private Set<String> disciplines;
    private Set<String> superiorDisciplines;
    private Set<String> disciplineIcons;
    private String sect;
}
