package com.vtesdecks.configuration.crawler;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;

public class AhrefsBotData implements CrawlerData {
    private static final Predicate<String> PREDICATE = new Predicate<String>() {
        public boolean apply(String userAgent) {
            return userAgent.contains("AhrefsBot");
        }
    };
    private static final ImmutableSet<String> HOSTNAMES = ImmutableSet.of("ahrefs.com");
    private static final AhrefsBotData INSTANCE = new AhrefsBotData();

    public static AhrefsBotData getInstance() {
        return INSTANCE;
    }

    private AhrefsBotData() {
    }

    @NotNull
    public String getIdentifier() {
        return "AHREFSBOT";
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
