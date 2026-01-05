package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiUserNotificationMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.enums.UserNotificationType;
import com.vtesdecks.jpa.entity.CommentEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.entity.UserFollowerEntity;
import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.jpa.repositories.UserFollowerRepository;
import com.vtesdecks.jpa.repositories.UserNotificationRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiUserNotification;
import com.vtesdecks.service.DeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUserNotificationService {
    private static final int NOTIFICATION_MAX_LENGTH = 1000;
    public static final String NEW_LINE = "<br/>";
    private final UserNotificationRepository userNotificationRepository;
    private final ApiUserNotificationMapper apiUserNotificationMapper;
    private final DeckService deckService;
    private final UserFollowerRepository userFollowerRepository;
    private final UserRepository userRepository;

    public Integer notificationUnreadCount(Integer userId) {
        if (userId == null) {
            return 0;
        } else {
            return userNotificationRepository.countUnreadByUser(userId);
        }
    }

    public List<ApiUserNotification> getUserNotifications() {
        Integer userId = ApiUtils.extractUserId();
        if (userId == null) {
            return Collections.emptyList();
        } else {
            List<UserNotificationEntity> userNotifications = userNotificationRepository.findByUserOrderByCreationDateDesc(userId);
            return apiUserNotificationMapper.map(userNotifications);
        }
    }

    public void markAsRead(Integer id) {
        Integer userId = ApiUtils.extractUserId();
        if (userId != null) {
            UserNotificationEntity userNotification = userNotificationRepository.findById(id).orElse(null);
            if (userNotification != null && userId.equals(userNotification.getUser()) && Boolean.FALSE.equals(userNotification.getRead())) {
                userNotification.setRead(true);
                userNotificationRepository.save(userNotification);
            }
        }
    }

    public void markAllAsRead() {
        Integer userId = ApiUtils.extractUserId();
        if (userId != null) {
            userNotificationRepository.updateReadAllByUser(userId);
        }
    }

    public void processCommentNotification(String deckId, CommentEntity comment, List<CommentEntity> commentList) {
        Deck deck = deckService.getDeck(deckId);
        if (deck != null) {
            /* Case 1: Notify deck owner
             *  Exceptions:
             * - Comment user is the same as deck owner
             */
            if (deck.getUser() != null && !comment.getUser().equals(deck.getUser().getId())) {
                addCommentNotification(comment, deck, deck.getUser().getId());
            }
            /* Case 2: Notify last comment user when someone reply
             * Exceptions:
             * - Last comment user is the same as current comment
             * - Last comment user is the owner of the deck(covered by case 1)
             */
            Integer replyUserId = commentList.size() > 1 ? commentList.get(commentList.size() - 2).getUser() : null;
            if (replyUserId != null && !replyUserId.equals(comment.getUser()) && !replyUserId.equals(deck.getUser() != null ? deck.getUser().getId() : null)) {
                addCommentNotification(comment, deck, replyUserId);
            }
        } else {
            log.warn("Deck {} not found for comment notification", deckId);
        }
    }

    private void addCommentNotification(CommentEntity comment, Deck deck, Integer notifyUserId) {
        UserNotificationEntity userNotification = new UserNotificationEntity();
        userNotification.setUser(notifyUserId);
        userNotification.setReferenceId(String.valueOf(comment.getId()));
        userNotification.setRead(false);
        userNotification.setType(UserNotificationType.COMMENT);
        userNotification.setNotification(fixLimit("<strong>New comment on \"" + deck.getName() + "\":</strong>" + NEW_LINE + comment.getContent()));
        userNotification.setLink("/deck/" + deck.getId());
        userNotificationRepository.save(userNotification);
    }


    public void updateCommentNotification(Integer commentReferenceId, String notification) {
        try {
            List<UserNotificationEntity> userNotificationList = userNotificationRepository.findByReferenceId(String.valueOf(commentReferenceId));
            if (userNotificationList == null) {
                return;
            }
            for (UserNotificationEntity userNotification : userNotificationList) {
                int fixedPart = userNotification.getNotification().indexOf(NEW_LINE);
                if (fixedPart > 0) {
                    userNotification.setNotification(fixLimit(new StringBuilder()
                            .append(userNotification.getNotification(), 0, fixedPart)
                            .append(NEW_LINE)
                            .append(notification)
                            .toString()));
                } else {
                    userNotification.setNotification(fixLimit(notification));
                }
                userNotificationRepository.save(userNotification);
            }
        } catch (Exception e) {
            log.error("Unexpected error editing comment notification with referenceId {} and new notification {}", commentReferenceId, notification, e);
        }
    }

    public void deleteCommentNotification(Integer commentReferenceId) {
        userNotificationRepository.deleteByReferenceId(String.valueOf(commentReferenceId));
    }

    private static String fixLimit(String notification) {
        if (notification.length() > NOTIFICATION_MAX_LENGTH) {
            return notification.substring(0, NOTIFICATION_MAX_LENGTH - 3).concat("...");
        }
        return notification;
    }

    public void welcomeNotifications(Integer userId) {
        UserNotificationEntity userNotification = new UserNotificationEntity();
        userNotification.setUser(userId);
        userNotification.setReferenceId("0");
        userNotification.setRead(false);
        userNotification.setType(UserNotificationType.LINK);
        userNotification.setNotification("<strong>Support VTESDecks on Patreon\uD83E\uDD87</strong><br/>Help us cover server costs and keep improving the site.");
        userNotification.setLink("https://www.patreon.com/bePatron?u=41542528");
        userNotificationRepository.save(userNotification);

    }

    public void deckUpdateNotifications(DeckEntity deck) {
        try {
            List<UserNotificationEntity> userNotificationList = userNotificationRepository.findByReferenceId(deck.getId());
            if (userNotificationList == null) {
                userNotificationList = new ArrayList<>();
            }
            for (UserFollowerEntity follower : userFollowerRepository.findByIdFollowedId(deck.getUser())) {
                UserNotificationEntity userNotification = userNotificationList.stream()
                        .filter(un -> un.getUser().equals(follower.getId().getUserId()))
                        .findFirst()
                        .orElse(null);
                if (userNotification == null) {
                    userNotification = new UserNotificationEntity();
                    userNotification.setUser(follower.getId().getUserId());
                    userNotification.setReferenceId(deck.getId());
                    userNotification.setType(UserNotificationType.DECK);
                    userNotification.setLink("/deck/" + deck.getId());
                    userNotification.setNotification(fixLimit("<strong>New deck added by " + getAuthor(deck.getUser()) + ":</strong>" + NEW_LINE + deck.getName()));
                } else if (Boolean.TRUE.equals(userNotification.getRead())) {
                    userNotification.setNotification(fixLimit("<strong>Deck updated by " + getAuthor(deck.getUser()) + ":</strong>" + NEW_LINE + deck.getName()));
                }
                userNotification.setRead(false);
                userNotification.setCreationDate(LocalDateTime.now());
                userNotificationRepository.save(userNotification);
                log.info("New notification for follower {} about new deck {}", userNotification.getUser(), deck.getId());
            }
        } catch (Exception e) {
            log.error("Unexpected error editing deck notification {}", deck, e);
        }
    }

    public void deckDeleteNotifications(String deckId) {
        try {
            List<UserNotificationEntity> userNotificationList = userNotificationRepository.findByReferenceId(deckId);
            if (!isEmpty(userNotificationList)) {
                userNotificationRepository.deleteByReferenceId(deckId);
                log.info("Deleted {} notifications for deck {}", userNotificationList.size(), deckId);
            }
        } catch (Exception e) {
            log.error("Unexpected error deleting deck notification {}", deckId, e);
        }
    }

    private String getAuthor(Integer user) {
        return userRepository.findById(user).map(UserEntity::getDisplayName).orElse("unknown");
    }


}
