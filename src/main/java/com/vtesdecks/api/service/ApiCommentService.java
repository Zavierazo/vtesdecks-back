package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCommonMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entity.CommentEntity;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.CommentRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.vtesdecks.api.util.ApiUtils.getProfileImage;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCommentService {
    private static final String SUPPORTER_ROLE = "supporter";
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ApiUserNotificationService userNotificationService;


    public List<ApiComment> getComments(String deckId) {
        UserEntity user = getUser();
        List<ApiComment> comments = new ArrayList<>();
        for (CommentEntity commentEntity : getActiveComments(getPageIdentifier(deckId))) {
            comments.add(getComment(user, Optional.of(commentEntity)));
        }
        return comments;
    }

    public ApiComment addComment(ApiComment comment) {
        UserEntity user = getUser();
        if (user != null) {
            CommentEntity commentEntity = new CommentEntity();
            commentEntity.setUser(user.getId());
            //Deprecated funcionality (reply in thread)
            //commentEntity.setParent(comment.getParent());
            commentEntity.setPageIdentifier(getPageIdentifier(comment.getDeckId()));
            commentEntity.setContent(comment.getContent());
            commentEntity.setDeleted(false);
            commentRepository.save(commentEntity);
            sendNotifications(comment, commentEntity);
            return getComment(user, commentRepository.findById(commentEntity.getId()));
        }
        return null;
    }

    private void sendNotifications(ApiComment comment, CommentEntity dbComment) {
        try {
            List<CommentEntity> commentList = getActiveComments(dbComment.getPageIdentifier());
            userNotificationService.processCommentNotification(comment.getDeckId(), dbComment, commentList);
        } catch (Exception e) {
            log.error("Unexpected error creating comment notification for deckId {} with comment {}", comment.getDeckId(), comment, e);
        }
    }

    public ApiComment modifyComment(ApiComment comment) {
        UserEntity user = getUser();
        if (user != null) {
            Optional<CommentEntity> optionalCommentEntity = commentRepository.findById(comment.getId());
            if (optionalCommentEntity.isEmpty()) {
                return null;
            }
            CommentEntity commentEntity = optionalCommentEntity.get();
            if ((user.getAdmin() != null && user.getAdmin()) || commentEntity.getUser().equals(user.getId())) {
                commentEntity.setContent(comment.getContent());
                commentRepository.save(commentEntity);
                userNotificationService.updateCommentNotification(comment.getId(), comment.getContent());
                return getComment(user, commentRepository.findById(commentEntity.getId()));
            }
        }
        return null;
    }

    public boolean deleteComment(Integer id) {
        UserEntity user = getUser();
        if (user != null) {
            Optional<CommentEntity> optionalCommentEntity = commentRepository.findById(id);
            if (optionalCommentEntity.isEmpty()) {
                return false;
            }
            CommentEntity commentEntity = optionalCommentEntity.get();
            if ((user.getAdmin() != null && user.getAdmin()) || commentEntity.getUser().equals(user.getId())) {
                commentEntity.setDeleted(true);
                commentRepository.save(commentEntity);
                userNotificationService.deleteCommentNotification(commentEntity.getId());
                return true;
            }
        }
        return false;
    }

    private String getPageIdentifier(String deckId) {
        return "deck_" + deckId;
    }

    private UserEntity getUser() {
        Integer userId = ApiUtils.extractUserId();
        return userId != null ? userRepository.findById(userId).orElse(null) : null;
    }

    private ApiComment getComment(UserEntity user, Optional<CommentEntity> commentEntity) {
        return commentEntity.map(entity -> getComment(user, entity)).orElse(null);
    }

    private ApiComment getComment(UserEntity user, CommentEntity commentEntity) {
        ApiComment comment = new ApiComment();
        comment.setId(commentEntity.getId());
        comment.setCreated(ApiCommonMapper.map(commentEntity.getCreationDate()));
        comment.setModified(ApiCommonMapper.map(commentEntity.getModificationDate()));
        comment.setContent(commentEntity.getContent());
        UserEntity commentUser = userRepository.findById(commentEntity.getUser()).orElse(null);
        if (commentUser != null) {
            List<String> roles = userRepository.selectRolesByUserId(commentUser.getId());
            comment.setFullName(commentUser.getDisplayName());
            comment.setProfileImage(getProfileImage(commentUser));
            comment.setCreatedByAdmin(commentUser.getAdmin() != null && commentUser.getAdmin());
            comment.setCreatedBySupporter(roles.contains(SUPPORTER_ROLE));
            comment.setCreatedByCurrentUser(user != null && user.getId().equals(commentUser.getId()));
        }
        return comment;
    }

    private List<CommentEntity> getActiveComments(String pageIdentifier) {
        return commentRepository.findByPageIdentifier(pageIdentifier)
                .stream()
                .filter(comment -> Boolean.FALSE.equals(comment.getDeleted()))
                .sorted(Comparator.comparing(CommentEntity::getCreationDate))
                .toList();
    }

}
