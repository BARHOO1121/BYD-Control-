package com.carx.byd;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;

/** Public diagnostics are built from fixed labels; exception/server messages are never echoed. */
public final class SafeDiagnostics {
    private SafeDiagnostics() {}

    public static String describe(Throwable failure) {
        if (failure instanceof BydApiClient.BydException) return ((BydApiClient.BydException) failure).safeMessage();
        if (failure instanceof SocketTimeoutException) return "انتهت مهلة الاتصال. أعد المحاولة. [NETWORK_TIMEOUT]";
        if (failure instanceof IOException) return "تعذر الوصول إلى الخادم عبر الشبكة. [NETWORK_ERROR]";
        if (failure instanceof GeneralSecurityException) return "تعذر التحقق من البيانات المشفرة. [CRYPTO_ERROR]";
        return "تعذر إكمال هذه الخطوة. أعد المحاولة وشارك رمز التشخيص فقط. [UNEXPECTED_ERROR]";
    }

    /** Allowlist actual known BYD status codes, not arbitrary numeric account identifiers. */
    public static String responseCode(Object raw) {
        String code = String.valueOf(raw);
        switch (code) {
            case "0": case "1002": case "1005": case "1010": return code;
            default: return "other";
        }
    }
}
