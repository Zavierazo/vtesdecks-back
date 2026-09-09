package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.AchievementService;
import com.vtesdecks.api.service.ApiCommentService;
import com.vtesdecks.api.service.ApiDeckService;
import com.vtesdecks.api.service.ApiReactionService;
import com.vtesdecks.api.service.ApiUserService;
import com.vtesdecks.api.service.EmailActionService;
import com.vtesdecks.api.service.UserSecurityService;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiAchievementFamily;
import com.vtesdecks.model.api.ApiComment;
import com.vtesdecks.model.api.ApiCommentReaction;
import com.vtesdecks.model.api.ApiDeckReaction;
import com.vtesdecks.model.api.ApiFavoriteDeck;
import com.vtesdecks.model.api.ApiFollowUser;
import com.vtesdecks.model.api.ApiRateDeck;
import com.vtesdecks.model.api.ApiUser;
import com.vtesdecks.model.api.ApiUserSettings;
import com.vtesdecks.model.api.ApiUserSettingsResponse;
import com.vtesdecks.service.DeckUserService;
import com.vtesdecks.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/api/1.0/user")
@Slf4j
public class ApiUserController {

    @Autowired
    private DeckUserService deckUserService;
    @Autowired
    private ApiDeckService deckService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApiCommentService apiCommentService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApiUserService userService;
    @Autowired
    private UserSecurityService security;
    @Autowired
    private ApiReactionService apiReactionService;
    @Autowired
    private AchievementService achievementService;

    @GetMapping(value = "/achievements", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ApiAchievementFamily> achievements() {
        return achievementService.getMine(ApiUtils.extractUserId());
    }

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
    @Transactional
    public ApiUser refreshUser() {
        Integer userId = ApiUtils.extractUserId();
        UserEntity user = userRepository.findById(userId).orElseThrow();
        List<String> roles = userRepository.selectRolesByUserId(user.getId());
        achievementService.activity(userId);
        return userService.getAuthenticatedUser(user, roles);
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

    @RequestMapping(method = RequestMethod.POST, value = "/decks/reaction", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean deckReaction(@RequestBody ApiDeckReaction deckReaction) {
        log.debug("Deck reaction {} user {}", deckReaction, ApiUtils.extractUserId());
        return apiReactionService.reactDeck(ApiUtils.extractUserId(), deckReaction.getDeck(), deckReaction.getReaction(), deckReaction.getActive());
    }

    @RequestMapping(method = RequestMethod.POST, value = "/comments/{id}/reaction", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean commentReaction(@PathVariable Integer id, @RequestBody ApiCommentReaction commentReaction) {
        log.debug("Comment {} reaction {} user {}", id, commentReaction, ApiUtils.extractUserId());
        return apiReactionService.reactComment(ApiUtils.extractUserId(), id, commentReaction.getReaction(), commentReaction.getActive());
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
    @Transactional
    public ApiUserSettingsResponse changeSettings(@RequestBody ApiUserSettings apiUserSettings) {
        log.info("Change settings user {} with displayName {}", ApiUtils.extractUserId(), apiUserSettings.getDisplayName());
        ApiUserSettingsResponse response = new ApiUserSettingsResponse();
        UserEntity user = userRepository.findById(ApiUtils.extractUserId()).orElseThrow();
        boolean changingPassword = StringUtils.isNotEmpty(apiUserSettings.getNewPassword())
                || StringUtils.isNotEmpty(apiUserSettings.getPassword());
        // Validate credentials before mutating a managed entity (including profile fields).
        if (changingPassword && (!EmailActionService.validPassword(apiUserSettings.getNewPassword())
                || apiUserSettings.getPassword() == null
                || !passwordEncoder.matches(apiUserSettings.getPassword(), user.getPassword()))) {
            response.setSuccessful(false);
            response.setMessage("Current password is incorrect or the new password is invalid.");
            return response;
        }
        if (!changingPassword && StringUtils.isBlank(apiUserSettings.getDisplayName())
                && apiUserSettings.getCardPrintingPreference() == null) {
            return response;
        }
        if (user != null) {
            boolean requireDeckRefresh = false;
            if (StringUtils.isNotBlank(apiUserSettings.getProfileImage())) {
                String validationImage = Utils.isValidImage(apiUserSettings.getProfileImage());
                if (validationImage != null) {
                    response.setSuccessful(false);
                    response.setMessage("Invalid profile image: " + validationImage);
                    return response;
                }
                user.setProfileImage(apiUserSettings.getProfileImage());
            } else {
                user.setProfileImage(null);
            }
            if (StringUtils.isNotBlank(apiUserSettings.getDisplayName())) {
                user.setDisplayName(apiUserSettings.getDisplayName());
                response.setSuccessful(true);
                requireDeckRefresh = true;
            }
            if (apiUserSettings.getCardPrintingPreference() != null) {
                user.setCardPrintingPreference(apiUserSettings.getCardPrintingPreference());
                response.setSuccessful(true);
            }
            if (changingPassword) {
                user.setPassword(passwordEncoder.encode(apiUserSettings.getNewPassword()));
                response.setSuccessful(true);
            }
            if (response.getSuccessful() != null && response.getSuccessful()) {
                userRepository.save(user);
                if (changingPassword) {
                    security.revoke(user);
                    response.setAuthenticatedUser(userService.getAuthenticatedUser(user, userRepository.selectRolesByUserId(user.getId())));
                }
                response.setMessage("Profile Settings changed!");
                if (requireDeckRefresh) {
                    deckUserService.refreshUserDecks(user.getId());
                }
            }
        }
        return response;
    }

    @PostMapping(value = "/follow", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean followUser(@RequestBody ApiFollowUser followUser) {
        log.debug("User follow action {} by user {}", followUser, ApiUtils.extractUserId());
        return userService.followUser(ApiUtils.extractUserId(), followUser.getUser(), followUser.getFollow());
    }

    @GetMapping(value = "/follow/{user}", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public Boolean isFollowing(@PathVariable String user) {
        log.debug("Check if user {} follows user {}", ApiUtils.extractUserId(), user);
        return userService.isFollowing(ApiUtils.extractUserId(), user);
    }

}
