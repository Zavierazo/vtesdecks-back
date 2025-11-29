package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.QueryFactory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class Set {
    public static final Attribute<Set, Integer> ID_ATTRIBUTE = QueryFactory.attribute(Set.class, Integer.class, "id", Set::getId);
    public static final Attribute<Set, String> ABBREV_ATTRIBUTE = QueryFactory.attribute(Set.class, String.class, "abbrev", Set::getAbbrev);
    public static final Attribute<Set, String> FULL_NAME_ATTRIBUTE = QueryFactory.attribute(Set.class, String.class, "full_name", Set::getFullName);
    public static final Attribute<Set, LocalDate> RELEASE_ATTRIBUTE = QueryFactory.nullableAttribute(Set.class, LocalDate.class, "releaseDate", Set::getReleaseDate);
    public static final Attribute<Set, LocalDateTime> LAST_UPDATE_ATTRIBUTE = QueryFactory.attribute(Set.class, LocalDateTime.class, "last_update", Set::getLastUpdate);

    private Integer id;
    private String abbrev;
    private LocalDate releaseDate;
    private String fullName;
    private String company;
    private LocalDateTime lastUpdate;
}
