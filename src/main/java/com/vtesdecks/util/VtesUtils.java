package com.vtesdecks.util;

import com.google.common.base.Splitter;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbCrypt;
import com.vtesdecks.db.model.DbLibrary;
import com.vtesdecks.model.CardType;
import com.vtesdecks.model.Clan;
import com.vtesdecks.model.CryptTaint;
import com.vtesdecks.model.Discipline;
import com.vtesdecks.model.LibraryTaint;
import com.vtesdecks.model.LibraryTitle;
import com.vtesdecks.model.Path;
import com.vtesdecks.model.Sect;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class VtesUtils {
    private static final int LIBRARY_ID_MIN = 100000;
    private static final int LIBRARY_ID_MAX = 200000;
    private static final int CRYPT_ID_MIN = 200000;
    private static final int CRYPT_ID_MAX = 300000;

    public static boolean isCrypt(Integer id) {
        return id > CRYPT_ID_MIN && id < CRYPT_ID_MAX;
    }


    public static boolean isLibrary(Integer id) {
        return id > LIBRARY_ID_MIN && id < LIBRARY_ID_MAX;
    }

    public static String getClanIcon(String clan) {
        if (StringUtils.isBlank(clan)) {
            return null;
        }
        Clan clanEnum = Clan.getFromName(clan);
        if (clanEnum != null) {
            return clanEnum.getIcon();
        } else {
            log.info("Clan not found for '{}'", clan);
            return null;
        }
    }

    public static String getPathIcon(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        Path pathEnum = Path.getFromName(path);
        if (pathEnum != null) {
            return pathEnum.getIcon();
        } else {
            log.info("Path not found for '{}'", path);
            return null;
        }
    }


    public static String getDisciplineIcon(String discipline, boolean superior) {
        Discipline disciplineEnum = Discipline.getFromName(discipline);
        if (disciplineEnum != null) {
            return (disciplineEnum.getIcon() + (superior ? "sup" : ""));
        } else {
            log.info("Discipline not found for '{}'", discipline);
        }
        return null;
    }

    public static String getDisciplineIconFromAbbreviation(String discipline) {
        Discipline disciplineEnum = Discipline.getFromName(discipline);
        if (disciplineEnum != null) {
            return disciplineEnum.getIcon() + (StringUtils.isAllUpperCase(discipline) ? "sup" : "");
        } else {
            log.info("Discipline not found for abbreviation {}", discipline);
            return null;
        }
    }

    public static String getType(String type) {
        CardType cardType = CardType.getFromName(type);
        if (cardType != null) {
            return cardType.getName();
        } else {
            log.info("Type not found for {}", type);
        }
        return null;
    }

    public static String getTypeIcon(String type) {
        CardType cardType = CardType.getFromName(type);
        if (cardType != null) {
            return cardType.getIcon();
        } else {
            log.info("Type not found for {}", type);
        }
        return null;
    }

    public static Set<String> getLibraryDisciplines(String disciplineValue) {
        Set<String> disciplines = new HashSet<>();
        for (String cardDiscipline : getLibraryDisciplineNames(disciplineValue)) {
            String discipline = VtesUtils.getDisciplineIcon(cardDiscipline, false);
            if (discipline != null) {
                disciplines.add(discipline);
            }
        }
        return disciplines;
    }

    public static Set<String> getLibraryDisciplineNames(String disciplineValue) {
        Set<String> disciplineNames = new HashSet<>();
        if (StringUtils.isNoneBlank(disciplineValue)) {
            if (disciplineValue.contains("&")) {
                disciplineNames.addAll(Splitter.on('&').trimResults().omitEmptyStrings().splitToList(disciplineValue));
            } else if (disciplineValue.contains("/")) {
                disciplineNames.addAll(Splitter.on('/').trimResults().omitEmptyStrings().splitToList(disciplineValue));
            } else {
                disciplineNames.add(disciplineValue);
            }
        }
        //Parche para Thaumaturgy...
        return disciplineNames.stream().map(name -> name.equals("Thaumaturgy") ? Discipline.BLOOD_SORCERY.getName() : name).collect(Collectors.toSet());
    }

    public static Set<String> getCryptDisciplines(String type, String disciplineValue) {
        Set<String> disciplines = new HashSet<>();
        if (disciplineValue != null) {
            List<String> cardDisciplines = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(disciplineValue);
            for (String cardDiscipline : cardDisciplines) {
                if (!cardDiscipline.equals("-none-")) {
                    if (type.equalsIgnoreCase("Imbued") && cardDiscipline.equalsIgnoreCase("vis")) {
                        cardDiscipline = "Imbuedvis";
                    }
                    String discipline = VtesUtils.getDisciplineIconFromAbbreviation(cardDiscipline);
                    if (discipline != null) {
                        disciplines.add(discipline);
                    }
                }
            }
        }
        return disciplines;
    }

    public static Set<String> getCryptDisciplineNames(String type, String disciplineValue, boolean onlySuperior) {
        Set<String> disciplines = new HashSet<>();
        if (disciplineValue != null) {
            List<String> cardDisciplines = Splitter.on(' ').trimResults().omitEmptyStrings().splitToList(disciplineValue);
            for (String cardDiscipline : cardDisciplines) {
                if (!cardDiscipline.equals("-none-")) {
                    if (type.equalsIgnoreCase("Imbued") && cardDiscipline.equalsIgnoreCase("vis")) {
                        cardDiscipline = "Imbuedvis";
                    }
                    if (onlySuperior && StringUtils.isAllLowerCase(cardDiscipline)) {
                        continue;
                    }
                    Discipline disciplineEnum = Discipline.getFromName(cardDiscipline);
                    if (disciplineEnum != null) {
                        disciplines.add(disciplineEnum.getName());
                    }
                }
            }
        }
        return disciplines;
    }

    public static String getCryptSect(String type, String text) {
        Sect sect = Sect.getCryptFromText(type);
        if (sect != null) {
            return sect.getName();
        }
        if (text != null) {
            sect = Sect.getCryptFromText(text);
            if (sect != null) {
                return sect.getName();
            }
        }
        return null;
    }

    public static Set<String> getLibrarySect(String text) {
        return Sect.getFromLibraryText(text).stream().map(Sect::getName).collect(Collectors.toSet());
    }

    public static List<CryptTaint> getCryptTaints(DbCrypt crypt) {
        List<CryptTaint> taints = CryptTaint.getFromText(crypt.getText());
        if (StringUtils.isNotBlank(crypt.getTitle()) && !taints.contains(CryptTaint.TITLED)) {
            taints.add(CryptTaint.TITLED);
        }
        if (crypt.getAdv() != null && crypt.getAdv() && !taints.contains(CryptTaint.ADVANCED)) {
            taints.add(CryptTaint.ADVANCED);
        }
        if (StringUtils.isNotBlank(crypt.getBanned()) && !taints.contains(CryptTaint.BANNED)) {
            taints.add(CryptTaint.BANNED);
        }
        return taints;
    }

    public static List<LibraryTaint> getLibraryTaints(DbLibrary library) {
        List<LibraryTaint> taints = LibraryTaint.getFromText(library.getId(), library.getText());
        if (StringUtils.isNotBlank(library.getBanned()) && !taints.contains(CryptTaint.BANNED)) {
            taints.add(LibraryTaint.BANNED);
        }
        if (library.getBurn() != null && library.getBurn() && !taints.contains(LibraryTaint.BURN_OPTION)) {
            taints.add(LibraryTaint.BURN_OPTION);
        }
        if (library.getType() != null && library.getType().contains("/")) {
            taints.add(LibraryTaint.MULTI_TYPE);
        }
        if (library.getDiscipline() != null && library.getDiscipline().contains("/")) {
            taints.add(LibraryTaint.MULTI_DISCIPLINE);
        }
        return taints;
    }

    public static List<LibraryTitle> getLibraryTitles(DbLibrary library) {
        List<LibraryTitle> titles = LibraryTitle.getFromLibraryText(library.getId(), library.getText());
        if (library.getId().equals(100431)) {
            titles.add(LibraryTitle.INNER_CIRCLE);
        }
        return titles;
    }

    public static Set<String> getLibraryClans(String clan) {
        return new HashSet<>(Splitter.on('/').trimResults().omitEmptyStrings().splitToList(clan));
    }

    public static List<String> getSets(String set) {
        if (set != null) {
            return Splitter.on(',').trimResults().omitEmptyStrings().splitToList(set);
        } else {
            return null;
        }
    }

    public static boolean isPrintOnDemand(List<DbCardShop> cardShopList) {
        return !CollectionUtils.isEmpty(cardShopList);
    }

    public static boolean isPrintOnDemand(List<DbCardShop> cardShopList, String platform) {
        if (CollectionUtils.isEmpty(cardShopList)) {
            return false;
        }
        return cardShopList.stream().anyMatch(cardShop -> cardShop.getPlatform().equals(platform));
    }
}
