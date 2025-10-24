package com.vtesdecks.cache.factory;


import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.vtesdecks.cache.indexable.I18n;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbLibrary;
import com.vtesdecks.db.model.DbLibraryI18n;
import com.vtesdecks.model.LibraryTaint;
import com.vtesdecks.model.LibraryTitle;
import com.vtesdecks.util.VtesUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class LibraryFactory {

    @Mapping(target = "types", ignore = true)
    @Mapping(target = "path", source = "dbLibrary.path", qualifiedByName = "mapNonEmpty")
    public abstract Library getLibrary(DbLibrary dbLibrary, @Context List<DbLibraryI18n> libraryI18nList, @Context List<DbCardShop> cardShopList);

    @AfterMapping
    protected void afterMapping(@MappingTarget Library library, DbLibrary dbLibrary, @Context List<DbLibraryI18n> libraryI18nList, @Context List<DbCardShop> cardShopList) {
        library.setImage("/img/cards/" + +dbLibrary.getId() + ".jpg");
        library.setCropImage("/img/cards/crop/" + +dbLibrary.getId() + ".jpg");
        library.setTypes(getTypes(dbLibrary));
        library.setTypeIcons(getTypeIcons(dbLibrary));
        library.setClans(VtesUtils.getLibraryClans(dbLibrary.getClan()));
        library.setClanIcons(library.getClans().stream().map(VtesUtils::getClanIcon).collect(Collectors.toSet()));
        library.setDisciplines(VtesUtils.getLibraryDisciplineNames(dbLibrary.getDiscipline()));
        library.setDisciplineIcons(VtesUtils.getLibraryDisciplines(dbLibrary.getDiscipline()));
        library.setTrifle(dbLibrary.getType().equals("Master") && dbLibrary.getText() != null && dbLibrary.getText().contains("Trifle."));
        library.setSects(VtesUtils.getLibrarySect(dbLibrary.getText()));
        library.setPathIcon(VtesUtils.getPathIcon(dbLibrary.getPath()));
        library.setTaints(VtesUtils.getLibraryTaints(dbLibrary).stream().map(LibraryTaint::getName).collect(Collectors.toSet()));
        library.setTitles(VtesUtils.getLibraryTitles(dbLibrary).stream().map(LibraryTitle::getName).collect(Collectors.toSet()));
        library.setSets(VtesUtils.getSets(dbLibrary.getSet()));
        library.setLastUpdate(dbLibrary.getModificationDate() != null ? dbLibrary.getModificationDate() : dbLibrary.getCreationDate());
        library.setPrintOnDemand(VtesUtils.isPrintOnDemand(cardShopList));
        if (library.isPrintOnDemand()) {
            for (DbCardShop cardShop : cardShopList) {
                if (VtesUtils.isPrintOnDemand(cardShopList, cardShop.getPlatform()) && !library.getSets().contains(cardShop.getSet())) {
                    library.setSets(new ImmutableList.Builder<String>().addAll(library.getSets()).add(cardShop.getSet()).build());
                }
            }
            //Force lastUpdate when new shop find
            if (library.isPrintOnDemand() && !CollectionUtils.isEmpty(cardShopList)) {
                LocalDateTime cardShopCreationDate = cardShopList.stream()
                        .map(DbCardShop::getCreationDate)
                        .findAny()
                        .orElse(null);
                if (cardShopCreationDate != null && cardShopCreationDate.isAfter(library.getLastUpdate())) {
                    library.setLastUpdate(cardShopCreationDate);
                }
            }
        }
        //Library i18n
        if (!CollectionUtils.isEmpty(libraryI18nList)) {
            Map<String, I18n> i18nMap = new HashMap<>();
            for (DbLibraryI18n libraryI18n : libraryI18nList) {
                I18n i18n = new I18n();
                i18n.setName(libraryI18n.getName());
                i18n.setText(libraryI18n.getText());
                i18n.setImage(libraryI18n.getImage());
                i18nMap.put(libraryI18n.getLocale(), i18n);
                if (libraryI18n.getModificationDate() != null && libraryI18n.getModificationDate().isAfter(library.getLastUpdate())) {
                    library.setLastUpdate(libraryI18n.getModificationDate());
                }
            }
            library.setI18n(i18nMap);
        }
    }

    private Set<String> getTypes(DbLibrary library) {
        Set<String> types = new HashSet<>();
        List<String> cardTypes = new ArrayList<>();
        if (library.getType().contains("/")) {
            cardTypes.addAll(Splitter.on('/').trimResults().omitEmptyStrings().splitToList(library.getType()));
        } else {
            cardTypes.add(library.getType());
        }
        for (String cardType : cardTypes) {
            String type = VtesUtils.getType(cardType);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private Set<String> getTypeIcons(DbLibrary library) {
        Set<String> types = new HashSet<>();
        List<String> cardTypes = new ArrayList<>();
        if (library.getType().contains("/")) {
            cardTypes.addAll(Splitter.on('/').trimResults().omitEmptyStrings().splitToList(library.getType()));
        } else {
            cardTypes.add(library.getType());
        }
        for (String cardType : cardTypes) {
            String type = VtesUtils.getTypeIcon(cardType);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    @Named("mapNonEmpty")
    public String mapNonEmpty(String value) {
        if (StringUtils.isNotEmpty(value)) {
            return value;
        }
        return null;
    }
}
