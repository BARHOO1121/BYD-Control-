package com.carx.byd;

import org.json.JSONArray;

/** A parsed response, not a claim about vehicle ownership or online state. */
public final class VehicleListResult {
    public enum Status { LOADED, EMPTY, UNSUPPORTED_FORMAT, INVALID_RECORDS, AMBIGUOUS_FORMAT, SERVER_REJECTED }

    public final Status status;
    public final JSONArray vehicles;
    public final String shape;
    public final String path;
    public final int receivedCount;
    public final int invalidCount;

    VehicleListResult(Status status, JSONArray vehicles, String shape, String path, int receivedCount, int invalidCount) {
        this.status = status;
        this.vehicles = vehicles;
        this.shape = shape;
        this.path = path;
        this.receivedCount = receivedCount;
        this.invalidCount = invalidCount;
    }

    public boolean isRecognized() { return status == Status.LOADED || status == Status.EMPTY; }

    /** Only parser-owned labels and counts; never server values or arbitrary field names. */
    public String safeSummary() {
        return "VL_" + status.name() + " | shape=" + shape + " | path=" + path
                + " | received=" + receivedCount + " | invalid=" + invalidCount;
    }

    public String userMessage() {
        switch (status) {
            case LOADED: return "تم تسجيل الدخول وجلب قائمة السيارات بنجاح.";
            case EMPTY: return "نجح تسجيل الدخول، لكن BYD أعاد قائمة سيارات فارغة لهذه المنطقة. تحقق من ظهور السيارة في تطبيق BYD الرسمي بالحساب والمنطقة نفسيهما، ثم أعد المحاولة.";
            case INVALID_RECORDS: return "نجح تسجيل الدخول، لكن قائمة السيارات تحتوي سجلات ناقصة أو غير صالحة. لم نفعّل التحكم حتى تصل قائمة قابلة للتحقق.";
            case AMBIGUOUS_FORMAT: return "نجح تسجيل الدخول، لكن الرد يحتوي أكثر من قائمة محتملة. تعذر اختيار قائمة سيارات موثوقة.";
            case SERVER_REJECTED: return "نجح تسجيل الدخول، لكن رد قائمة السيارات يتضمن رفضاً من الخادم. هذا لا يثبت أن الحساب بلا سيارة.";
            default: return "نجح تسجيل الدخول، لكن صيغة رد قائمة السيارات غير مدعومة. هذا لا يعني أن الحساب بلا سيارة.";
        }
    }
}
