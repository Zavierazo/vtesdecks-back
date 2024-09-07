package com.vtesdecks.configuration.crawler;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;

public class PetalBotData implements CrawlerData {
    private static final Predicate<String> PREDICATE = new Predicate<String>() {
        public boolean apply(String userAgent) {
            return userAgent.contains("petalbot");
        }
    };
    private static final ImmutableSet<String> HOSTNAMES = ImmutableSet.of("petalsearch.com");
    private static final PetalBotData INSTANCE = new PetalBotData();

    public static PetalBotData getInstance() {
        return INSTANCE;
    }

    private PetalBotData() {
    }

    @NotNull
    public String getIdentifier() {
        return "PETALBOT";
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
