package com.carx.byd;

public final class BydSession {
    public final String userId;
    public final String signToken;
    public final String encryToken;
    public final long createdAtMs;

    public BydSession(String userId, String signToken, String encryToken) {
        this.userId = userId;
        this.signToken = signToken;
        this.encryToken = encryToken;
        this.createdAtMs = System.currentTimeMillis();
    }

    public String contentKey() { return BydCrypto.md5Hex(encryToken); }
    public String signKey() { return BydCrypto.md5Hex(signToken); }
    public boolean isExpired() { return System.currentTimeMillis() - createdAtMs >= 12L * 60L * 60L * 1000L; }
}
