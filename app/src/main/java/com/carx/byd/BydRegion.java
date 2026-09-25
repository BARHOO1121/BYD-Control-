package com.carx.byd;

import java.util.Arrays;
import java.util.List;

public final class BydRegion {
    public final String label, countryCode, language, timeZone, baseUrl;
    public final boolean china;

    public BydRegion(String label, String countryCode, String language, String timeZone, String baseUrl, boolean china) {
        this.label=label; this.countryCode=countryCode; this.language=language; this.timeZone=timeZone; this.baseUrl=baseUrl; this.china=china;
    }
    @Override public String toString(){return label;}

    public static final List<BydRegion> SUPPORTED = Arrays.asList(
        new BydRegion("الصين • حساب BYD الصيني", "CN", "zh", "Asia/Shanghai", "https://dilinksuperappserver-cn.byd.auto", true),
        new BydRegion("الأردن • BYD Global", "JO", "ar", "Asia/Amman", "https://dilinkappoversea-no.byd.auto", false),
        new BydRegion("الإمارات • BYD Global", "AE", "ar", "Asia/Dubai", "https://dilinkappoversea-no.byd.auto", false),
        new BydRegion("السعودية • BYD Global", "SA", "ar", "Asia/Riyadh", "https://dilinkappoversea-sa.byd.auto", false),
        new BydRegion("عُمان • BYD Global", "OM", "ar", "Asia/Muscat", "https://dilinkappoversea-om.byd.auto", false),
        new BydRegion("ألمانيا • BYD Global", "DE", "de", "Europe/Berlin", "https://dilinkappoversea-eu.byd.auto", false),
        new BydRegion("المملكة المتحدة • BYD Global", "GB", "en", "Europe/London", "https://dilinkappoversea-eu.byd.auto", false)
    );
}
