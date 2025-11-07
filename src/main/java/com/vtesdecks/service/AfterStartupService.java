package com.vtesdecks.service;

import com.google.common.base.Splitter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.ProxyCardOptionCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.csv.entity.CryptCsv;
import com.vtesdecks.csv.entity.CryptI18nCsv;
import com.vtesdecks.csv.entity.LibraryCsv;
import com.vtesdecks.csv.entity.LibraryI18nCsv;
import com.vtesdecks.csv.entity.SetCsv;
import com.vtesdecks.csv.mapper.CryptCsvMapper;
import com.vtesdecks.csv.mapper.CryptI18nCsvMapper;
import com.vtesdecks.csv.mapper.LibraryCsvMapper;
import com.vtesdecks.csv.mapper.LibraryI18nCsvMapper;
import com.vtesdecks.csv.mapper.SetCsvMapper;
import com.vtesdecks.jpa.entity.CryptEntity;
import com.vtesdecks.jpa.entity.CryptI18nEntity;
import com.vtesdecks.jpa.entity.LibraryEntity;
import com.vtesdecks.jpa.entity.LibraryI18nEntity;
import com.vtesdecks.jpa.entity.SetEntity;
import com.vtesdecks.jpa.repositories.CryptI18nRepository;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryI18nRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.jpa.repositories.LoadHistoryRepository;
import com.vtesdecks.jpa.repositories.SetRepository;
import com.vtesdecks.model.csv.SetCard;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterStartupService {
    private static final String BASE_PATH = "D://Trabajo//Git//vtesdecks-statics//public//img//cards//";
    private static final char CSV_SEPARATOR = ',';
    private static final String SETS_FILE = "data/vtessets.csv";
    private static final String CUSTOM_SETS_FILE = "data/vtessets_custom.csv";
    private static final String CRYPT_FILE = "data/vtescrypt.csv";
    private static final String LIBRARY_FILE = "data/vteslib.csv";
    private static final String CRYPT_I18N_FILE = "data/vtescrypt.i18n.csv";
    private static final String LIBRARY_I18N_FILE = "data/vteslib.i18n.csv";
    private static final String FULL_ART_CARDS_FILE = "data/fullarts.csv";
    private static final String BCP_BUSSINESS_CARDS_FILE = "data/bcp_business_cards.csv";
    public static final String PROMO = "Promo";

    private Set<Integer> fullArtCards;
    private Set<Integer> bcpBusinessCards;

    @Autowired
    private CryptRepository cryptRepository;
    @Autowired
    private LibraryRepository libraryRepository;
    @Autowired
    private CryptI18nRepository cryptI18nRepository;
    @Autowired
    private LibraryI18nRepository libraryI18nRepository;
    @Autowired
    private SetRepository setRepository;
    @Autowired
    private DeckIndex deckIndex;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private SetCache setCache;
    @Autowired
    private ProxyCardOptionCache proxyCardOptionCache;
    @Autowired
    private LoadHistoryRepository loadHistoryRepository;
    @Autowired
    private LoadHistoryService loadHistoryService;

    @Transactional
    public void executeAfterStartupTasks() {
        boolean changed = false;
        //Startup actions
        if (!loadHistoryService.isLoaded(SETS_FILE)) {
            sets();
            changed = true;
        }
        if (!loadHistoryService.isLoaded(CUSTOM_SETS_FILE)) {
            customSets();
            changed = true;
        }
        fullArtCards = readSetCardList(FULL_ART_CARDS_FILE);
        bcpBusinessCards = readSetCardList(BCP_BUSSINESS_CARDS_FILE);
        if (!loadHistoryService.isLoaded(CRYPT_FILE)) {
            crypt();
            changed = true;
        }
        if (!loadHistoryService.isLoaded(LIBRARY_FILE)) {
            library();
            changed = true;
        }
        if (!loadHistoryService.isLoaded(CRYPT_I18N_FILE)) {
            cryptI18n();
            changed = true;
        }
        if (!loadHistoryService.isLoaded(LIBRARY_I18N_FILE)) {
            libraryI18n();
            changed = true;
        }
        if (changed) {
            setCache.refreshIndex();
            cryptCache.refreshIndex();
            libraryCache.refreshIndex();
            deckIndex.refreshIndex();
        }
        proxyCardOptionCache.refreshIndex();
        log.info("Finish background tasks...");
    }


    private void crypt() {
        log.info("Starting Crypt load...");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CRYPT_FILE);
        boolean loaded = false;
        try (Reader targetReader = new InputStreamReader(inputStream)) {
            List<CryptCsv> cryptCsv = parse(targetReader, CryptCsv.class);
            List<CryptEntity> newEntities = CryptCsvMapper.INSTANCE.toEntities(cryptCsv);
            List<CryptEntity> currentEntities = cryptRepository.findAll();
            for (CryptEntity crypt : newEntities) {
                try {
                    fixName(crypt);
                    crypt.setClan(mapClan(crypt.getClan()));
                    crypt.setSet(mapSets(crypt.getId(), crypt.getSet()));
                    CryptEntity actual = cryptRepository.findById(crypt.getId()).orElse(null);
                    if (actual == null) {
                        cryptRepository.save(crypt);
                        log.debug("Insert crypt {}", crypt.getId());
                    } else if (!actual.equals(crypt)) {
                        log.debug("Update crypt {}", crypt.getId());
                        cryptRepository.save(crypt);
                    }
                    //cropImage(crypt.getId());
                    currentEntities.removeIf(c -> c.getId().equals(crypt.getId()));
                } catch (Exception e) {
                    log.error("Unable to load crypt {}", crypt, e);
                }
            }
            log.info("Crypt to be deleted {}", currentEntities);
            currentEntities.forEach(crypt -> cryptRepository.delete(crypt));
            loaded = true;
        } catch (IOException e) {
            log.error("Unable to parse", e);
        } finally {
            stopWatch.stop();
            if (loaded) {
                loadHistoryService.updateLoadedHistory(stopWatch, CRYPT_FILE);
            }
            log.info("Crypt load finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());
        }
    }


    private void fixName(CryptEntity crypt) {
        switch (crypt.getId()) {
            case 201617:
                crypt.setName("Gilbert Duane (G6)");
                break;
            case 201591:
                crypt.setName("Hesha Ruhadze (G6)");
                break;
            case 201596:
                crypt.setName("Kalinda (G6)");
                break;
            case 201613:
                crypt.setName("Theo Bell (G6)");
                break;
            case 201619:
                crypt.setName("Victoria Ash (G7)");
                break;
            case 201627:
                crypt.setName("Evan Klein (G6)");
                break;
            case 201645:
                crypt.setName("Mithras (G6)");
                break;
            case 201636:
                crypt.setClan("Assamite");
                break;
            case 201654:
                crypt.setName("Tegyrius, Vizier (G6)");
                break;
            case 201696:
                crypt.setName("François Villon (G6)");
                break;
            case 201693:
                crypt.setName("Annabelle Triabell (G6)");
                break;
            case 201647:
                crypt.setName("Queen Anne (G6)");
                break;
            case 201705:
                crypt.setName("Nikolaus Vermeulen (G6)");
                break;
            case 201700:
                crypt.setName("Lucinde, Alastor (G7)");
                break;
            case 201695:
                crypt.setName("Dónal O'Connor (G6)");
                break;
        }
    }

    private void library() {
        log.info("Starting Library load...");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(LIBRARY_FILE);
        boolean loaded = false;
        try (Reader targetReader = new InputStreamReader(inputStream)) {
            List<LibraryCsv> libraryCsv = parse(targetReader, LibraryCsv.class);
            List<LibraryEntity> newEntities = LibraryCsvMapper.INSTANCE.toEntities(libraryCsv);
            List<LibraryEntity> currentEntities = libraryRepository.findAll();
            for (LibraryEntity library : newEntities) {
                try {
                    library.setClan(mapClan(library.getClan()));
                    library.setSet(mapSets(library.getId(), library.getSet()));
                    LibraryEntity actual = libraryRepository.findById(library.getId()).orElse(null);
                    if (actual == null) {
                        log.debug("Insert library {}", library.getId());
                        libraryRepository.save(library);
                    } else if (!actual.equals(library)) {
                        log.debug("Update library {}", library.getId());
                        libraryRepository.save(library);
                    }
                    //cropImage(library.getId());
                    currentEntities.removeIf(l -> l.getId().equals(library.getId()));
                } catch (Exception e) {
                    log.error("Unable to load library {}", library, e);
                }
            }
            log.info("Library to be deleted {}", currentEntities);
            currentEntities.forEach(library -> libraryRepository.delete(library));
            loaded = true;
        } catch (IOException e) {
            log.error("Unable to parse", e);
        } finally {
            stopWatch.stop();
            if (loaded) {
                loadHistoryService.updateLoadedHistory(stopWatch, LIBRARY_FILE);
            }
            log.info("Library load finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());
        }
    }


    private String mapClan(String rawClans) {
        return Splitter.on('/')
                .trimResults()
                .omitEmptyStrings()
                .splitToStream(rawClans)
                .map(rawClan -> {
                    if (rawClan.equalsIgnoreCase("Follower of Set")) {
                        return "Ministry";
                    } else if (rawClan.equalsIgnoreCase("Assamite")) {
                        return "Banu Haqim";
                    }
                    return rawClan;
                })
                .collect(Collectors.joining("/"));
    }

    private String mapSets(Integer id, String rawSet) {
        List<String> sets = new ArrayList<>(Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToStream(rawSet)
                .map(set -> {
                    if (set.startsWith(PROMO + "-")) {
                        if (set.length() < 7) {
                            return PROMO;
                        } else {
                            return PROMO + ":" + set.substring(6);
                        }
                    }
                    return set;
                }).toList());
        if (fullArtCards != null && fullArtCards.contains(id)) {
            sets.add("PFA:1");
        }
        if (bcpBusinessCards != null && bcpBusinessCards.contains(id)) {
            sets.add("BCPBC:1");
        }
        convertSubSetToSet(sets, "V5", "PH", "V5H");
        convertSubSetToSet(sets, "V5", "PL", "V5L");
        return String.join(",", sets);
    }

    private void convertSubSetToSet(List<String> sets, String oldSet, String subSet, String newSet) {
        List<String> newSets = new ArrayList<>();
        for (Iterator<String> it = sets.iterator(); it.hasNext(); ) {
            String set = it.next();
            List<String> setInfo = Splitter.on(':').splitToList(set);
            if (setInfo.size() != 2) {
                continue;
            }
            if (setInfo.getFirst().equals(oldSet) && setInfo.getLast().contains(subSet)) {
                it.remove();
                List<String> subSets = Splitter.on('/').splitToList(setInfo.getLast());
                Optional<String> subSetOpt = subSets.stream().filter(s -> s.startsWith(subSet)).findFirst();
                if (subSetOpt.isPresent()) {
                    String subSetInfo = subSetOpt.get().substring(subSet.length());
                    newSets.add(newSet + ':' + subSetInfo);
                }
                if (subSets.size() > 1) {
                    List<String> otherSubSet = subSets.stream().filter(s -> !s.startsWith(subSet)).toList();
                    newSets.add(oldSet + ':' + String.join("/", otherSubSet));
                }
            }
        }
        sets.addAll(newSets);
    }

    private void sets() {
        log.info("Starting Sets load...");
        boolean loaded = false;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(SETS_FILE);
        try (Reader targetReader = new InputStreamReader(inputStream)) {
            List<SetCsv> setsCsv = parse(targetReader, SetCsv.class);
            List<SetEntity> newEntities = SetCsvMapper.INSTANCE.toEntities(setsCsv);
            for (SetEntity set : newEntities) {
                try {
                    if (set.getAbbrev().startsWith(PROMO + "-")) {
                        log.debug("Ignore promo set {}", set.getId());
                    } else {
                        upsertSet(set);
                    }
                } catch (Exception e) {
                    log.error("Unable to load set {}", newEntities, e);
                }
            }
            loaded = true;
        } catch (IOException e) {
            log.error("Unable to parse", e);

        } finally {
            stopWatch.stop();
            if (loaded) {
                loadHistoryService.updateLoadedHistory(stopWatch, SETS_FILE);
            }
            log.info("Set load finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());
        }
    }

    private void customSets() {
        log.info("Starting Custom Sets load...");
        boolean loaded = false;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CUSTOM_SETS_FILE);
        try (Reader targetReader = new InputStreamReader(inputStream)) {
            List<SetCsv> setsCsv = parse(targetReader, SetCsv.class);
            List<SetEntity> newEntities = SetCsvMapper.INSTANCE.toEntities(setsCsv);
            for (SetEntity set : newEntities) {
                try {
                    upsertSet(set);
                } catch (Exception e) {
                    log.error("Unable to load set {}", newEntities, e);
                }
            }
            loaded = true;
        } catch (IOException e) {
            log.error("Unable to parse", e);

        } finally {
            stopWatch.stop();
            if (loaded) {
                loadHistoryService.updateLoadedHistory(stopWatch, CUSTOM_SETS_FILE);
            }
            log.info("Custom sets load finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());
        }
    }

    public Set<Integer> readSetCardList(String fileName) {
        Set<Integer> cards = new HashSet<>();
        try (Reader targetReader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(fileName))) {
            List<SetCard> setCardList = parse(targetReader, SetCard.class);
            for (SetCard setCard : setCardList) {
                cards.add(setCard.getId());
            }
        } catch (IOException e) {
            log.error("Unable to parse", e);
        }
        return cards;
    }


    private void upsertSet(SetEntity set) {
        SetEntity actual = setRepository.findById(set.getId()).orElse(null);
        if (actual == null) {
            log.debug("Insert set {}", set.getId());
            setRepository.save(set);
            setCache.refreshIndex(set);
        } else if (!actual.equals(set)) {
            log.debug("Update set {}", set.getId());
            setRepository.save(set);
            setCache.refreshIndex(set);
        }
    }

    private void libraryI18n() {
        log.info("Starting Library i18n load...");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(LIBRARY_I18N_FILE);
        boolean loaded = false;
        try (Reader targetReader = new InputStreamReader(inputStream)) {
            List<LibraryI18nCsv> libraryI18nsCsv = parse(targetReader, LibraryI18nCsv.class);
            List<LibraryI18nEntity> newEntities = LibraryI18nCsvMapper.INSTANCE.toEntities(libraryI18nsCsv);
            List<LibraryI18nEntity> currentEntities = libraryI18nRepository.findAll();
            for (LibraryI18nEntity libraryI18n : newEntities) {
                try {
                    LibraryI18nEntity actual = currentEntities.stream().
                            filter(e -> e.getId().equals(libraryI18n.getId()) && e.getId().getLocale().equals(libraryI18n.getId().getLocale()))
                            .findFirst().orElse(null);
                    if (libraryI18n.getImage().isEmpty()) {
                        libraryI18n.setImage(null);
                    }
                    if (actual == null) {
                        log.debug("Insert library i18n {}", libraryI18n.getId());
                        libraryI18nRepository.save(libraryI18n);
                    } else if (!actual.equals(libraryI18n)) {
                        log.debug("Update library i18n {}", libraryI18n.getId());
                        libraryI18nRepository.save(libraryI18n);
                    }
                    currentEntities.remove(actual);
                } catch (Exception e) {
                    log.error("Unable to load library i18n {}", libraryI18n, e);
                }
            }
            currentEntities.forEach(e -> libraryI18nRepository.delete(e));
            loaded = true;
        } catch (IOException e) {
            log.error("Unable to parse", e);
        } finally {
            stopWatch.stop();
            if (loaded) {
                loadHistoryService.updateLoadedHistory(stopWatch, LIBRARY_I18N_FILE);
            }
            log.info("Library i18n load finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());
        }
    }

    private void cryptI18n() {
        log.info("Starting Crypt i18n load...");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CRYPT_I18N_FILE);
        boolean loaded = false;
        try (Reader targetReader = new InputStreamReader(inputStream)) {
            List<CryptI18nCsv> cryptI18nsCsv = parse(targetReader, CryptI18nCsv.class);
            List<CryptI18nEntity> newEntities = CryptI18nCsvMapper.INSTANCE.toEntities(cryptI18nsCsv);
            List<CryptI18nEntity> currentEntities = cryptI18nRepository.findAll();
            for (CryptI18nEntity cryptI18n : newEntities) {
                try {
                    CryptI18nEntity actual = currentEntities.stream().
                            filter(e -> e.getId().equals(cryptI18n.getId()) && e.getId().getLocale().equals(cryptI18n.getId().getLocale()))
                            .findFirst().orElse(null);
                    if (cryptI18n.getImage().isEmpty()) {
                        cryptI18n.setImage(null);
                    }
                    if (actual == null) {
                        log.debug("Insert crypt i18n {}", cryptI18n.getId());
                        cryptI18nRepository.save(cryptI18n);
                    } else if (!actual.equals(cryptI18n)) {
                        log.debug("Update crypt i18n {}", cryptI18n.getId());
                        cryptI18nRepository.save(cryptI18n);
                    }
                    currentEntities.remove(actual);
                } catch (Exception e) {
                    log.error("Unable to load crypt i18n {}", cryptI18n, e);
                }
            }
            currentEntities.forEach(e -> cryptI18nRepository.delete(e));
            loaded = true;
        } catch (IOException e) {
            log.error("Unable to parse", e);
        } finally {
            stopWatch.stop();
            if (loaded) {
                loadHistoryService.updateLoadedHistory(stopWatch, CRYPT_I18N_FILE);
            }
            log.info("Crypt i18n load finished in {} ms", stopWatch.lastTaskInfo().getTimeMillis());
        }

    }


    private void cropImage(Integer id) throws IOException {
        //Crop image for background
        try {
            BufferedImage originalImage = ImageIO.read(new File(BASE_PATH + id + ".jpg"));
            BufferedImage dest;
            if (VtesUtils.isLibrary(id)) {
                dest = originalImage.getSubimage(75, 58, 259, 225);
            } else {
                dest = originalImage.getSubimage(75, 55, 260, 333);
            }
            ImageIO.write(dest, "jpg", new File(BASE_PATH + "crop/" + id + ".jpg"));
        } catch (Exception e) {
            log.error("Unable to crop {}", id, e);
        }
    }


    private <T> List<T> parse(Reader targetReader, Class<T> type) {
        CsvToBean<T> build = new CsvToBeanBuilder<T>(targetReader)
                .withSeparator(CSV_SEPARATOR)
                .withType(type)
                .withThrowExceptions(true)
                .build();
        List<CsvException> exceptions = build.getCapturedExceptions();
        for (CsvException csvException : exceptions) {
            log.warn("Error reading csv", csvException);
        }
        return build.parse();
    }
}
