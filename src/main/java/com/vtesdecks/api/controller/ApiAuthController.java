package com.vtesdecks.api.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.vtesdecks.api.service.ApiUserNotificationService;
import com.vtesdecks.api.service.ApiUserService;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiResponse;
import com.vtesdecks.model.api.ApiUser;
import com.vtesdecks.model.api.ApiUserCountry;
import com.vtesdecks.service.MailService;
import com.vtesdecks.service.OauthService;
import com.vtesdecks.service.RecaptchaService;
import com.vtesdecks.util.Utils;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.vtesdecks.util.Constants.USER_COUNTRY_HEADER;

@Controller
@RequestMapping("/api/1.0/auth")
@Slf4j
public class ApiAuthController {
    private static final String FORM_DATA_TOKEN = "token";
    private static final String FORM_DATA_USERNAME = "username";
    private static final String FORM_DATA_EMAIL = "email";
    private static final String FORM_DATA_PASSWORD = "password";
    private static final String FORM_DATA_CONFIRM_PASSWORD = "confirmPassword";
    private static final String FORM_DATA_RECAPTCHA = "g-recaptcha-response";
    public static final String EXPOSED_CREDENTIAL_CHECK = "Exposed-Credential-Check";
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MailService mailService;
    @Autowired
    private RecaptchaService recaptchaService;
    @Autowired
    private ApiUserService userService;
    @Autowired
    private ApiUserNotificationService userNotificationService;
    @Autowired
    private OauthService oauthService;

