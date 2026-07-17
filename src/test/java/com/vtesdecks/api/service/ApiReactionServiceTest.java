package com.vtesdecks.api.service;

import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.enums.ReactionTargetType;
import com.vtesdecks.enums.ReactionType;
import com.vtesdecks.jpa.entity.CommentEntity;
import com.vtesdecks.jpa.entity.ReactionEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.CommentRepository;
import com.vtesdecks.jpa.repositories.ReactionRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.api.ApiReactionSummary;
import com.vtesdecks.service.DeckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiReactionServiceTest {
    private static final Integer USER_ID = 1;
    private static final Integer OWNER_ID = 2;
    private static final String DECK_ID = "deck-123";

    @Mock
    private ReactionRepository reactionRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private DeckService deckService;
    @Mock
    private MessageProducer messageProducer;
    @InjectMocks
    private ApiReactionService service;

    private Deck deck(Integer ownerId) {
        Deck deck = new Deck();
        deck.setId(DECK_ID);
        deck.setName("Test Deck");
        if (ownerId != null) {
            UserEntity owner = new UserEntity();
            owner.setId(ownerId);
            deck.setUser(owner);
        }
        return deck;
    }

    private ReactionEntity reaction(Integer userId, ReactionType type) {
        ReactionEntity entity = new ReactionEntity();
        entity.setId(new ReactionEntity.ReactionId(userId, ReactionTargetType.DECK, DECK_ID, type));
        return entity;
    }

    @Test
    public void reactDeckActivationSaves() {
        when(deckService.getDeck(DECK_ID)).thenReturn(deck(OWNER_ID));
        when(reactionRepository.existsById(any())).thenReturn(false);

        assertTrue(service.reactDeck(USER_ID, DECK_ID, ReactionType.SPICY, true));

        verify(reactionRepository).save(any(ReactionEntity.class));
        verify(messageProducer).publishDeckSync(DECK_ID);
    }

    @Test
    public void reactDeckActivationIsIdempotent() {
        when(deckService.getDeck(DECK_ID)).thenReturn(deck(OWNER_ID));
        when(reactionRepository.existsById(any())).thenReturn(true);

        assertTrue(service.reactDeck(USER_ID, DECK_ID, ReactionType.SPICY, true));

        verify(reactionRepository, never()).save(any());
        verify(messageProducer, never()).publishDeckSync(any());
    }

    @Test
    public void reactDeckDeactivationDeletesAndSyncs() {
        when(deckService.getDeck(DECK_ID)).thenReturn(deck(OWNER_ID));
        when(reactionRepository.existsById(any())).thenReturn(true);

        assertTrue(service.reactDeck(USER_ID, DECK_ID, ReactionType.SPICY, false));

        verify(reactionRepository).deleteById(any());
        verify(messageProducer).publishDeckSync(DECK_ID);
    }

    @Test
    public void reactDeckRejectsCommentReaction() {
        assertFalse(service.reactDeck(USER_ID, DECK_ID, ReactionType.THUMBS_UP, true));

        verify(reactionRepository, never()).save(any());
    }

    @Test
    public void reactCommentRejectsDeckReaction() {
        assertFalse(service.reactComment(USER_ID, 55, ReactionType.SPICY, true));

        verify(reactionRepository, never()).save(any());
    }

    @Test
    public void reactDeckUnknownDeckReturnsFalse() {
        when(deckService.getDeck(DECK_ID)).thenReturn(null);

        assertFalse(service.reactDeck(USER_ID, DECK_ID, ReactionType.SPICY, true));

        verify(reactionRepository, never()).save(any());
    }

    @Test
    public void reactDeckAnonymousReturnsFalse() {
        assertFalse(service.reactDeck(null, DECK_ID, ReactionType.SPICY, true));

        verify(reactionRepository, never()).save(any());
    }

    @Test
    public void reactCommentActivationSaves() {
        CommentEntity comment = new CommentEntity();
        comment.setId(55);
        comment.setUser(OWNER_ID);
        comment.setDeleted(false);
        when(commentRepository.findById(55)).thenReturn(Optional.of(comment));
        when(reactionRepository.existsById(any())).thenReturn(false);

        assertTrue(service.reactComment(USER_ID, 55, ReactionType.THUMBS_UP, true));

        verify(reactionRepository).save(any(ReactionEntity.class));
    }

    @Test
    public void reactCommentDeletedReturnsFalse() {
        CommentEntity comment = new CommentEntity();
        comment.setId(55);
        comment.setUser(OWNER_ID);
        comment.setDeleted(true);
        when(commentRepository.findById(55)).thenReturn(Optional.of(comment));

        assertFalse(service.reactComment(USER_ID, 55, ReactionType.THUMBS_UP, true));

        verify(reactionRepository, never()).save(any());
    }

    @Test
    public void getReactionsSummarizesCountsAndReactedInFixedOrder() {
        when(reactionRepository.findByIdTargetTypeAndIdTargetId(ReactionTargetType.DECK, DECK_ID)).thenReturn(List.of(
                reaction(OWNER_ID, ReactionType.SPICY),
                reaction(USER_ID, ReactionType.SPICY),
                reaction(USER_ID, ReactionType.WOULD_PLAY)));

        List<ApiReactionSummary> summaries = service.getReactions(ReactionTargetType.DECK, DECK_ID, USER_ID);

        assertEquals(2, summaries.size());
        assertEquals(ReactionType.WOULD_PLAY, summaries.get(0).getReaction());
        assertEquals(1, summaries.get(0).getCount());
        assertTrue(summaries.get(0).isReacted());
        assertEquals(ReactionType.SPICY, summaries.get(1).getReaction());
        assertEquals(2, summaries.get(1).getCount());
        assertTrue(summaries.get(1).isReacted());
    }

    @Test
    public void getReactionsAnonymousNeverReacted() {
        when(reactionRepository.findByIdTargetTypeAndIdTargetId(ReactionTargetType.DECK, DECK_ID))
                .thenReturn(List.of(reaction(OWNER_ID, ReactionType.SPICY)));

        List<ApiReactionSummary> summaries = service.getReactions(ReactionTargetType.DECK, DECK_ID, null);

        assertEquals(1, summaries.size());
        assertFalse(summaries.get(0).isReacted());
    }

    @Test
    public void getReactionsBulkGroupsByTarget() {
        ReactionEntity commentReaction = new ReactionEntity();
        commentReaction.setId(new ReactionEntity.ReactionId(USER_ID, ReactionTargetType.COMMENT, "55", ReactionType.HEART));
        when(reactionRepository.findByIdTargetTypeAndIdTargetIdIn(ReactionTargetType.COMMENT, List.of("55", "56")))
                .thenReturn(List.of(commentReaction));

        Map<String, List<ApiReactionSummary>> result = service.getReactionsBulk(ReactionTargetType.COMMENT, List.of("55", "56"), USER_ID);

        assertEquals(1, result.size());
        assertEquals(1, result.get("55").size());
        assertEquals(ReactionType.HEART, result.get("55").get(0).getReaction());
        assertTrue(result.get("55").get(0).isReacted());
    }
}
