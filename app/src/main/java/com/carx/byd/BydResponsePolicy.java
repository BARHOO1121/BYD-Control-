package com.carx.byd;

import org.json.JSONObject;

/** A payload's presence is not proof of successful authentication/authorization. */
public final class BydResponsePolicy {
    private BydResponsePolicy() {}

    public static boolean isAccepted(JSONObject response) {
        return response != null && "0".equals(response.optString("code", ""))
                && !Boolean.FALSE.equals(response.opt("success"));
    }

    public static boolean isExpired(String code) {
        return "1002".equals(code) || "1005".equals(code) || "1010".equals(code);
    }
}
