package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCollectionService;
import com.vtesdecks.model.api.ApiCollection;
import com.vtesdecks.model.api.ApiCollectionBinder;
import com.vtesdecks.model.api.ApiCollectionCard;
import com.vtesdecks.model.api.ApiCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.vtesdecks.api.controller.ApiUserCollectionController.ALLOWED_FILTERS;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@RestController
@RequestMapping("/api/1.0/collections")
@RequiredArgsConstructor
@Slf4j
public class ApiCollectionController {

    private final ApiCollectionService collectionService;


    @GetMapping(value = "/users/{username}/collection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollection getUserPublicCollection(@PathVariable String username) throws Exception {
        return collectionService.getUserPublicCollection(username);
    }

    @GetMapping(value = "/binders/{publicHash}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionBinder getBinder(@PathVariable String publicHash) throws Exception {
        return collectionService.getPublicBinder(publicHash);
    }

    @GetMapping(value = "/binders/{publicHash}/cards", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionPage<ApiCollectionCard> getCards(@PathVariable String publicHash, @RequestParam Integer page, @RequestParam Integer size, @RequestParam(required = false) String groupBy, @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortDirection, @RequestParam Map<String, String> params) throws Exception {
        Map<String, String> filters = params != null ? params.entrySet().stream()
                .filter(entry -> ALLOWED_FILTERS.contains(entry.getKey()))
                .filter(entry -> !isEmpty(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();
        return collectionService.getPublicCards(publicHash, page, size, groupBy, sortBy, sortDirection, filters);
    }


}
