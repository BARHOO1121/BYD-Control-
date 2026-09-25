package com.carx.byd;

import java.util.Arrays;
import java.util.List;

public final class BydRegion {
    public final String label;
    public final String countryCode;
    public final String language;
    public final String timeZone;
    public final String baseUrl;

    public BydRegion(String label, String countryCode, String language, String timeZone, String baseUrl) {
        this.label = label;
        this.countryCode = countryCode;
        this.language = language;
        this.timeZone = timeZone;
        this.baseUrl = baseUrl;
    }

    @Override public String toString() { return label; }

    public static final List<BydRegion> SUPPORTED = Arrays.asList(
        new BydRegion("الأردن", "JO", "ar", "Asia/Amman", "https://dilinkappoversea-no.byd.auto"),
        new BydRegion("الإمارات", "AE", "ar", "Asia/Dubai", "https://dilinkappoversea-no.byd.auto"),
        new BydRegion("السعودية", "SA", "ar", "Asia/Riyadh", "https://dilinkappoversea-sa.byd.auto"),
        new BydRegion("عُمان", "OM", "ar", "Asia/Muscat", "https://dilinkappoversea-om.byd.auto"),
        new BydRegion("قطر", "QA", "ar", "Asia/Qatar", "https://dilinkappoversea-no.byd.auto"),
        new BydRegion("الكويت", "KW", "ar", "Asia/Kuwait", "https://dilinkappoversea-no.byd.auto"),
        new BydRegion("البحرين", "BH", "ar", "Asia/Bahrain", "https://dilinkappoversea-no.byd.auto"),
        new BydRegion("ألمانيا", "DE", "de", "Europe/Berlin", "https://dilinkappoversea-eu.byd.auto"),
        new BydRegion("المملكة المتحدة", "GB", "en", "Europe/London", "https://dilinkappoversea-eu.byd.auto")
    );
}
