package com.carx.byd;

public final class BydSession {
    public final String userId;
    public final String superId;
    public final String signToken;
    public final String encryToken;
    public final long createdAtMs;

    public BydSession(String userId, String signToken, String encryToken) {
        this(userId, "", signToken, encryToken);
    }

    public BydSession(String userId, String superId, String signToken, String encryToken) {
        this.userId = userId == null ? "" : userId;
        this.superId = superId == null ? "" : superId;
        this.signToken = signToken == null ? "" : signToken;
        this.encryToken = encryToken == null ? "" : encryToken;
        this.createdAtMs = System.currentTimeMillis();
    }

    public String apiIdentifier() { return !superId.isEmpty() ? superId : userId; }
    public String contentKey() { return BydCrypto.md5Hex(encryToken); }
    public String signKey() { return BydCrypto.md5Hex(signToken); }
    public boolean isExpired() { return System.currentTimeMillis() - createdAtMs >= 12L * 60L * 60L * 1000L; }
}
