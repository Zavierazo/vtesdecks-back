package com.vtesdecks.configuration.crawler;

import java.util.Collections;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;

public class FacebookExternalHitData implements CrawlerData {
    private static final Predicate<String> PREDICATE = new Predicate<String>() {
        public boolean apply(String userAgent) {
            return userAgent.contains("facebookexternalhit") || userAgent.contains("Facebot");
        }
    };
    private static final ImmutableSet<String> HOSTNAMES = ImmutableSet.of("facebook.com", "AS32934");
    private static final FacebookExternalHitData INSTANCE = new FacebookExternalHitData();

    public static FacebookExternalHitData getInstance() {
        return INSTANCE;
    }

    private FacebookExternalHitData() {
    }

    @NotNull
    public String getIdentifier() {
        return "FACEBOOKEXTERNALHIT";
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
