package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiCommonMapper;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.db.CommentMapper;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbComment;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.model.api.ApiComment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.vtesdecks.api.util.ApiUtils.getProfileImage;

@Slf4j
@Service
public class ApiCommentService {
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ApiUserNotificationService userNotificationService;


    public List<ApiComment> getComments(String deckId) {
        DbUser user = getUser();
        List<ApiComment> comments = new ArrayList<>();
        for (DbComment dbComment : getActiveComments(getPageIdentifier(deckId))) {
            comments.add(getComment(user, dbComment));
        }
        return comments;
    }

    public ApiComment addComment(ApiComment comment) {
        DbUser user = getUser();
        if (user != null) {
            DbComment dbComment = new DbComment();
            dbComment.setUser(user.getId());
            //Deprecated funcionality (reply in thread)
            //dbComment.setParent(comment.getParent());
            dbComment.setPageIdentifier(getPageIdentifier(comment.getDeckId()));
            dbComment.setContent(comment.getContent());
            commentMapper.insert(dbComment);
            sendNotifications(comment, dbComment);
            return getComment(user, commentMapper.selectById(dbComment.getId()));
        }
        return null;
    }

    private void sendNotifications(ApiComment comment, DbComment dbComment) {
        try {
            List<DbComment> commentList = getActiveComments(dbComment.getPageIdentifier());
            userNotificationService.processCommentNotification(comment.getDeckId(), dbComment, commentList);
        } catch (Exception e) {
            log.error("Unexpected error creating comment notification for deckId {} with comment {}", comment.getDeckId(), comment, e);
        }
    }

    public ApiComment modifyComment(ApiComment comment) {
        DbUser user = getUser();
        if (user != null) {
            DbComment dbComment = commentMapper.selectById(comment.getId());
            if (user.isAdmin() || dbComment.getUser().equals(user.getId())) {
                dbComment.setContent(comment.getContent());
                commentMapper.update(dbComment);
                userNotificationService.updateNotification(comment.getId(), comment.getContent());
                return getComment(user, commentMapper.selectById(dbComment.getId()));
            }
        }
        return null;
    }

    public boolean deleteComment(Integer id) {
        DbUser user = getUser();
        if (user != null) {
            DbComment dbComment = commentMapper.selectById(id);
            if (user.isAdmin() || dbComment.getUser().equals(user.getId())) {
                dbComment.setDeleted(true);
                commentMapper.update(dbComment);
                userNotificationService.deleteNotification(dbComment.getId());
                return true;
            }
        }
        return false;
    }

    private String getPageIdentifier(String deckId) {
        return "deck_" + deckId;
    }

    private DbUser getUser() {
        Integer userId = ApiUtils.extractUserId();
        return userId != null ? userMapper.selectById(userId) : null;
    }

    private ApiComment getComment(DbUser user, DbComment dbComment) {
        ApiComment comment = new ApiComment();
        comment.setId(dbComment.getId());
        comment.setCreated(ApiCommonMapper.map(dbComment.getCreationDate()));
        comment.setModified(ApiCommonMapper.map(dbComment.getModificationDate()));
        comment.setContent(dbComment.getContent());
        DbUser commentUser = userMapper.selectById(dbComment.getUser());
        if (commentUser != null) {
            comment.setFullName(commentUser.getDisplayName());
            comment.setProfileImage(getProfileImage(commentUser));
            comment.setCreatedByAdmin(commentUser.isAdmin());
            comment.setCreatedByCurrentUser(user != null && user.getId().equals(commentUser.getId()));
        }
        return comment;
    }

    private List<DbComment> getActiveComments(String pageIdentifier) {
        return commentMapper.selectByPageIdentifier(pageIdentifier)
                .stream()
                .filter(comment -> !comment.isDeleted())
                .collect(Collectors.toList());
    }

}
