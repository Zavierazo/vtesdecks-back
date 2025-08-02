package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.vtesdecks.api.mapper.ApiCollectionMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entities.Collection;
import com.vtesdecks.jpa.entities.CollectionBinder;
import com.vtesdecks.jpa.entities.CollectionCard;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepositoryCustom;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import com.vtesdecks.model.api.ApiCollectionCardCsv;
import com.vtesdecks.model.api.ApiCollectionImport;
import com.vtesdecks.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.opencsv.ICSVWriter.DEFAULT_QUOTE_CHARACTER;
import static com.opencsv.ICSVWriter.DEFAULT_SEPARATOR;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCollectionImportService {
    private static final List<String> ALLOWED_LANGUAGES = List.of("en", "fr", "es", "pt", "la");
    private static final String ERROR_IN_CARD = "Error in card ";

    private final CollectionRepository collectionRepository;
    private final CollectionBinderRepository collectionBinderRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CollectionCardRepositoryCustom collectionCardRepositoryCustom;
    private final ApiCollectionMapper apiCollectionMapper;
    private final SetCache setCache;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;

    public ApiCollectionImport importCardsVtesDecks(Collection collection, MultipartFile file, Integer binderId) {
        ApiCollectionImport apiCollectionImport = new ApiCollectionImport();
        apiCollectionImport.setSuccess(true);
        try {
            CsvToBean<ApiCollectionCardCsv> csvToBean = new CsvToBeanBuilder<ApiCollectionCardCsv>(new InputStreamReader(file.getInputStream()))
                    .withType(ApiCollectionCardCsv.class)
                    .withQuoteChar(DEFAULT_QUOTE_CHARACTER)
                    .withSeparator(DEFAULT_SEPARATOR)
                    .withThrowExceptions(false)
                    .build();

            List<ApiCollectionCardCsv> cards = csvToBean.parse();
            List<CsvException> csvExceptions = csvToBean.getCapturedExceptions();
            if (!csvExceptions.isEmpty()) {
                apiCollectionImport.setSuccess(false);
                apiCollectionImport.setErrors(csvExceptions.stream()
                        .map(csvException -> "Error in line " + csvException.getLineNumber() + ": " + csvException.getMessage())
                        .toList());
                return apiCollectionImport;
            }
            if (binderId != null) {
                if (binderId == 0) {
                    cards.forEach(card -> card.setBinder(null));
                } else {
                    Optional<CollectionBinder> binderOptional = collectionBinderRepository.findByCollectionIdAndId(collection.getId(), binderId);
                    if (binderOptional.isPresent()) {
                        cards.forEach(card -> card.setBinder(binderOptional.get().getName()));
                    } else {
                        apiCollectionImport.setSuccess(false);
                        apiCollectionImport.setErrors(List.of("Binder does not exist."));
                        return apiCollectionImport;
                    }
                }
            }
            List<String> validationError = validateCards(cards);
            if (!validationError.isEmpty()) {
                apiCollectionImport.setSuccess(false);
                apiCollectionImport.setErrors(validationError);
                return apiCollectionImport;
            }
            importCards(collection, cards);
        } catch (Exception e) {
            log.warn("Error importing collection from VTESdecks file: {}", e.getMessage(), e);
            apiCollectionImport.setErrors(List.of("Error reading file: " + e.getMessage()));
            apiCollectionImport.setSuccess(false);
        }
        return apiCollectionImport;
    }


    public ApiCollectionImport importCardsVDB(Collection collection, MultipartFile file, Integer binderId) {
        ApiCollectionImport apiCollectionImport = new ApiCollectionImport();
        apiCollectionImport.setSuccess(true);
        List<ApiCollectionCardCsv> cards = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isHeader = true;
            for (Row row : sheet) {
                if (isHeader) { // Saltar encabezado
                    isHeader = false;
                    continue;
                }
                ApiCollectionCardCsv card = new ApiCollectionCardCsv();
                card.setNumber(Utils.getCellInteger(row, 0));
                card.setCardName(Utils.getCellString(row, 1));
                cards.add(card);
            }
            if (binderId != null && binderId != 0) {
                Optional<CollectionBinder> binderOptional = collectionBinderRepository.findByCollectionIdAndId(collection.getId(), binderId);
                if (binderOptional.isPresent()) {
                    cards.forEach(card -> card.setBinder(binderOptional.get().getName()));
                } else {
                    apiCollectionImport.setSuccess(false);
                    apiCollectionImport.setErrors(List.of("Binder does not exist."));
                    return apiCollectionImport;
                }
            }
            List<String> validationError = validateCards(cards);
            if (!validationError.isEmpty()) {
                apiCollectionImport.setSuccess(false);
                apiCollectionImport.setErrors(validationError);
                return apiCollectionImport;
            }
            importCards(collection, cards);
        } catch (Exception e) {
            log.warn("Error importing collection from VDB file: {}", e.getMessage(), e);
            apiCollectionImport.setErrors(List.of("Error reading file: " + e.getMessage()));
            apiCollectionImport.setSuccess(false);
        }
        return apiCollectionImport;
    }

    public ApiCollectionImport importCardsLackey(Collection collection, MultipartFile file, Integer binderId) {
        ApiCollectionImport apiCollectionImport = new ApiCollectionImport();
        apiCollectionImport.setSuccess(true);
        List<ApiCollectionCardCsv> cards = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length < 2) {
                    continue;
                }
                ApiCollectionCardCsv card = new ApiCollectionCardCsv();
                card.setNumber(Integer.parseInt(parts[0].trim()));
                card.setCardName(parts[1].trim());
                cards.add(card);
            }
            if (binderId != null && binderId != 0) {
                Optional<CollectionBinder> binderOptional = collectionBinderRepository.findByCollectionIdAndId(collection.getId(), binderId);
                if (binderOptional.isPresent()) {
                    cards.forEach(card -> card.setBinder(binderOptional.get().getName()));
                } else {
                    apiCollectionImport.setSuccess(false);
                    apiCollectionImport.setErrors(List.of("Binder does not exist."));
                    return apiCollectionImport;
                }
            }
            List<String> validationError = validateCards(cards);
            if (!validationError.isEmpty()) {
                apiCollectionImport.setSuccess(false);
                apiCollectionImport.setErrors(validationError);
                return apiCollectionImport;
            }
            importCards(collection, cards);
        } catch (Exception e) {
            log.warn("Error importing collection from VDB file: {}", e.getMessage(), e);
            apiCollectionImport.setErrors(List.of("Error reading file: " + e.getMessage()));
            apiCollectionImport.setSuccess(false);
        }
        return apiCollectionImport;
    }


    private void importCards(Collection collection, List<ApiCollectionCardCsv> cards) {
        List<CollectionCard> collectionCards = collectionCardRepository.findByCollectionId(collection.getId());
        Map<String, Integer> binderMap = new HashMap<>();
        for (ApiCollectionCardCsv card : cards) {
            if (isNotBlank(card.getBinder()) && !binderMap.containsKey(card.getBinder())) {
                Optional<CollectionBinder> binderOptional = collectionBinderRepository.findByCollectionIdAndNameIgnoreCase(collection.getId(), card.getBinder());
                if (binderOptional.isPresent()) {
                    CollectionBinder binder = binderOptional.get();
                    binderMap.put(card.getBinder(), binder.getId());
                } else {
                    CollectionBinder newBinder = new CollectionBinder();
                    newBinder.setCollectionId(collection.getId());
                    newBinder.setName(card.getBinder());
                    newBinder.setPublicVisibility(false);
                    newBinder = collectionBinderRepository.save(newBinder);
                    binderMap.put(card.getBinder(), newBinder.getId());
                }
            }
            CollectionCard collectionCard = this.apiCollectionMapper.mapCsvToEntity(card);
            // Normalize card attributes
            if (isBlank(collectionCard.getSet())) {
                collectionCard.setSet(null);
            }
            if (isBlank(collectionCard.getCondition())) {
                collectionCard.setCondition(null);
            }
            if (isBlank(collectionCard.getLanguage())) {
                collectionCard.setLanguage("EN"); // Default to English if no language is specified
            }
            if (isBlank(collectionCard.getNotes())) {
                collectionCard.setNotes(null);
            }
            collectionCard.setCollectionId(collection.getId());
            collectionCard.setBinderId(binderMap.getOrDefault(card.getBinder(), null));
            collectionCard.setCardId(getCardId(card));
            Optional<CollectionCard> existingCardOptional = collectionCards.stream()
                    .filter(c -> Objects.equals(c.getCardId(), collectionCard.getCardId())
                            && StringUtils.equalsIgnoreCase(c.getSet(), collectionCard.getSet())
                            && StringUtils.equalsIgnoreCase(c.getCondition(), collectionCard.getCondition())
                            && StringUtils.equalsIgnoreCase(c.getLanguage(), collectionCard.getLanguage())
                            && Objects.equals(c.getBinderId(), collectionCard.getBinderId())
                            && StringUtils.equalsIgnoreCase(c.getNotes(), collectionCard.getNotes()))
                    .findFirst();
            if (existingCardOptional.isPresent()) {
                // If a card with the same attributes already exists, increase it instead of creating a new one
                CollectionCard existingCard = existingCardOptional.get();
                existingCard.setNumber(existingCard.getNumber() + card.getNumber());
                collectionCardRepository.save(existingCard);
            } else {
                collectionCardRepository.save(collectionCard);
            }
        }
    }


    private List<String> validateCards(List<ApiCollectionCardCsv> cards) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            ApiCollectionCardCsv card = cards.get(i);
            if (card.getCardName() == null || card.getCardName().isEmpty()) {
                errors.add("Error in line " + i + ": Missing card name");
            }
            if (card.getCardName() != null && getCardId(card) == null) {
                errors.add(ERROR_IN_CARD + card.getCardName() + ": Unknown card '" + card.getCardName() + "'");
            }
            if (card.getNumber() == null || card.getNumber() <= 0) {
                errors.add(ERROR_IN_CARD + card.getCardName() + ": Invalid quantity");
            }
            if (card.getSet() != null && !card.getSet().isEmpty() && setCache.get(card.getSet()) == null) {
                errors.add(ERROR_IN_CARD + card.getCardName() + ": unknown set '" + card.getSet() + "'");
            }
            if (card.getLanguage() != null && !card.getLanguage().isEmpty() && !ALLOWED_LANGUAGES.contains(StringUtils.lowerCase(card.getLanguage()))) {
                errors.add(ERROR_IN_CARD + card.getCardName() + ": unknown language '" + card.getLanguage() + "'");
            }
        }
        return errors;
    }

    private Integer getCardId(ApiCollectionCardCsv card) {
        try (ResultSet<Library> library = libraryCache.selectAll(card.getCardName(), null)) {
            if (library.isEmpty() || library.stream().noneMatch(l -> compareExactName(card.getCardName(), l.getName()))) {
                String cryptName = StringUtils.substringBefore(card.getCardName(), " (ADV)");
                boolean isAdv = StringUtils.containsIgnoreCase(card.getCardName(), "(ADV)");
                try (ResultSet<Crypt> crypt = cryptCache.selectAll(cryptName, null)) {
                    if (!crypt.isEmpty() && crypt.stream().anyMatch(c -> compareExactName(cryptName, c.getName()))) {
                        return crypt.stream()
                                .filter(c -> c.isAdv() == isAdv)
                                .findFirst().orElseThrow().getId();
                    }
                }
            } else {
                return library.stream().findFirst().orElseThrow().getId();
            }
        }
        log.warn("Card not found in cache: {}", card.getCardName());
        return null;
    }

    private static boolean compareExactName(String cardNameA, String cardNameB) {
        return Utils.normalizeLackeyName(cardNameA).equalsIgnoreCase(Utils.normalizeLackeyName(cardNameB));
    }
}
