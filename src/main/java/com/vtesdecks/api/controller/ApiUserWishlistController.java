package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiWishlistService;
import com.vtesdecks.model.api.ApiWishlistCard;
import com.vtesdecks.model.api.ApiWishlistPage;
import com.vtesdecks.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/1.0/user/wishlist")
@RequiredArgsConstructor
@Slf4j
public class ApiUserWishlistController {

    private final ApiWishlistService wishlistService;

    @GetMapping(value = "/cards", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiWishlistPage<ApiWishlistCard> getCards(HttpServletRequest request, @RequestParam Integer page, @RequestParam Integer size, @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortDirection, @RequestParam Map<String, String> params) throws Exception {
        return wishlistService.getWishlist(page, size, sortBy, sortDirection, params, Utils.getCurrencyCode(request));
    }

    @PostMapping(value = "/cards", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiWishlistCard createCard(HttpServletRequest request, @RequestBody ApiWishlistCard card) throws Exception {
        return wishlistService.addCard(card, Utils.getCurrencyCode(request));
    }

    @PutMapping(value = "/cards/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiWishlistCard updateCard(HttpServletRequest request, @PathVariable Integer id, @RequestBody ApiWishlistCard card) throws Exception {
        return wishlistService.updateCard(id, card, Utils.getCurrencyCode(request));
    }

    @DeleteMapping(value = "/cards/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Boolean deleteCard(@PathVariable Integer id) throws Exception {
        return wishlistService.deleteCard(id);
    }

    @PutMapping(value = "/visibility", produces = MediaType.APPLICATION_JSON_VALUE)
    public Boolean updateVisibility(@RequestParam Boolean publicVisibility) throws Exception {
        return wishlistService.updateVisibility(publicVisibility);
    }
}
