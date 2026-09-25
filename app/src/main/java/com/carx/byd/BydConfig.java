package com.carx.byd;

public final class BydConfig {
    public final String username;
    public final String password;
    public final String controlPin;
    public final BydRegion region;

    public final String appVersion = "3.5.1";
    public final String appInnerVersion = "351";
    public final String softType = "0";
    public final String tboxVersion = "3";
    public final String isAuto = "1";
    public final String identifierType = "0";

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
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.controlPin = controlPin == null ? "" : controlPin.trim();
        this.region = region;
    }

    public String imeiMd5() { return BydCrypto.md5Hex(username); }
}
