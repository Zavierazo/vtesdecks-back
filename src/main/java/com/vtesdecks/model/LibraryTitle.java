package com.vtesdecks.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum LibraryTitle {
    TITLED("Titled"),
    PRIMOGEN("Primogen"),
    PRINCE("Prince"),
    JUSTICAR("Justicar"),
    INNER_CIRCLE("Inner Circle", 100431, 100364, 101088, 102004),
    BARON("Baron"),
    BISHOP("Bishop"),
    ARCHBISHOP("Archbishop"),
    PRISCUS("Priscus"),
    CARDINAL("Cardinal", 100200, 100210, 100206, 100218, 100296, 100413, 100441, 100666, 100733, 101490, 101520, 102094, 102145),
    REGENT(
            "Regent",
            100200,
            100210,
            100206,
            100218,
            100296,
            100413,
            100441,
            100666,
            100733,
            101490,
            101520,
            102094,
            102145,
            100295,
            100319,
            100525,
            101666,
            101768),
    MAGAJI("Magaji");


    private static final List<String> LIBRARY_STARTS_WITH =
            List.of("requires an ", "requires a ready ", "requires a ready, ", "requires a ready, non-anarch, ", "requires a non-sterile ",
                    "requires a ready non-sterile ", "requires a ");
    @Getter
    private final String name;
    @Getter
    private final Integer[] ids;

    LibraryTitle(String name, Integer... ids) {
        this.name = name;
        this.ids = ids;
    }


    public static List<LibraryTitle> getFromLibraryText(Integer id, String text) {
        String textLower = text.toLowerCase();
        List<LibraryTitle> titles = new ArrayList<>();
        for (String startsWith : LIBRARY_STARTS_WITH) {
            for (LibraryTitle title : LibraryTitle.values()) {
                int indexOf = textLower.indexOf(startsWith + title.name.toLowerCase());
                if (indexOf != -1) {
                    titles.add(title);
                }
                for (LibraryTitle secondTitle : LibraryTitle.values()) {
                    indexOf = textLower.indexOf(startsWith + secondTitle.name.toLowerCase() + " or " + title.name.toLowerCase());
                    if (indexOf != -1) {
                        titles.add(title);
                    }
                    indexOf = textLower.indexOf(startsWith + secondTitle.name.toLowerCase() + ", " + title.name.toLowerCase());
                    if (indexOf != -1) {
                        titles.add(title);
                    }
                }
            }
            if (!titles.isEmpty()) {
                break;
            }
        }
        for (LibraryTitle title : LibraryTitle.values()) {
            if (Arrays.stream(title.ids).anyMatch(cardId -> cardId.equals(id))) {
                titles.add(title);
            }
        }
        return titles;
    }


}
