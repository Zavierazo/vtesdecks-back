package com.vtesdecks.configuration;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.ProxyCardOptionCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.db.CryptMapper;
import com.vtesdecks.db.LibraryMapper;
import com.vtesdecks.db.LoadHistoryMapper;
import com.vtesdecks.db.SetMapper;
import com.vtesdecks.db.model.DbBase;
import com.vtesdecks.db.model.DbCrypt;
import com.vtesdecks.db.model.DbLibrary;
import com.vtesdecks.db.model.DbLoadHistory;
import com.vtesdecks.db.model.DbSet;
import com.vtesdecks.util.Utils;
import com.vtesdecks.util.VtesUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.util.StopWatch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Configuration
@Slf4j
@DependsOn("flywayInitializer")
public class AppStartUpActions implements InitializingBean {
    private static final String BASE_PATH = "D://Trabajo//Git//vtesdecks-front//src//assets//img//cards//";
    private static final char CSV_SEPARATOR = ',';
    private static final String SETS_FILE = "data/vtessets.csv";
    private static final String CRYPT_FILE = "data/vtescrypt.csv";
    private static final String LIBRARY_FILE = "data/vteslib.csv";

    @Autowired
    private CryptMapper cryptMapper;
    @Autowired
    private LibraryMapper libraryMapper;
    @Autowired
    private SetMapper setMapper;
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
    private LoadHistoryMapper loadHistoryMapper;