    @RequestMapping(method = RequestMethod.POST, value = "/login", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiUser login(HttpServletRequest httpServletRequest, @RequestParam Map<String, String> data) {
        String username = data.get(FORM_DATA_USERNAME);
        log.debug("Login request for {}", username);
        ApiUser user = new ApiUser();
        user.setUser(username);
        if (username != null) {
            UserEntity dbUser = userRepository.findByUsername(username);
            if (dbUser == null) {
                dbUser = userRepository.findByEmail(username);
            }
            if (dbUser != null) {
                String password = data.get(FORM_DATA_PASSWORD);
                if (password != null && passwordEncoder.matches(password, dbUser.getPassword())) {
                    List<String> roles = userRepository.selectRolesByUserId(dbUser.getId());
                    if (Boolean.FALSE.equals(dbUser.getValidated())) {
                        if (recaptchaService.isResponseValid(Utils.getIp(httpServletRequest), data.get(FORM_DATA_RECAPTCHA))
                                && (dbUser.getModificationDate() == null
                                || dbUser.getModificationDate().isBefore(LocalDateTime.now().minusMinutes(30)))) {
                            mailService.sendConfirmationMail(dbUser.getEmail(), userService.getJWTToken(dbUser, roles, true));
                            user.setMessage("Pending email verification! " +
                                    "We have sent another email for confirmation. " +
                                    "Please check your spam folder.");
                            log.warn("Pending email verification for {}", username);
                        } else {
                            user.setMessage("Check your email inbox to activate your account" +
                                    "(check your spam folder). " +
                                    "If you have problems, please contact support@vtesdecks.com");
                            log.warn("Check email verification for {}", username);
                        }
                    } else {
                        user = userService.getAuthenticatedUser(dbUser, roles);
                        log.info("Login request for {} success. Jwt token: {}", username, user.getToken());
                        String exposedCredentialCheck = httpServletRequest.getHeader(EXPOSED_CREDENTIAL_CHECK);
                        if (exposedCredentialCheck != null) {
                            log.info("User {} has leaked credentials with flag {}.", username, exposedCredentialCheck);
                        }
                    }
                }
            }
        }
        if (user.getToken() == null && user.getMessage() == null) {
            user.setMessage("You have entered an invalid username or password");
            log.warn("Invalid username or password for login {}", username);
        }
        return user;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/oauth/login", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiUser oauthLogin(HttpServletRequest httpServletRequest, @RequestParam Map<String, String> data) {
        String token = data.get(FORM_DATA_TOKEN);
        log.debug("Login request for {}", token);
        ApiUser user = new ApiUser();

        GoogleIdToken googleId = oauthService.validateOauthToken(token);
        if (googleId != null && googleId.getPayload() != null) {
            GoogleIdToken.Payload googlePayload = googleId.getPayload();
            UserEntity dbUser = userRepository.findByEmail(googlePayload.getEmail());
            if (dbUser == null) {
                // Create new user
                String emailUser = googlePayload.getEmail().split("@")[0];
                dbUser = new UserEntity();
                dbUser.setUsername("oauth_" + emailUser);
                dbUser.setEmail(googlePayload.getEmail());
                dbUser.setPassword(StringUtils.EMPTY); // No password for oauth users (no default login)
                dbUser.setValidated(true); // Oauth users are always validated
                dbUser.setAdmin(false);
                dbUser.setLoginHash(getRandomLoginHash());
                if (googlePayload.get("name") != null) {
                    dbUser.setDisplayName((String) googlePayload.get("name"));
                } else {
                    dbUser.setDisplayName(emailUser);
                }
                userRepository.save(dbUser);
                try {
                    userNotificationService.welcomeNotifications(dbUser.getId());
                } catch (Exception e) {
                    log.error("Error sending in-app notification for user {}", dbUser.getUsername(), e);
                }
                log.info("Oauth Register {} success, with user {}", dbUser.getEmail(), dbUser.getUsername());
            }
            List<String> roles = userRepository.selectRolesByUserId(dbUser.getId());
            if (Boolean.FALSE.equals(dbUser.getValidated())) {
                dbUser.setValidated(true);
                userRepository.save(dbUser);
                log.info("Validated user {} through oauth login", dbUser.getEmail());
            }
            user = userService.getAuthenticatedUser(dbUser, roles);
            log.info("Oauth Login request for {} success. Jwt token: {}", user.getEmail(), user.getToken());
        }

        if (user.getToken() == null && user.getMessage() == null) {
            user.setMessage("Invalid authentication");
            log.warn("Invalid oauth token {}", token);
        }
        return user;
    }


    @RequestMapping(method = RequestMethod.POST, value = "/create", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiResponse create(HttpServletRequest httpServletRequest, @RequestParam Map<String, String> data) {
        ApiResponse response = new ApiResponse();
        response.setSuccessful(false);
        if (recaptchaService.isResponseValid(Utils.getIp(httpServletRequest), data.get(FORM_DATA_RECAPTCHA))) {
            String username = StringUtils.lowerCase(data.get(FORM_DATA_USERNAME));
            String email = StringUtils.lowerCase(data.get(FORM_DATA_EMAIL));
            String password = data.get(FORM_DATA_PASSWORD);
            String confirmPassword = data.get(FORM_DATA_CONFIRM_PASSWORD);

            if (!(StringUtils.isNotBlank(username) && StringUtils.isAlphanumeric(username) && Normalizer.isNormalized(username, Normalizer.Form.NFKD)
                    && username.length() >= 5 && username.length() <= 25)) {
                response.setMessage(
                        "Invalid username. Only letters and numbers are allowed. Username length must be between 5 and 25 character.");
                log.warn("Invalid username register {}", username);
            } else if (!isValidEmailAddress(email)) {
                response.setMessage("Invalid email address");
                log.warn("Invalid email register {}", email);
            } else if (!password.equals(confirmPassword)) {
                response.setMessage("Passwords did not match.");
                log.warn("Invalid password register for user{}", username);
            } else {
                log.info("Register request for {} with email {}", username, email);
                UserEntity actual = userRepository.findByEmail(email);
                if (actual == null) {
                    actual = userRepository.findByUsername(username);
                }
                if (actual == null) {
                    UserEntity user = new UserEntity();
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setPassword(passwordEncoder.encode(password));
                    user.setValidated(false);
                    user.setAdmin(false);
                    user.setLoginHash(getRandomLoginHash());
                    user.setDisplayName(data.get(FORM_DATA_USERNAME));
                    UserEntity dbUser = userRepository.save(user);
                    mailService.sendConfirmationMail(dbUser.getEmail(), userService.getJWTToken(dbUser, new ArrayList<>(), true));
                    try {
                        userNotificationService.welcomeNotifications(dbUser.getId());
                    } catch (Exception e) {
                        log.error("Error sending in-app notification for user {}", username, e);
                    }
                    response.setSuccessful(true);
                    response.setMessage("Verification link has been sent to your email address!");
                    log.info("Register request for {} success", username);
                } else {
                    response.setMessage("User or email already exists.");
                }
            }
        } else {
            response.setMessage("Google Recaptcha has detected you as a bot and restricted access.");
            log.info("Bot detected while register user {}", data.get(FORM_DATA_USERNAME));
        }
        return response;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/forgot-password", produces = {
            MediaType.APPLICATION_JSON_VALUE
    })
    @ResponseBody
    public ApiResponse forgotPassword(HttpServletRequest
                                              httpServletRequest, @RequestParam Map<String, String> data) {
        ApiResponse response = new ApiResponse();
        response.setSuccessful(false);
        if (recaptchaService.isResponseValid(Utils.getIp(httpServletRequest), data.get(FORM_DATA_RECAPTCHA))) {
            String email = StringUtils.lowerCase(data.get(FORM_DATA_EMAIL));
            if (!isValidEmailAddress(email)) {
                response.setMessage("Invalid email address");
                log.warn("Invalid email forgot password {}", email);
            } else {
                log.info("Forgot password request with email {}", email);
                UserEntity user = userRepository.findByEmail(email);
                if (user != null) {
                    if (user.getForgotPasswordDate() == null || user.getForgotPasswordDate().isBefore(LocalDateTime.now().minusMinutes(30))) {
                        List<String> roles = userRepository.selectRolesByUserId(user.getId());
                        mailService.sendForgotPasswordMail(user.getEmail(), userService.getJWTToken(user, roles, true));
                        user.setForgotPasswordDate(LocalDateTime.now());
                        userRepository.save(user);
                        log.info("Forgot password request for {} success", email);
                    } else {
                        log.info("Forgot password request for {} duplicated", email);
                    }
                } else {
                    log.warn("Invalid forgot password email {}", email);
                }
                response.setSuccessful(true);
                response.setMessage("Your forgot password request has been received. Please check your email for further instructions on how to reset your password.");
            }
        } else {
            response.setMessage("Google Recaptcha has detected you as a bot and restricted access.");
            log.info("Bot detected while register user {}", data.get(FORM_DATA_USERNAME));
        }
        return response;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/country", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ApiUserCountry getUserCountry(HttpServletRequest request) {
        ApiUserCountry userCountry = new ApiUserCountry();
        userCountry.setCountryCode(request.getHeader(USER_COUNTRY_HEADER));
        return userCountry;
    }


    private static boolean isValidEmailAddress(String email) {
        boolean result = true;
        try {
            InternetAddress emailAddr = new InternetAddress(email);
            emailAddr.validate();
        } catch (AddressException ex) {
            result = false;
        }
        return result;
    }

    private String getRandomLoginHash() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
