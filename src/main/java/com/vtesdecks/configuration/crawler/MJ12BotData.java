package com.vtesdecks.configuration.crawler;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;

public class MJ12BotData implements CrawlerData {
    private static final Predicate<String> PREDICATE = new Predicate<String>() {
        public boolean apply(String userAgent) {
            return userAgent.contains("MJ12bot");
        }
    };
    private static final ImmutableSet<String> HOSTNAMES = ImmutableSet.of("mj12bot.com", "majestic.com");
    private static final MJ12BotData INSTANCE = new MJ12BotData();

    public static MJ12BotData getInstance() {
        return INSTANCE;
    }

    private MJ12BotData() {
    }

    @NotNull
    public String getIdentifier() {
        return "MJ12BOT";
    }

    @NotNull
    public Predicate<String> getUserAgentChecker() {
        return PREDICATE;
    }

    @NotNull
    public Set<String> getIps() {
        return Collections.emptySet();
    }

    @NotNull
    public Set<String> getHostnames() {
        return HOSTNAMES;
    }
}