    @Override
    public void afterPropertiesSet() throws Exception {
        // Start async tasks thread
        ExecutorService executor = null;
        try {
            log.info("Starting concurrent indexing...");
            executor = Executors.newFixedThreadPool(3);
            executor.execute(() -> cryptCache.refreshIndex());
            executor.execute(() -> libraryCache.refreshIndex());
            executor.execute(() -> setCache.refreshIndex());
        } finally {
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            }
            log.info("Finish concurrent indexing...");
        }
        log.info("Starting serial indexing...");
        deckIndex.refreshIndex();
        log.info("Finish serial indexing...");
        StartUpActionsAsync startActions = new StartUpActionsAsync();
        log.info("Starting background tasks...");
        startActions.start();
    }

    @AllArgsConstructor
    private class StartUpActionsAsync extends Thread {

        @Override
        public void run() {
            boolean changed = false;
            //Startup actions
            if (!isLoaded(SETS_FILE)) {
                sets();
                changed = true;
                setCache.refreshIndex();
            }
            if (!isLoaded(CRYPT_FILE)) {
                crypt();
                changed = true;
                cryptCache.refreshIndex();
            }
            if (!isLoaded(LIBRARY_FILE)) {
                library();
                changed = true;
                libraryCache.refreshIndex();
            }
            if (changed) {
                deckIndex.refreshIndex();
            }

            proxyCardOptionCache.refreshIndex();

            log.info("Finish background tasks...");
        }

        private boolean isLoaded(String filePath) {
            DbLoadHistory loadHistory = loadHistoryMapper.selectById(filePath);
            if (loadHistory != null) {
                String md5 = Utils.getMD5(getClass().getClassLoader(), filePath);
                if (loadHistory.getChecksum().equals(md5)) {
                    log.info("File '{}' already loaded", filePath);
                    return true;
                }
            }
            return false;
        }

        private void updateLoadedHistory(StopWatch stopWatch, String filePath) {
            DbLoadHistory loadHistory = loadHistoryMapper.selectById(filePath);
            if (loadHistory == null) {
                loadHistory = new DbLoadHistory();
                loadHistory.setScript(filePath);
                loadHistory.setChecksum(Utils.getMD5(getClass().getClassLoader(), filePath));
                loadHistory.setExecutionTime(stopWatch.getLastTaskTimeMillis());
                loadHistoryMapper.insert(loadHistory);
            } else {
                loadHistory.setChecksum(Utils.getMD5(getClass().getClassLoader(), filePath));
                loadHistory.setExecutionTime(stopWatch.getLastTaskTimeMillis());
                loadHistoryMapper.update(loadHistory);
            }
        }


        private void crypt() {
            log.info("Starting Crypt load...");
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CRYPT_FILE);
            boolean loaded = false;
            try (Reader targetReader = new InputStreamReader(inputStream)) {
                List<DbCrypt> crypts = parse(targetReader, DbCrypt.class);
                List<Integer> keys = cryptMapper.selectAll().stream().map(DbCrypt::getId).collect(Collectors.toList());
                for (DbCrypt crypt : crypts) {
                    try {
                        fixName(crypt);
                        DbCrypt actual = cryptMapper.selectById(crypt.getId());
                        if (actual == null) {
                            cryptMapper.insert(crypt);
                            log.debug("Insert crypt {}", crypt.getId());
                        } else if (!actual.equals(crypt)) {
                            cryptMapper.update(crypt);
                            log.debug("Update crypt {}", crypt.getId());
                        }
                        //downloadImage(crypt.getId(), crypt.getSet());
                        //cropImage(crypt.getId());
                        keys.remove(crypt.getId());
                    } catch (Exception e) {
                        log.error("Unable to load library {}", crypt, e);
                    }
                }
                log.info("Crypt to be deleted {}", keys);
                keys.forEach(key -> cryptMapper.delete(key));
                cryptCache.refreshIndex();
                loaded = true;
            } catch (IOException e) {
                log.error("Unable to parse", e);
            } finally {
                stopWatch.stop();
                if (loaded) {
                    updateLoadedHistory(stopWatch, CRYPT_FILE);
                }
                log.info("Crypt load finished in {} ms", stopWatch.getLastTaskTimeMillis());
            }
        }

        private void fixName(DbCrypt crypt) {
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
            }
        }

        private void library() {
            log.info("Starting Library load...");
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(LIBRARY_FILE);
            boolean loaded = false;
            try (Reader targetReader = new InputStreamReader(inputStream)) {
                List<DbLibrary> libraries = parse(targetReader, DbLibrary.class);
                List<Integer> keys = libraryMapper.selectAll().stream().map(DbLibrary::getId).collect(Collectors.toList());
                for (DbLibrary library : libraries) {
                    try {
                        DbLibrary actual = libraryMapper.selectById(library.getId());
                        if (actual == null) {
                            log.debug("Insert library {}", library.getId());
                            libraryMapper.insert(library);
                        } else if (!actual.equals(library)) {
                            libraryMapper.update(library);
                            log.debug("Update library {}", library.getId());
                        }
                        //downloadImage(library.getId(), library.getSet());
                        //cropImage(library.getId());
                        keys.remove(library.getId());
                    } catch (Exception e) {
                        log.error("Unable to load library {}", library, e);
                    }
                }
                log.info("Library to be deleted {}", keys);
                keys.forEach(key -> libraryMapper.delete(key));
                libraryCache.refreshIndex();
                loaded = true;
            } catch (IOException e) {
                log.error("Unable to parse", e);
            } finally {
                stopWatch.stop();
                if (loaded) {
                    updateLoadedHistory(stopWatch, LIBRARY_FILE);
                }
                log.info("Library load finished in {} ms", stopWatch.getLastTaskTimeMillis());
            }
        }

        private void sets() {
            log.info("Starting Sets load...");
            boolean loaded = false;
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(SETS_FILE);
            try (Reader targetReader = new InputStreamReader(inputStream)) {
                List<DbSet> sets = parse(targetReader, DbSet.class);
                for (DbSet set : sets) {
                    try {
                        DbSet actual = setMapper.selectById(set.getId());
                        if (actual == null) {
                            log.debug("Insert set {}", set.getId());
                            setMapper.insert(set);
                            setCache.refreshIndex(set);
                        } else if (!actual.equals(set)) {
                            log.debug("Update set {}", set.getId());
                            setMapper.update(set);
                            setCache.refreshIndex(set);
                        }
                    } catch (Exception e) {
                        log.error("Unable to load library {}", sets, e);
                    }
                }
                loaded = true;
            } catch (IOException e) {
                log.error("Unable to parse", e);

            } finally {
                stopWatch.stop();
                if (loaded) {
                    updateLoadedHistory(stopWatch, SETS_FILE);
                }
                log.info("Library load finished in {} ms", stopWatch.getLastTaskTimeMillis());
            }
        }
    }

    private void downloadImage(Integer id, String rawSet) throws IOException {
        String set = rawSet;
        if (rawSet.indexOf(":") > 0) {
            set = rawSet.substring(0, rawSet.indexOf(":"));
        }
        if (set.indexOf(",") > 0) {
            set = set.substring(0, rawSet.indexOf(","));
        }
        set = StringUtils.lowerCase(set);
        if (set.contains("promo")) {
            set = "promo";
        }
        try {
            URL website = new URL("https://statics.bloodlibrary.info/img/sets/" + set + "/" + id + ".jpg");
            try (ReadableByteChannel rbc = Channels.newChannel(website.openStream())) {
                try (FileOutputStream fos = new FileOutputStream(BASE_PATH + id + ".jpg")) {
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                }
            }
        } catch (Exception e) {
            log.error("Unable to download {}", id, e);
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

    private <T extends DbBase> List<T> parse(Reader targetReader, Class<T> type) {
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
