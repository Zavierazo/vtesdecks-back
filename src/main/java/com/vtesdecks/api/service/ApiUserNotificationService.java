package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiUserNotificationMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.enums.UserNotificationType;
import com.vtesdecks.jpa.entity.CommentEntity;
import com.vtesdecks.jpa.entity.UserNotificationEntity;
import com.vtesdecks.jpa.repositories.UserNotificationRepository;
import com.vtesdecks.model.api.ApiUserNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ApiUserNotificationService {
    private static final int NOTIFICATION_MAX_LENGTH = 1000;
    public static final String NEW_LINE = "<br/>";
    @Autowired
    private UserNotificationRepository userNotificationRepository;
    @Autowired
    private ApiUserNotificationMapper apiUserNotificationMapper;
    @Autowired
    private DeckIndex deckIndex;

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
        Deck deck = deckIndex.get(deckId);
        if (deck != null) {
            /* Case 1: Notify deck owner
             *  Exceptions:
             * - Comment user is the same as deck owner
             */
            if (deck.getUser() != null && !deck.getUser().equals(comment.getUser())) {
                addCommentNotification(comment, deck, deck.getUser());
            }
            /* Case 2: Notify last comment user when someone reply
             * Exceptions:
             * - Last comment user is the same as current comment
             * - Last comment user is the owner of the deck(covered by case 1)
             */
            Integer replyUserId = commentList.size() > 1 ? commentList.get(commentList.size() - 2).getUser() : null;
            if (replyUserId != null && !replyUserId.equals(comment.getUser()) && !replyUserId.equals(deck.getUser())) {
                addCommentNotification(comment, deck, replyUserId);
            }
        } else {
            log.warn("Deck {} not found for comment notification", deckId);
        }
    }

    private void addCommentNotification(CommentEntity comment, Deck deck, Integer notifyUserId) {
        UserNotificationEntity userNotification = new UserNotificationEntity();
        userNotification.setUser(notifyUserId);
        userNotification.setReferenceId(comment.getId());
        userNotification.setRead(false);
        userNotification.setType(UserNotificationType.COMMENT);
        userNotification.setNotification(fixLimit("<strong>New comment on \"" + deck.getName() + "\":</strong>" + NEW_LINE + comment.getContent()));
        userNotification.setLink("/deck/" + deck.getId());
        userNotificationRepository.save(userNotification);
    }


    public void updateNotification(Integer referenceId, String notification) {
        try {
            UserNotificationEntity userNotification = userNotificationRepository.findByReferenceId(referenceId);
            if (userNotification != null) {
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
            log.error("Unexpected error editing comment notification with referenceId {} and new notification {}", referenceId, notification, e);
        }
    }

    public void deleteNotification(Integer referenceId) {
        userNotificationRepository.deleteByReferenceId(referenceId);
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
        userNotification.setReferenceId(0);
        userNotification.setRead(false);
        userNotification.setType(UserNotificationType.LINK);
        userNotification.setNotification("<strong>Support VTESDecks on Patreon\uD83E\uDD87</strong><br/>Help us cover server costs and keep improving the site.");
        userNotification.setLink("https://www.patreon.com/bePatron?u=41542528");
        userNotificationRepository.save(userNotification);

    }

}
