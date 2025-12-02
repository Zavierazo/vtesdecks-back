package com.vtesdecks.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
public enum ShopPlatform {
    DTC("DriveThruCards", "https://drivethrucards.com", true, true, true),
    GP("GamePod", "https://gamepod.es", true, true, true),
    CGG("CardGameGeek", "https://shop.cardgamegeek.com", false, false, false);

    @Getter
    private String fullName;
    @Getter
    private String baseUrl;
    @Getter
    private boolean enabled;
    @Getter
    private boolean showButton;
    @Getter
    private boolean printOnDemand;
}
