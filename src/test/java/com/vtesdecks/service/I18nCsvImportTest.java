package com.vtesdecks.service;

import com.opencsv.CSVReader;
import com.vtesdecks.csv.entity.CryptI18nCsv;
import com.vtesdecks.csv.entity.LibraryI18nCsv;
import com.vtesdecks.csv.mapper.CryptI18nCsvMapper;
import com.vtesdecks.csv.mapper.LibraryI18nCsvMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class I18nCsvImportTest {
    @Test
    void importsTheRealTranslationFilesWithOnlyIdLocaleNameAndText() throws Exception {
        var service = new AfterStartupService();
        for (String name : List.of("vtescrypt", "vteslib")) {
            var resource = new ClassPathResource("data/" + name + ".i18n.csv");
            try (var reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                assertArrayEquals(new String[]{"id", "locale", "name", "text"}, reader.readNext());
            }
        }
        try (var reader = new InputStreamReader(new ClassPathResource("data/vtescrypt.i18n.csv").getInputStream(), StandardCharsets.UTF_8)) {
            List<CryptI18nCsv> rows = ReflectionTestUtils.invokeMethod(service, "parse", reader, CryptI18nCsv.class);
            assertNotNull(rows);
            assertFalse(rows.isEmpty());
            var mapped = CryptI18nCsvMapper.INSTANCE.toEntities(rows);
            assertEquals(rows.size(), mapped.size());
            for (int i = 0; i < rows.size(); i++) {
                assertEquals(rows.get(i).getId(), mapped.get(i).getId().getCardId());
                assertEquals(rows.get(i).getLocale(), mapped.get(i).getId().getLocale());
                assertEquals(rows.get(i).getName(), mapped.get(i).getName());
                assertEquals(rows.get(i).getText(), mapped.get(i).getText());
            }
        }
        try (var reader = new InputStreamReader(new ClassPathResource("data/vteslib.i18n.csv").getInputStream(), StandardCharsets.UTF_8)) {
            List<LibraryI18nCsv> rows = ReflectionTestUtils.invokeMethod(service, "parse", reader, LibraryI18nCsv.class);
            assertNotNull(rows);
            assertFalse(rows.isEmpty());
            var mapped = LibraryI18nCsvMapper.INSTANCE.toEntities(rows);
            assertEquals(rows.size(), mapped.size());
            for (int i = 0; i < rows.size(); i++) {
                assertEquals(rows.get(i).getId(), mapped.get(i).getId().getCardId());
                assertEquals(rows.get(i).getLocale(), mapped.get(i).getId().getLocale());
                assertEquals(rows.get(i).getName(), mapped.get(i).getName());
                assertEquals(rows.get(i).getText(), mapped.get(i).getText());
            }
        }
    }
}
