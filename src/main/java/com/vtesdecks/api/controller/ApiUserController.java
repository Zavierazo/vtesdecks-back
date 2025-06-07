package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCommentService;
import com.vtesdecks.api.service.ApiDeckService;
import com.vtesdecks.api.service.ApiUserService;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.db.UserMapper;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.model.api.ApiComment;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.model.api.ApiFavoriteDeck;
import com.vtesdecks.model.api.ApiRateDeck;
import com.vtesdecks.model.api.ApiResponse;
import com.vtesdecks.model.api.ApiUser;
import com.vtesdecks.model.api.ApiUserPassword;
import com.vtesdecks.model.api.ApiUserSettings;
import com.vtesdecks.service.DeckUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api/1.0/user")
@Slf4j
public class ApiUserController {

    @Autowired
    private DeckUserService deckUserService;
    @Autowired
    private ApiDeckService deckService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ApiCommentService apiCommentService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApiUserService userService;

    @RequestMapping(method = RequestMethod.GET, value = "/validate", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean validate() {
        return true;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/refresh", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiUser refreshUser() {
        DbUser user = userMapper.selectById(ApiUtils.extractUserId());
        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        return userService.getAuthenticatedUser(user, roles);
    }

    @RequestMapping(method = RequestMethod.POST, value = "/verify", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean verify() {
        log.info("Verify user {}", ApiUtils.extractUserId());
        DbUser user = userMapper.selectById(ApiUtils.extractUserId());
        if (user != null) {
            user.setValidated(true);
            userMapper.update(user);
            return true;
        } else {
            return false;
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/decks/bookmark", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    @Deprecated
    public List<ApiDeck> bookmarkDecks() {
        log.info("Deprectaded /api/1.0/user/bookmark");
        List<String> deckIds = deckUserService.getFavoriteDecks(ApiUtils.extractUserId());
        return deckIds.stream().map(deckId -> deckService.getDeck(deckId, false)).collect(Collectors.toList());
    }

    @RequestMapping(method = RequestMethod.GET, value = "/decks", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    @Deprecated
    public List<ApiDeck> decks() {
        log.info("Deprectaded /api/1.0/user/decks");
        List<String> deckIds = deckUserService.getUserDecks(ApiUtils.extractUserId());
        return deckIds.stream().map(deckId -> deckService.getDeck(deckId, false)).collect(Collectors.toList());
    }


    @RequestMapping(method = RequestMethod.POST, value = "/decks/rating", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean rating(@RequestBody ApiRateDeck rateDeck) {
        log.debug("Deck rating {} user {}", rateDeck, ApiUtils.extractUserId());
        deckUserService.rate(ApiUtils.extractUserId(), rateDeck.getDeck(), rateDeck.getRating());
        return true;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/decks/bookmark", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean bookmark(@RequestBody ApiFavoriteDeck favoriteDeck) {
        log.debug("Deck bookmark {} user {}", favoriteDeck, ApiUtils.extractUserId());
        return deckUserService.favorite(ApiUtils.extractUserId(), favoriteDeck.getDeck(), favoriteDeck.getFavorite());
    }

    @RequestMapping(method = RequestMethod.POST, value = "/comments", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiComment addComment(@RequestBody ApiComment apiComment) {
        log.debug("Deck comment {} user {}", apiComment, ApiUtils.extractUserId());
        return apiCommentService.addComment(apiComment);
    }

    @RequestMapping(method = RequestMethod.PUT, value = "/comments/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiComment modifyComment(@PathVariable Integer id, @RequestBody ApiComment apiComment) {
        log.debug("Deck modify comment {} user {}", apiComment, ApiUtils.extractUserId());
        if (!id.equals(apiComment.getId())) {
            return null;
        }
        return apiCommentService.modifyComment(apiComment);
    }

    @RequestMapping(method = RequestMethod.DELETE, value = "/comments/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean deleteComments(@PathVariable Integer id) {
        log.debug("Deck delete  comment {} user {}", id, ApiUtils.extractUserId());
        return apiCommentService.deleteComment(id);
    }


    @RequestMapping(method = RequestMethod.PUT, value = "/settings", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiResponse changeSettings(@RequestBody ApiUserSettings apiUserSettings) {
        log.info("Change settings user {} with displayName {}", ApiUtils.extractUserId(), apiUserSettings.getDisplayName());
        ApiResponse response = new ApiResponse();
        DbUser user = userMapper.selectById(ApiUtils.extractUserId());
        if (user != null) {
            boolean requireDeckRefresh = false;
            if (StringUtils.isNotBlank(apiUserSettings.getDisplayName())) {
                user.setDisplayName(apiUserSettings.getDisplayName());
                response.setSuccessful(true);
                requireDeckRefresh = true;
            }
            if (StringUtils.isNotBlank(apiUserSettings.getPassword()) && StringUtils.isNotBlank(apiUserSettings.getNewPassword())) {
                if (passwordEncoder.matches(apiUserSettings.getPassword(), user.getPassword())) {
                    user.setPassword(passwordEncoder.encode(apiUserSettings.getNewPassword()));
                    response.setSuccessful(true);
                } else {
                    response.setSuccessful(false);
                    response.setMessage("Old Password doesn't match!");
                }
            }
            if (response.getSuccessful() != null && response.getSuccessful()) {
                userMapper.update(user);
                response.setMessage("Profile Settings changed!");
                if (requireDeckRefresh) {
                    deckUserService.refreshUserDecks(user.getId());
                }
            }
        }
        return response;
    }

    @RequestMapping(method = RequestMethod.PUT, value = "/reset-password", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiResponse resetPassword(@RequestBody ApiUserPassword apiUserPassword) {
        log.info("Change password for user {}", ApiUtils.extractUserId());
        ApiResponse response = new ApiResponse();
        response.setSuccessful(false);
        DbUser user = userMapper.selectById(ApiUtils.extractUserId());
        if (user != null) {
            if (!apiUserPassword.getEmail().equalsIgnoreCase(user.getEmail())) {
                log.warn("Invalid email when reset password");
                response.setMessage("Invalid reset password link!");
            } else {
                user.setPassword(passwordEncoder.encode(apiUserPassword.getPassword()));
                userMapper.update(user);
                response.setSuccessful(true);
                response.setMessage("Your password has been successfully reset. You can now log in using your new password.");
            }
        } else {
            log.warn("Invalid user when reset password");
            response.setMessage("Invalid reset password link!");
        }
        return response;
    }

}
