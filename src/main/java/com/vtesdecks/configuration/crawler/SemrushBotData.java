package com.vtesdecks.configuration.crawler;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;

public class SemrushBotData implements CrawlerData {
    private static final Predicate<String> PREDICATE = new Predicate<String>() {
        public boolean apply(String userAgent) {
            return userAgent.contains("SemrushBot");
        }
    };
    private static final ImmutableSet<String> HOSTNAMES = ImmutableSet.of("semrush.com");
    private static final SemrushBotData INSTANCE = new SemrushBotData();

    public static SemrushBotData getInstance() {
        return INSTANCE;
    }

    private SemrushBotData() {
    }

    @NotNull
    public String getIdentifier() {
        return "SEMRUSHBOT";
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
