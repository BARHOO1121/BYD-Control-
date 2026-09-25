package com.carx.byd;

public final class BydConfig {
    public final String username;
    public final String password;
    public final String controlPin;
    public final BydRegion region;

    // Overseas stack
    public final String appVersion = "3.5.1";
    public final String appInnerVersion = "351";
    public final String isAuto = "1";

    // China stack (mainland BYD app)
    public final String appChannel = "99";
    public final String cnAppVersion = "9.10.2";
    public final String cnAppInnerVersion = "502";
    public final String targetBrand = "1";
    public final String vehicleBrand = "1";
    public final String networkOperator = "无";
    public final String brandFlag = "dynasty";

    public final String softType = "0";
    public final String tboxVersion = "3";

    public final String ostype = "and";
    public final String imei = "BANGCLE01234";
    public final String mac = "00:00:00:00:00:00";
    public final String model = "POCO F1";
    public final String sdk = "35";
    public final String mod = "Xiaomi";
    public final String mobileBrand = "XIAOMI";
    public final String mobileModel = "POCO F1";
    public final String deviceType = "0";
    public final String networkType = "wifi";
    public final String osType = "15";
    public final String osVersion = "35";

    public BydConfig(String username, String password, String controlPin, BydRegion region) {
        String u = username == null ? "" : username.trim().replace(" ", "");
        if (region != null && region.china && u.startsWith("+86")) u = u.substring(3);
        if (region != null && region.china && u.startsWith("0086")) u = u.substring(4);
        this.username = u;
        this.password = password == null ? "" : password;
        this.controlPin = controlPin == null ? "" : controlPin.trim();
        this.region = region;
    }

    public boolean isChina() { return region != null && region.china; }
    public String identifierType() {
        if (isChina()) return "0"; // CN token envelope; login uses loginType instead.
        return username.contains("@") ? "0" : "1";
    }
    public String imeiMd5() { return BydCrypto.md5Hex(username); }
}
