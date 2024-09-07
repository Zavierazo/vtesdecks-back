package com.vtesdecks.configuration.crawler;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;

public class DiscordBotData implements CrawlerData {
    private static final Predicate<String> PREDICATE = new Predicate<String>() {
        public boolean apply(String userAgent) {
            return userAgent.contains("Discordbot");
        }
    };
    private static final ImmutableSet<String> HOSTNAMES = ImmutableSet.of("discordapp.com");
    private static final DiscordBotData INSTANCE = new DiscordBotData();

    public static DiscordBotData getInstance() {
        return INSTANCE;
    }

    private DiscordBotData() {
    }

    @NotNull
    public String getIdentifier() {
        return "DISCORDBOT";
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
