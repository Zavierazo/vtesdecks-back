package com.vtesdecks.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.optimaize.webcrawlerverifier.DefaultKnownCrawlerDetector;
import com.optimaize.webcrawlerverifier.KnownCrawlerDetector;
import com.optimaize.webcrawlerverifier.bots.BuiltInCrawlers;
import com.optimaize.webcrawlerverifier.bots.CrawlerData;
import com.optimaize.webcrawlerverifier.bots.KnownHostBotVerifier;
import com.optimaize.webcrawlerverifier.bots.KnownHostBotVerifierBuilder;
import com.vtesdecks.configuration.crawler.AhrefsBotData;
import com.vtesdecks.configuration.crawler.DiscordBotData;
import com.vtesdecks.configuration.crawler.FacebookExternalHitData;
import com.vtesdecks.configuration.crawler.MJ12BotData;
import com.vtesdecks.configuration.crawler.PetalBotData;
import com.vtesdecks.configuration.crawler.SemrushBotData;

@Configuration
public class CrawlerDetectorConfiguration {

    @Bean
    public KnownCrawlerDetector knownCrawlerDetector() {
        List<KnownHostBotVerifier> verifiers = new ArrayList<>();
        for (CrawlerData crawlerData : BuiltInCrawlers.get()) {
            verifiers.add(new KnownHostBotVerifierBuilder()
                .crawlerData(crawlerData)
                .dnsVerifierDefault()
                .dnsResultCacheDefault()
                .build());
        }
        verifiers.add(new KnownHostBotVerifierBuilder()
            .crawlerData(SemrushBotData.getInstance())
            .dnsVerifierDefault()
            .dnsResultCacheDefault()
            .build());
        verifiers.add(new KnownHostBotVerifierBuilder()
            .crawlerData(FacebookExternalHitData.getInstance())
            .dnsVerifierDefault()
            .dnsResultCacheDefault()
            .build());
        verifiers.add(new KnownHostBotVerifierBuilder()
            .crawlerData(AhrefsBotData.getInstance())
            .dnsVerifierDefault()
            .dnsResultCacheDefault()
            .build());
        verifiers.add(new KnownHostBotVerifierBuilder()
            .crawlerData(MJ12BotData.getInstance())
            .dnsVerifierDefault()
            .dnsResultCacheDefault()
            .build());
        verifiers.add(new KnownHostBotVerifierBuilder()
            .crawlerData(DiscordBotData.getInstance())
            .dnsVerifierDefault()
            .dnsResultCacheDefault()
            .build());
        verifiers.add(new KnownHostBotVerifierBuilder()
            .crawlerData(PetalBotData.getInstance())
            .dnsVerifierDefault()
            .dnsResultCacheDefault()
            .build());
        return new DefaultKnownCrawlerDetector(verifiers);
    }
}
