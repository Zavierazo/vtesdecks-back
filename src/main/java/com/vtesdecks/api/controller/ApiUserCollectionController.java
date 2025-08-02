package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCollectionService;
import com.vtesdecks.model.CollectionType;
import com.vtesdecks.model.api.ApiCollection;
import com.vtesdecks.model.api.ApiCollectionBinder;
import com.vtesdecks.model.api.ApiCollectionCard;
import com.vtesdecks.model.api.ApiCollectionCardStats;
import com.vtesdecks.model.api.ApiCollectionImport;
import com.vtesdecks.model.api.ApiCollectionPage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@RestController
@RequestMapping("/api/1.0/user/collections")
@RequiredArgsConstructor
@Slf4j
public class ApiUserCollectionController {
    protected static final List<String> ALLOWED_FILTERS = List.of("binderId", "cardType", "set", "cardId", "cardName");

    private final ApiCollectionService collectionService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollection getCollection() throws Exception {
        return collectionService.getCollection();
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollection resetCollection() throws Exception {
        return collectionService.resetCollection();
    }

    @GetMapping(value = "/binders", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiCollectionBinder> getBinders() throws Exception {
        return collectionService.getBinders();
    }

    @GetMapping(value = "/binders/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionBinder getBinder(@PathVariable Integer id) throws Exception {
        return collectionService.getBinder(id);
    }

    @PostMapping(value = "/binders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionBinder createBinder(@RequestBody ApiCollectionBinder binder) throws Exception {
        return collectionService.createBinder(binder);
    }

    @PutMapping(value = "/binders/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionBinder updateBinder(@PathVariable Integer id, @RequestBody ApiCollectionBinder binder) throws Exception {
        return collectionService.updateBinder(id, binder);
    }

    @DeleteMapping(value = "/binders/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Boolean deleteBinder(@PathVariable Integer id, @RequestParam(required = false, defaultValue = "false") Boolean deleteCards) throws Exception {
        return collectionService.deleteBinder(id, deleteCards);
    }

    @GetMapping(value = "/cards", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionPage<ApiCollectionCard> getCards(@RequestParam Integer page, @RequestParam Integer size, @RequestParam(required = false) String groupBy, @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortDirection, @RequestParam Map<String, String> params) throws Exception {
        Map<String, String> filters = params != null ? params.entrySet().stream()
                .filter(entry -> ALLOWED_FILTERS.contains(entry.getKey()))
                .filter(entry -> !isEmpty(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();
        return collectionService.getCards(page, size, groupBy, sortBy, sortDirection, filters);
    }

    @PostMapping(value = "/cards", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionCard createCard(HttpServletResponse response, @RequestBody ApiCollectionCard card) throws Exception {
        return collectionService.createCards(card, response);
    }

    @GetMapping(value = "/cards/{ids}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiCollectionCard> getCardsById(@PathVariable List<Integer> ids) {
        return collectionService.getCardsById(ids);
    }

    @PutMapping(value = "/cards/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionCard updateCard(HttpServletResponse response, @PathVariable Integer id, @RequestBody ApiCollectionCard card) throws Exception {
        return collectionService.updateCard(id, card, response);
    }

    @DeleteMapping(value = "/cards/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Boolean deleteCard(@PathVariable List<Integer> id) throws Exception {
        return collectionService.deleteCard(id);
    }

    @PatchMapping(value = "/cards/{id}/binders", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionCard addCardToBinder(@PathVariable Integer id, @RequestParam(required = false) Integer binderId, @RequestParam Integer quantity) throws Exception {
        return collectionService.moveCardToBinder(id, binderId, quantity);
    }

    @PatchMapping(value = "/cards/{ids}/bulk", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiCollectionCard> bulkEditCards(HttpServletResponse response, @PathVariable List<Integer> ids, @RequestParam(required = false) Integer binderId, @RequestParam(required = false) String condition, @RequestParam(required = false) String language) throws Exception {
        return collectionService.bulkEditCards(ids, binderId, condition, language, response);
    }

    @GetMapping(value = "/cards/export")
    public void exportCards(HttpServletResponse response, @RequestParam(required = false) Integer binderId) throws Exception {
        collectionService.export(response, binderId);
    }

    @PostMapping(value = "/cards/import/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionImport importCards(@PathVariable CollectionType type, @RequestParam("file") MultipartFile file, @RequestParam(required = false) Integer binderId) throws Exception {
        return collectionService.importCards(type, file, binderId);
    }

    @GetMapping(value = "/cards/{id}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCollectionCardStats cardStats(@PathVariable Integer id, @RequestParam(defaultValue = "false") Boolean mini) throws Exception {
        return collectionService.getCardStats(id, mini);
    }
}
