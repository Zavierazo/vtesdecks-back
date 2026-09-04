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
                if (containsRequirement(textLower, startsWith + title.name.toLowerCase())) {
                    titles.add(title);
                }
                for (LibraryTitle secondTitle : LibraryTitle.values()) {
                    if (containsRequirement(textLower, startsWith + secondTitle.name.toLowerCase() + " or " + title.name.toLowerCase())) {
                        titles.add(title);
                    }
                    if (containsRequirement(textLower, startsWith + secondTitle.name.toLowerCase() + ", " + title.name.toLowerCase())) {
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

    private static boolean containsRequirement(String text, String requirement) {
        int fromIndex = 0;
        int indexOf;
        while ((indexOf = text.indexOf(requirement, fromIndex)) != -1) {
            if (indexOf == 0 || text.charAt(indexOf - 1) == '\n' || text.charAt(indexOf - 1) == '\r') {
                return true;
            }

            int previousIndex = indexOf - 1;
            while (previousIndex >= 0 && Character.isWhitespace(text.charAt(previousIndex))) {
                previousIndex--;
            }
            if (previousIndex >= 0 && ".!?".indexOf(text.charAt(previousIndex)) != -1) {
                return true;
            }
            fromIndex = indexOf + 1;
        }
        return false;
    }


}
