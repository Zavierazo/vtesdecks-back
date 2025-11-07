package com.vtesdecks.scheduler;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomAttr;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.CryptEntity;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.LibraryEntity;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.util.VtesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TournamentDeckScheduler {

    private static final DateTimeFormatter FORMATTER_TOURNAMENT = DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH);
    private static final Pattern DECK_NAME_REGEX = Pattern.compile("(Deck Name|Name|Deckname|deck|Deck_Name|Title)\\s*:\\s*('|\"|)\\s*(?<name>.*[^'\"]).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_REGEX = Pattern.compile(".*(?<year>(199|200|201|202|203|204|205|206|207|208|209)\\d).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYERS_REGEX = Pattern.compile("(?<number>\\d*)\\splayers", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile(".*(?<url>(https|http)\\:\\/\\/(www.|).*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRYPT = Pattern.compile("Crypt\\s\\((?<cards>\\d\\d)\\scards.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD = Pattern.compile("[xX]?(?<number>\\d+)\\s*[xX]?\\s+(?<name>[^\\d]+).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_ALT = Pattern.compile("(?<name>.*)\\s+[xX](?<number>\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DISCARDED_NAMES = Sets.newHashSet("Equipment:", "Reactions:", "Masters", "Actions", "Reactions", "REActions:", "MODIFIERS:", "Other:", "Masters:", "actions", "Disciplineless cards:", "Events:", "Actions:", "Modifiers:", "Combo:", "Mods", "Cards", "Fortitude Cards:", "REActions", "Fortitude Cards:", "Allies:", "Equipment", "Combo", "Modifiers", "Event");
    private static final Map<String, String> TYPO_FIXES = ImmutableMap.<String, String>builder().put("Denys", "Deny").put("Powerbase: Tshuane", "Powerbase: Tshwane").put("Mr Wintrop", "Mr. Winthrop").put("Mr. Wintrop", "Mr. Winthrop").put("AK-", "AK-47").put("Skin of Stell", "Skin of Steel").put("Rejuvenation", "Rejuvenate").put("Bonespour", "Bone Spur").put("Papillion", "Papillon").put("Diversiom", "Diversion").put("WWEF", "Wake with Evening's Freshness").put("WwEF", "Wake with Evening's Freshness").put("Precogntion", "Precognition").put("Sidestrike", "Side Strike").put("Thaumatugy", "Thaumaturgy").put("Perfectionnist", "Perfectionist").put("Acad. HG", "Academic Hunting Ground").put("Univ. HG", "University Hunting Ground").put("The Phartenon", "Parthenon, The").put("Antelios", "Anthelios, The Red Star").put("Deflections", "Deflection").put("Perfeccionist", "Perfectionist").put("Path of Paradoxx", "Path of Paradox, The").put("Nightmoves", "Night Moves").put("Info Superhighway", "Information Highway").put("deflections", "Deflection").put("Laptops", "Laptop Computer").put("Revalations", "Revelations").put("Ecoterrorist", "Ecoterrorists").put("Institutional Hunting Grounds", "Institution Hunting Ground").put("Personnal Involvment", "Personal Involvement").put("Obediance", "Obedience").put("Jack \"Hannibal", "Jack \"Hannibal137\" Harmon").put("Confusion Dementation", "Confusion").put("Restructure Dementation", "Restructure").put("Deny Dementation", "Deny").put("Reality Chimerstry", "Reality").put("Brujah Justcar", "Brujah Justicar").put("Kine Dominance", "Dominate Kine").put("Improvised Flame Thrower", "Improvised Flamethrower").put("Dogde", "Dodge").put("The Labyrinth", "Labyrinth, The").put("Recuitment", "Recruitment").put("Votercaptivation", "Voter Captivation").put("Golgonda", "Golconda: Inner Peace").put("Infohighway", "Information Highway").put("J.S. Simons", "J. S. Simmons, Esq.").put("Misderection", "Misdirection").put("Sportbike", "Sport Bike").put("Domiante", "Dominate").put("The barren", "Barrens, The").put("rats warning", "Rat's Warning").put("Rats' Warning", "Rat's Warning").put("Homonculus", "Homunculus").put("Decapitiate", "Decapitate").put("Humunculus", "Homunculus").put("sidestrike", "Side Strike").put("Univ HG", "University Hunting Ground").put("mindnumb", "Mind Numb").put("arsons", "Arson").put("Info Hwy", "Information Highway").put("Obliette", "Oubliette").put("GtU", "Govern the Unaligned").put("Indominability", "Indomitability").put("Wakeys", "Wake with Evening's Freshness").put("PTO", "Protect Thine Own").put("Anathelios", "Anthelios, The Red Star").put("Ravenspy", "Raven Spy").put("WWStick", "Weighted Walking Stick").put("Ecstacy", "Ecstasy").put("Earthmeld", "Earth Meld").put("Freakdrive", "Freak Drive").put("Roling with the Punshes", "Rolling with the Punches").put("Indomnability", "Indomitability").put("Revenent", "Revenant").put("Revealations", "Revelations").put("Misdirections", "Misdirection").put("ANARCHTROUBLEMAKER", "Anarch Troublemaker").put("KRC", "Kine Resources Contested").put("Mashochism", "Masochism").put("Vissitude", "Vicissitude").put("Villain", "Villein").put("Soul Gems", "Soul Gem of Etrius").put("mr wintrop", "Mr. Winthrop").put("Entice", "Enticement").put("Mirrorwalk", "Mirror Walk").put("GOLGONDA", "Golconda: Inner Peace").put("wwef", "Wake with Evening's Freshness").put("Perfeccionista", "Perfectionist").put("2nd Tradition", "Second Tradition: Domain").put("5th Tradition", "Fifth Tradition: Hospitality").put("ZipGun", "Zip Gun").put("Anna \"Dictatrix", "Anna \"Dictatrix11\" Suljic").put("Béatrice \"Oracle", "Béatrice \"Oracle171\" Tremblay").put("Earl \"Shaka", "Earl \"Shaka74\" Deams").put("Erick \"Shophet", "Erick \"Shophet125\" Franco").put("Inez \"Nurse", "Inez \"Nurse216\" Villagrande").put("Jennie \"Cassie", "Jennie \"Cassie247\" Orne").put("Jennifer \"Flame", "Jennifer \"Flame61\" Vidisania").put("John \"Cop", "John \"Cop90\" O'Malley").put("Leaf \"Potter", "Leaf \"Potter116\" Pankowski").put("Liz \"Ticket", "Liz \"Ticket312\" Thornton").put("Lupe \"Cabbie", "Lupe \"Cabbie22\" Droin").put("Marion \"Teacher", "Marion \"Teacher193\" Perks").put("Paul \"Sixofswords", "Paul \"Sixofswords29\" Moreton").put("Peter \"Outback", "Peter \"Outback295\" Rophail").put("Travis \"Traveler", "Travis \"Traveler72\" Miller").put("Xian \"DziDzat", "Xian \"DziDzat155\" Quan").build();


    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CryptRepository cryptRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private DeckCardRepository deckCardRepository;

    @Autowired
    private DeckIndex deckIndex;

    //To run immediately after startup 
    //@Scheduled(fixedDelay = 86400000L)
    //Update tournament decks once a day at 00:00
    @Scheduled(cron = "${jobs.scrappingDecksCron:0 0 0 * * *}")
    public void scrappingDecks() {
        log.info("Starting tournament decks scrapping...");
        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);
        try {
            String searchUrl = "http://www.vekn.fr/decks/twd.htm";
            HtmlPage page = client.getPage(searchUrl);
            List<HtmlElement> items = (List<HtmlElement>) page.getByXPath("//a[@href='#']");
            for (HtmlElement item : items) {
                DomAttr domAttrId = item.getAttributeNode("id");
                if (domAttrId != null) {
                    String deckId = domAttrId.getNodeValue();
                    DomElement paragraph = item.getNextElementSibling();
                    if ("p".equals(paragraph.getLocalName())) {
                        try {
                            parseDeck(deckId, paragraph.getTextContent());
                        } catch (Exception e) {
                            log.error("Unable to parse deck {}", paragraph.getTextContent());
                            throw e;
                        }
                    } else {
                        log.warn("Paragraph not found for deckId {}", deckId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unable to scan decks", e);
        }
        log.info("Finished to scan");
    }

    private void parseDeck(String deckId, String text) {
        String id = "tournament-" + deckId;
        Optional<DeckEntity> optionalDeck = deckRepository.findById(id);
        DeckEntity actual = optionalDeck.orElse(null);
        if (actual == null || Boolean.FALSE.equals(actual.getVerified())) {
            DeckEntity deck = actual != null ? actual : new DeckEntity();
            deck.setId(id);
            deck.setType(DeckType.TOURNAMENT);
            deck.setSource("http://www.vekn.fr/decks/twd.htm#" + deckId);
            Map<Integer, DeckCardEntity> deckCards = new HashMap<>();
            Map<Integer, String> debugLines = new HashMap<>();
            try (Scanner scanner = new Scanner(text)) {
                scanner.nextLine();//Skip first empty string
                boolean empty = false;
                List<String> headers = new ArrayList<>();
                while (scanner.hasNextLine() && !empty) {
                    String line = scanner.nextLine();
                    empty = StringUtils.isBlank(line);
                    if (!empty) {
                        headers.add(line);
                    }
                }
                deck.setTournament(headers.get(0));
                deck.setPlayers(getPlayers(headers));
                deck.setYear(getYear(headers));
                deck.setCreationDate(getCreationDate(headers));
                //Nunca entra pero por si las moscas xD
                if (deck.getCreationDate() == null && deck.getYear() != null) {
                    deck.setCreationDate(LocalDate.of(deck.getYear(), 1, 1).atStartOfDay());
                }
                deck.setAuthor(getAuthor(headers));
                deck.setUrl(getUrl(headers));
                deck.setViews(actual != null ? actual.getViews() : 0);
                deck.setVerified(true);//Assume first scan goes ok, manual mark for rescan if needed

                boolean crypt = false;
                List<String> nameDescriptionPart = new ArrayList<>();
                while (!crypt) {
                    String line = scanner.nextLine();
                    nameDescriptionPart.add(line);
                    Matcher deckNameMatcher = DECK_NAME_REGEX.matcher(line);
                    if (deck.getName() == null && deckNameMatcher.matches()) {
                        deck.setName(deckNameMatcher.group("name"));
                    }
                    Matcher matcher = CRYPT.matcher(line);
                    if (matcher.matches()) {
                        crypt = true;
                    }
                }
                if (deck.getName() == null) {
                    for (int i = 0; i < nameDescriptionPart.size() && deck.getName() == null; i++) {
                        String possibleName = nameDescriptionPart.get(i);
                        if (!StringUtils.isBlank(possibleName) && possibleName.length() < 30 && !possibleName.startsWith("--") && possibleName.startsWith("\"") && possibleName.endsWith("\"")) {
                            deck.setName(possibleName.substring(1, possibleName.length() - 1));
                        }
                    }
                }
                StringBuilder description = new StringBuilder();
                boolean descriptionFound = false;
                for (int i = 0; i < nameDescriptionPart.size() - 1; i++) {
                    String fragment = nameDescriptionPart.get(i);
                    if (fragment.toLowerCase().startsWith("description")) {
                        descriptionFound = true;
                        if (fragment.indexOf(":") != -1) {
                            fragment = fragment.substring(fragment.indexOf(":") + 1);
                        }
                    }
                    if (descriptionFound && !StringUtils.isBlank(fragment) && !fragment.contains("http://www.secretlibrary.info")) {
                        if (description.length() > 0) {
                            description.append("<br/>");
                        }
                        description.append(fragment);
                    }
                }
                if (description.length() > 0) {
                    deck.setDescription(description.toString());
                }
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    Matcher cardMatcher = CARD.matcher(line);
                    if (!cardMatcher.matches()) {
                        cardMatcher = CARD_ALT.matcher(line);
                    }
                    if (cardMatcher.matches()) {
                        log.trace("Found {} x '{}'", cardMatcher.group("number"), cardMatcher.group("name"));
                        Integer number = Integer.parseInt(cardMatcher.group("number"));
                        String name = StringUtils.normalizeSpace(cardMatcher.group("name"));
                        //Some decks have cards with comments referring to another cars, who cause confusion to the algorithm
                        if (name.contains("--") && !name.startsWith("--")) {
                            name = StringUtils.trim(name.substring(0, name.indexOf("--")));
                        }
                        //Remove comments inside parentesis. (ADV) & (TM) are part of card names
                        if (name.contains("(") && !name.startsWith("(") && !name.contains("(ADV)") && !name.contains("(TM)")) {
                            name = StringUtils.trim(name.substring(0, name.indexOf("(")));
                        }
                        if (line.contains(".44 Magnum")) {
                            name = ".44 Magnum";
                        }
                        if (line.contains("Channel 10")) {
                            name = "Channel 10";
                        }
                        if (line.contains("419 Operation")) {
                            name = "419 Operation";
                        }
                        if (TYPO_FIXES.containsKey(name)) {
                            name = TYPO_FIXES.get(name);
                        }
                        if (!DISCARDED_NAMES.contains(name)) {
                            List<TextSearch> results = deckCardRepository.search(name, name.contains("(ADV)"));//TODO Search ADV (Advanced)
                            if (CollectionUtils.isNotEmpty(results)) {
                                //Get result with exact string or first element who have more score
                                TextSearch result = selectResult(name, line, results);
                                Integer cardId = result.getId();
                                storeDeckCard(deck, deckCards, debugLines, line, cardId, number);
                            } else {
                                try {
                                    CryptEntity dbCrypt = cryptRepository.selectByName(name);
                                    if (dbCrypt != null) {
                                        storeDeckCard(deck, deckCards, debugLines, line, dbCrypt.getId(), number);
                                    } else {
                                        LibraryEntity dbLibrary = libraryRepository.selectByName(name);
                                        if (dbLibrary != null) {
                                            storeDeckCard(deck, deckCards, debugLines, line, dbLibrary.getId(), number);
                                        } else {
                                            log.info("Unable to found card {}  on deck {} for line '{}'", name, deck.getName(), line);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("Unable to find with failover card '{}' for line '{}'", name, line, e);
                                }
                            }
                        }
                    } else if (line.contains("x 419 Operation")) {
                        LibraryEntity dbLibrary = libraryRepository.selectByName("419 Operation");
                        storeDeckCard(deck, deckCards, debugLines, line, dbLibrary.getId(), Integer.parseInt(line.substring(0, line.indexOf('x'))));
                    }
                }
            }
            if (deck.getName() == null) {
                if (deck.getTournament() != null && deck.getYear() != null && deck.getAuthor() != null) {
                    deck.setName(deck.getAuthor() + ", " + deck.getTournament() + ", " + deck.getYear());
                } else if (deck.getTournament() != null && deck.getYear() != null) {
                    deck.setName(deck.getTournament() + ", " + deck.getYear());
                } else {
                    throw new IllegalArgumentException("Unable to define name for deck " + deck.toString());
                }
            }
            if (deck.getUrl() != null && deck.getUrl().contains("vekn.net/event-calendar/event")) {
                LocalDateTime eventCreationDate = getCreationDate(deck.getUrl());
                if (eventCreationDate != null) {
                    deck.setCreationDate(eventCreationDate);
                }
            }
            if (isValidDeck(deck, deckCards)) {
                boolean updated = false;
                boolean insert = false;
                if (actual == null) {
                    deckRepository.save(deck);
                    updated = true;
                    insert = true;
                    log.debug("Insert deck {}", deck.getId());
                } else if (!actual.equals(deck)) {
                    log.warn("Deck {} updated metadata", deck.getId());
//                    deckMapper.update(deck);
//                    updated = true;
                }
                List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(deck.getId());
                for (Map.Entry<Integer, DeckCardEntity> card : deckCards.entrySet()) {
                    DeckCardEntity dbCard = dbCards.stream().filter(db -> db.getId().getCardId().equals(card.getKey()) && db.getId().getDeckId().equals(deck.getId())).findFirst().orElse(null);
                    if (dbCard == null) {
                        if (insert) {
                            deckCardRepository.save(card.getValue());
                            updated = true;
                            log.debug("Insert deck card {}", card.getValue());
                        } else {
                            log.warn("New card detected for card {} of deck {}", card.getValue(), id);
                        }
                    } else if (!dbCard.equals(card.getValue())) {
                        log.warn("Found new card count for card {} of deck {}", card.getValue(), id);
                        deckCardRepository.save(card.getValue());
                        updated = true;
                    }
                }
                //Delete removed cards
                for (DeckCardEntity card : dbCards) {
                    DeckCardEntity deckCardEntity = deckCards.get(card.getId().getCardId());
                    if (deckCardEntity == null) {
                        log.warn("Missing card {} of deck {}", card, id);
                        deckCardRepository.delete(card);
                        updated = true;
                    }
                }
                if (updated) {
                    deckIndex.enqueueRefreshIndex(deck.getId());
                }
            }
        }
    }

    private boolean isValidDeck(DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        int crypt = 0;
        int library = 0;
        for (DeckCardEntity card : deckCards.values()) {
            if (VtesUtils.isCrypt(card.getId().getCardId())) {
                crypt += card.getNumber();
            } else if (VtesUtils.isLibrary(card.getId().getCardId())) {
                library += card.getNumber();
            }
        }
        if (crypt >= 12 && library >= 60 && library <= 90) {
            return true;
        } else if (deck.getYear() < 2015 && crypt >= 11 && library >= 59 && library <= 91) {
            //crypt of 59/91 because some old decks are have illegal amount of cards....
            //library of 11 because some old decks are have illegal amount of cards....
            return true;
        } else {
            log.error("Invalid number of cards for deck {}. Crypt {} Library {}", deck.getId(), crypt, library);
            return false;
        }
    }


    private void storeDeckCard(DeckEntity deck, Map<Integer, DeckCardEntity> deckCards, Map<Integer, String> debugLines, String line, Integer cardId, Integer number) {
        DeckCardEntity dbDeckCardEntity = new DeckCardEntity();
        dbDeckCardEntity.setId(new DeckCardEntity.DeckCardId());
        dbDeckCardEntity.getId().setDeckId(deck.getId());
        dbDeckCardEntity.getId().setCardId(cardId);
        dbDeckCardEntity.setNumber(number);
        if (deckCards.containsKey(cardId)) {
            DeckCardEntity previousCard = deckCards.get(cardId);
            //Exactly the same line duplicated, author have made a mistake
            String debugLine = debugLines.get(cardId);
            if (debugLine.equalsIgnoreCase(line)) {
                previousCard.setNumber(previousCard.getNumber() + dbDeckCardEntity.getNumber());
            } else if ((debugLine.contains("Dominate Kine") || debugLine.contains("Kine Dominance")) && (line.contains("Dominate Kine") || line.contains("Kine Dominance"))) {
                previousCard.setNumber(previousCard.getNumber() + dbDeckCardEntity.getNumber());
            } else {
                log.warn("Duplicated card on deck {} - {}!! \"{}\": {} Debug: {}", deck.getId(), deck.getName(), line, deckCards.get(cardId), debugLines.get(cardId));
            }
        } else {
            deckCards.put(cardId, dbDeckCardEntity);
            debugLines.put(cardId, line);
        }
    }

    private TextSearch selectResult(String name, String line, List<TextSearch> results) {
        //First loop for exact name with G6 & G7 cards...
        for (TextSearch result : results) {
            if (result.getName().toLowerCase().startsWith(name.toLowerCase())) {
                if (result.getName().endsWith("(G6)") && line.endsWith(":6")) {
                    return result;
                } else if (result.getName().endsWith("(G7)") && line.endsWith(":7")) {
                    return result;
                }
            }
        }
        //Second loop with exact name
        for (TextSearch result : results) {
            if (result.getName().equalsIgnoreCase(name)) {
                return result;
            }
            //'The Labyrinth' equals to 'Labyrinth, The'
            int theIndex = result.getName().indexOf(", The");
            if (theIndex != -1 && ("The " + result.getName().substring(0, theIndex)).equalsIgnoreCase(name)) {
                return result;
            }
        }
        //Else return most probable card
        return results.get(0);
    }

    private LocalDateTime getCreationDate(String url) {
        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);
        try {
            HtmlPage page = client.getPage(url);
            List<HtmlElement> items = (List<HtmlElement>) page.getByXPath("//div[contains(@class, 'eventdate')]");
            for (HtmlElement item : items) {
                String value = item.getTextContent();
                try {
                    TemporalAccessor parse = FORMATTER.parse(value, new ParsePosition(0));
                    return LocalDateTime.from(parse);
                } catch (Exception e) {
                    log.error("Unable to parse {} to date", value);
                }
            }
        } catch (FailingHttpStatusCodeException e) {
            log.info("Url {} returns {} error code", url, e.getStatusCode());
        } catch (Exception e) {
            log.error("Unable to scan creation date", e);
        }
        return null;
    }

    private String getUrl(List<String> headers) {
        for (String header : headers) {
            Matcher matcher = URL.matcher(header);
            if (matcher.matches()) {
                return matcher.group("url");
            }
        }
        return null;
    }

    private String getAuthor(List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            Matcher matcher = URL.matcher(header);
            if (matcher.matches()) {
                for (int j = i - 1; j >= 0; j--) {
                    if (!headers.get(j).contains("Organizer") && !headers.get(j).contains("Judge")) {
                        return headers.get(j);
                    }

                }
            }
        }
        for (int j = headers.size() - 1; j >= 0; j--) {
            if (!headers.get(j).contains("Organizer") && !headers.get(j).contains("Judge")) {
                return headers.get(j);
            }
        }
        return null;
    }

    private Integer getYear(List<String> headers) {
        for (String header : headers) {
            Matcher matcher = YEAR_REGEX.matcher(header);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group("year"));
            }
        }
        return null;
    }

    private Integer getPlayers(List<String> headers) {
        for (String header : headers) {
            Matcher matcher = PLAYERS_REGEX.matcher(header);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group("number"));
            }
        }
        return null;
    }

    private LocalDateTime getCreationDate(List<String> headers) {
        for (String header : headers) {
            Matcher matcher = YEAR_REGEX.matcher(header);
            if (matcher.matches()) {
                try {
                    TemporalAccessor parse = FORMATTER_TOURNAMENT.parse(header.replaceAll("(?<=\\d)(st|nd|rd|th)", ""), new ParsePosition(0));
                    return LocalDate.from(parse).atStartOfDay();
                } catch (Exception e) {
                    log.debug("Unable to parse {} to date", header);
                }
            }
        }
        return null;
    }

}
