package com.carx.byd;

import android.graphics.Color;

public final class ThemeSpec {
    public final String key;
    public final String title;
    public final int bg, surface, surface2, surface3, accent, accentSoft, accentFaint, text, muted, muted2, danger, green;
    public final int heroRes;
    public final boolean light;

    private ThemeSpec(String key, String title, int bg, int surface, int surface2, int surface3,
                      int accent, int accentSoft, int accentFaint, int text, int muted, int muted2,
                      int danger, int green, int heroRes, boolean light) {
        this.key=key; this.title=title; this.bg=bg; this.surface=surface; this.surface2=surface2; this.surface3=surface3;
        this.accent=accent; this.accentSoft=accentSoft; this.accentFaint=accentFaint; this.text=text; this.muted=muted;
        this.muted2=muted2; this.danger=danger; this.green=green; this.heroRes=heroRes; this.light=light;
    }

    public static ThemeSpec fromKey(String key) {
        if ("gold".equals(key)) return GOLD;
        if ("blue".equals(key)) return BLUE;
        if ("white".equals(key)) return WHITE;
        return RED;
    }

    public static final ThemeSpec RED = new ThemeSpec(
            "red", "الأحمر الرياضي",
            Color.rgb(5,6,8), Color.rgb(16,17,20), Color.rgb(21,22,26), Color.rgb(29,30,34),
            Color.rgb(255,55,82), Color.rgb(190,40,60), Color.rgb(92,27,38),
            Color.rgb(248,248,250), Color.rgb(166,168,176), Color.rgb(104,107,116),
            Color.rgb(255,92,110), Color.rgb(53,221,119), R.drawable.car_theme_red, false);

    public static final ThemeSpec GOLD = new ThemeSpec(
            "gold", "الأسود الذهبي",
            Color.rgb(6,7,8), Color.rgb(14,15,17), Color.rgb(20,21,24), Color.rgb(26,27,30),
            Color.rgb(224,181,86), Color.rgb(168,132,58), Color.rgb(83,67,35),
            Color.rgb(248,248,248), Color.rgb(155,157,163), Color.rgb(105,108,115),
            Color.rgb(238,105,108), Color.rgb(88,203,135), R.drawable.car_theme_gold, false);

    public static final ThemeSpec BLUE = new ThemeSpec(
            "blue", "الأزرق الليلي",
            Color.rgb(3,11,22), Color.rgb(7,22,39), Color.rgb(10,31,54), Color.rgb(15,41,68),
            Color.rgb(49,169,255), Color.rgb(31,111,190), Color.rgb(16,61,104),
            Color.rgb(245,250,255), Color.rgb(155,181,207), Color.rgb(97,126,155),
            Color.rgb(255,92,110), Color.rgb(63,221,143), R.drawable.car_theme_blue, false);

    public static final ThemeSpec WHITE = new ThemeSpec(
            "white", "الأبيض الفاخر",
            Color.rgb(247,247,245), Color.rgb(255,255,255), Color.rgb(242,243,244), Color.rgb(234,235,237),
            Color.rgb(184,137,47), Color.rgb(144,104,34), Color.rgb(222,202,163),
            Color.rgb(25,26,29), Color.rgb(104,106,111), Color.rgb(142,144,149),
            Color.rgb(196,62,76), Color.rgb(25,167,92), R.drawable.car_theme_white, true);

    public static final ThemeSpec[] ALL = { RED, GOLD, BLUE, WHITE };
}
