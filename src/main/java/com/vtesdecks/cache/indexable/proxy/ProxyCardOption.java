package com.vtesdecks.cache.indexable.proxy;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.QueryFactory;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProxyCardOption {
    public static final Attribute<ProxyCardOption, Integer> CARD_ID_ATTRIBUTE = QueryFactory.attribute("cardId", ProxyCardOption::getCardId);

    private Integer cardId;
    private String setAbbrev;
}
