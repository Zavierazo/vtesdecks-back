package com.vtesdecks.cache.indexable;

import static com.vtesdecks.util.Constants.SEPARATOR;

import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.QueryFactory;
import com.vtesdecks.util.VtesUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeckCard {
    public static final Attribute<DeckCard, String> ID_ATTRIBUTE = QueryFactory.attribute("id", DeckCard::getUniqueId);
    public static final Attribute<DeckCard, String> DECK_ID_ATTRIBUTE = QueryFactory.attribute("deckId", DeckCard::getDeckId);
    public static final Attribute<DeckCard, Integer> CARD_ID_ATTRIBUTE = QueryFactory.attribute("cardId", DeckCard::getId);
    public static final Attribute<DeckCard, Integer> NUMBER_ATTRIBUTE = QueryFactory.attribute("number", DeckCard::getNumber);
    public static final Attribute<DeckCard, Boolean> IS_CRYPT_ATTRIBUTE = QueryFactory.attribute("isCrypt", card -> VtesUtils.isCrypt(card.getId()));
    public static final Attribute<DeckCard, Boolean> IS_LIBRARY_ATTRIBUTE =
        QueryFactory.attribute("isLibrary", card -> VtesUtils.isLibrary(card.getId()));
    private String deckId;
    private Integer id;
    private Integer number;

    public String getUniqueId() {
        return String.join(SEPARATOR, String.valueOf(deckId), String.valueOf(id));
    }

}
