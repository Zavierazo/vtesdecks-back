package com.vtesdecks.scheduler;

import com.vtesdecks.db.CryptI18nMapper;
import com.vtesdecks.db.CryptMapper;
import com.vtesdecks.db.LibraryI18nMapper;
import com.vtesdecks.db.LibraryMapper;
import com.vtesdecks.db.model.DbCrypt;
import com.vtesdecks.db.model.DbCryptI18n;
import com.vtesdecks.db.model.DbLibrary;
import com.vtesdecks.db.model.DbLibraryI18n;
import com.vtesdecks.integration.KRCGClient;
import com.vtesdecks.model.krcg.Card;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class I18nScheduler {

    private final CryptMapper cryptMapper;
    private final CryptI18nMapper cryptI18nMapper;
    private final LibraryMapper libraryMapper;
    private final LibraryI18nMapper libraryI18nMapper;
    private final KRCGClient krcgClient;

    @Scheduled(cron = "${jobs.updateI18nCrypt:2 0 0 * * *}")
    public void updateI18nCrypt() {
        log.info("Starting update i18n crypt...");
        long start = System.currentTimeMillis();
        for (DbCrypt crypt : cryptMapper.selectAll()) {
            try {
                Card card = krcgClient.getCard(crypt.getId());
                if (card != null && card.getI18n() != null) {
                    List<DbCryptI18n> currentI18n = cryptI18nMapper.selectById(crypt.getId());
                    for (Map.Entry<String, Card> entry : card.getI18n().entrySet()) {
                        if (entry.getValue().getName() == null) {
                            continue;
                        }
                        DbCryptI18n dbCryptI18n = currentI18n.stream()
                                .filter(db -> db.getLocale().equals(entry.getKey()))
                                .findFirst().orElse(null);
                        if (dbCryptI18n == null) {
                            dbCryptI18n = new DbCryptI18n();
                            dbCryptI18n.setId(crypt.getId());
                            dbCryptI18n.setLocale(entry.getKey());
                            dbCryptI18n.setName(entry.getValue().getName());
                            dbCryptI18n.setText(entry.getValue().getText());
                            dbCryptI18n.setImage(entry.getValue().getUrl());
                            cryptI18nMapper.insert(dbCryptI18n);
                        } else {
                            dbCryptI18n.setName(entry.getValue().getName());
                            dbCryptI18n.setText(entry.getValue().getText());
                            dbCryptI18n.setImage(entry.getValue().getUrl());
                            cryptI18nMapper.update(dbCryptI18n);
                        }
                        currentI18n.remove(dbCryptI18n);
                    }
                    for (DbCryptI18n dbCryptI18n : currentI18n) {
                        cryptI18nMapper.deleteByIdAndLocale(dbCryptI18n.getId(), dbCryptI18n.getLocale());
                    }
                }
            } catch (Exception e) {
                log.error("Unable to update i18n crypt {}", crypt.getId(), e);
            }
        }
        log.info("Finished to update i18n crypt in {} ms", System.currentTimeMillis() - start);

    }

    @Scheduled(cron = "${jobs.updateI18nCrypt:3 0 0 * * *}")
    public void updateI18nLibrary() {
        log.info("Starting update i18n library...");
        long start = System.currentTimeMillis();
        for (DbLibrary library : libraryMapper.selectAll()) {
            try {
                Card card = krcgClient.getCard(library.getId());
                if (card != null && card.getI18n() != null) {
                    List<DbLibraryI18n> currentI18n = libraryI18nMapper.selectById(library.getId());
                    for (Map.Entry<String, Card> entry : card.getI18n().entrySet()) {
                        if (entry.getValue().getName() == null) {
                            continue;
                        }
                        DbLibraryI18n dbLibraryI18n = currentI18n.stream()
                                .filter(db -> db.getLocale().equals(entry.getKey()))
                                .findFirst().orElse(null);
                        if (dbLibraryI18n == null) {
                            dbLibraryI18n = new DbLibraryI18n();
                            dbLibraryI18n.setId(library.getId());
                            dbLibraryI18n.setLocale(entry.getKey());
                            dbLibraryI18n.setName(entry.getValue().getName());
                            dbLibraryI18n.setText(entry.getValue().getText());
                            dbLibraryI18n.setImage(entry.getValue().getUrl());
                            libraryI18nMapper.insert(dbLibraryI18n);
                        } else {
                            dbLibraryI18n.setName(entry.getValue().getName());
                            dbLibraryI18n.setText(entry.getValue().getText());
                            dbLibraryI18n.setImage(entry.getValue().getUrl());
                            libraryI18nMapper.update(dbLibraryI18n);
                        }
                        currentI18n.remove(dbLibraryI18n);
                    }
                    for (DbLibraryI18n dbLibraryI18n : currentI18n) {
                        libraryI18nMapper.deleteByIdAndLocale(dbLibraryI18n.getId(), dbLibraryI18n.getLocale());
                    }
                }
            } catch (Exception e) {
                log.error("Unable to update i18n library {}", library.getId(), e);
            }
        }
        log.info("Finished to update i18n library in {} ms", System.currentTimeMillis() - start);
    }


}
