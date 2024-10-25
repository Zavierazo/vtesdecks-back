package com.vtesdecks.cache.indexable;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.QueryFactory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class Set {
    public static final Attribute<Set, Integer> ID_ATTRIBUTE = QueryFactory.attribute(Set.class, Integer.class, "id", Set::getId);
    public static final Attribute<Set, String> ABBREV_ATTRIBUTE = QueryFactory.attribute(Set.class, String.class, "abbrev", Set::getAbbrev);
    public static final Attribute<Set, LocalDate> RELEASE_ATTRIBUTE = QueryFactory.attribute(Set.class, LocalDate.class, "releaseDate", Set::getReleaseDate);

    private Integer id;
    private String abbrev;
    private LocalDate releaseDate;
    private String fullName;
    private String company;
}
