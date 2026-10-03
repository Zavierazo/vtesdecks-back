package com.vtesdecks.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchetypeAttributeRequirement {
    public enum Type { CRYPT_CLAN, LIBRARY_TYPE, CRYPT_DISCIPLINE, LIBRARY_DISCIPLINE }

    private Type type;
    private String value;
    @JsonDeserialize(using = ArchetypeCardRequirement.StrictIntegerDeserializer.class)
    private Integer minimumQuantity;
}
