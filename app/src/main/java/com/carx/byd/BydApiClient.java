package com.carx.byd;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BydApiClient {
    private static final String APP_NAME = "pyBYD+0.0.76";
    private static final SecureRandom RNG = new SecureRandom();

    private final BydConfig config;
    private final BangcleCodec codec;
    private BydSession session;

    public BydApiClient(Context context, BydConfig config) {
        this.config = config;
        this.codec = new BangcleCodec(context);
    }

    public void prepare(BangcleCodec.Progress progress) throws Exception { codec.ensureTables(progress); }
    public BydSession getSession() { return session; }

    public BydSession login() throws Exception {
        LinkedHashMap<String,Object> outer = buildLoginRequest();
        JSONObject response = postSecure("/app/account/login", outer);
        ensureCodeOk("/app/account/login", response);
        String respondData = response.optString("respondData", "");
        if (respondData.isEmpty()) throw new BydException("BYD login response missing respondData");
        String plain = BydCrypto.aesDecryptUtf8(respondData, BydCrypto.pwdLoginKey(config.password));
        JSONObject inner = new JSONObject(plain);
        JSONObject token = inner.optJSONObject("token");
        if (token == null) throw new BydException("BYD login response missing token");
        String userId = token.optString("userId", "");
        String signToken = token.optString("signToken", "");
        String encryToken = token.optString("encryToken", "");
        if (userId.isEmpty() || signToken.isEmpty() || encryToken.isEmpty())
            throw new BydException("BYD login token is incomplete");
        session = new BydSession(userId, signToken, encryToken);
        return session;
    }

    public JSONArray getVehicles() throws Exception {
        Object decoded = postTokenJson("/app/account/getAllListByUserId", buildInnerBase(null, null), null);
        return decoded instanceof JSONArray ? (JSONArray) decoded : new JSONArray();
    }

    public JSONObject getRealtime(String vin, int energyType) throws Exception {
        LinkedHashMap<String,String> trigger = buildInnerBase(vin, null);
        trigger.put("energyType", String.valueOf(energyType));
        trigger.put("tboxVersion", config.tboxVersion);
        Object first = postTokenJson("/vehicleInfo/vehicle/vehicleRealTimeRequest", trigger, vin);
        JSONObject latest = first instanceof JSONObject ? (JSONObject) first : new JSONObject();
        String serial = latest.optString("requestSerial", "");
        if (looksLikeRealtime(latest)) return latest;
        for (int i = 0; i < 7 && !serial.isEmpty(); i++) {
            Thread.sleep(1500);
            LinkedHashMap<String,String> poll = buildInnerBase(vin, serial);
            poll.put("energyType", String.valueOf(energyType));
            poll.put("tboxVersion", config.tboxVersion);
            Object o = postTokenJson("/vehicleInfo/vehicle/vehicleRealTimeResult", poll, vin);
            if (o instanceof JSONObject) {
                latest = (JSONObject) o;
                serial = latest.optString("requestSerial", serial);
                if (looksLikeRealtime(latest)) break;
            }
        }
        return latest;
    }

    public boolean verifyControlPin(String vin) throws Exception {
        if (config.controlPin == null || config.controlPin.length() != 6) throw new BydException("أدخل PIN التحكم المكوّن من 6 أرقام");
        LinkedHashMap<String,String> inner = buildInnerBase(vin, null);
        inner.put("commandPwd", BydCrypto.md5Hex(config.controlPin));
        inner.put("functionType", "remoteControl");
        Object decoded = postTokenJson("/vehicle/vehicleswitch/verifyControlPassword", inner, vin);
        if (decoded instanceof JSONObject) {
            JSONObject o = (JSONObject) decoded;
            // Different regions return different success shapes; no exception + non-failure is accepted.
            String result = o.optString("result", "");
            String code = o.optString("code", "");
            return !"false".equalsIgnoreCase(result) && !"0".equals(result) && !"FAIL".equalsIgnoreCase(result) && !"2".equals(code);
        }
        return true;
    }

    public JSONObject remoteCommand(String vin, String commandType) throws Exception {
        if (config.controlPin == null || config.controlPin.length() != 6) throw new BydException("PIN التحكم غير مضبوط");
        String pwd = BydCrypto.md5Hex(config.controlPin);
        LinkedHashMap<String,String> inner = buildControlInner(vin, commandType, pwd, null, null);
        Object first = postTokenJson("/control/remoteControl", inner, vin);
        JSONObject latest = first instanceof JSONObject ? (JSONObject) first : new JSONObject();
        if (isTerminalControl(latest)) return latest;
        String serial = latest.optString("requestSerial", "");
        if (serial.isEmpty()) throw new BydException("تم إرسال الأمر لكن BYD لم يُرجع رقم متابعة");
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1500);
            LinkedHashMap<String,String> poll = buildControlInner(vin, commandType, "", null, serial);
            Object result = postTokenJson("/control/remoteControlResult", poll, vin);
            if (result instanceof JSONObject) {
                latest = (JSONObject) result;
                serial = latest.optString("requestSerial", serial);
                if (isTerminalControl(latest)) return latest;
            }
        }
        return latest;
    }

    public JSONObject remoteClimate(String vin, boolean on, double temperature, int minutes) throws Exception {
        if (config.controlPin == null || config.controlPin.length() != 6) throw new BydException("PIN التحكم غير مضبوط");
        String command = on ? "OPENAIR" : "CLOSEAIR";
        String pwd = BydCrypto.md5Hex(config.controlPin);
        LinkedHashMap<String,Object> params = new LinkedHashMap<>();
        if (on) {
            int tempScale = Math.max(1, Math.min(17, (int)Math.round(temperature - 14.0)));
            int timeSpan = minutes == 10 ? 1 : minutes == 15 ? 2 : minutes == 20 ? 3 : minutes == 25 ? 4 : 5;
            params.put("cycleMode", 2);
            params.put("timeSpan", timeSpan);
            params.put("remoteMode", 4);
            params.put("airAccuracy", 1);
            params.put("airConditioningMode", 1);
            params.put("mainSettingTemp", tempScale);
            params.put("copilotSettingTemp", tempScale);
            params.put("airSet", null);
        }
        LinkedHashMap<String,String> inner = buildControlInner(vin, command, pwd, params, null);
        Object first = postTokenJson("/control/remoteControl", inner, vin);
        JSONObject latest = first instanceof JSONObject ? (JSONObject) first : new JSONObject();
        if (isTerminalControl(latest)) return latest;
        String serial = latest.optString("requestSerial", "");
        for (int i=0; i<10 && !serial.isEmpty(); i++) {
            Thread.sleep(1500);
            LinkedHashMap<String,String> poll = buildControlInner(vin, command, "", null, serial);
            Object o = postTokenJson("/control/remoteControlResult", poll, vin);
            if (o instanceof JSONObject) { latest=(JSONObject)o; if(isTerminalControl(latest)) break; }
        }
        return latest;
    }

    private LinkedHashMap<String,Object> buildLoginRequest() {
        long now = System.currentTimeMillis();
        String ts = String.valueOf(now);
        String random = randomHex(16);
        LinkedHashMap<String,Object> inner = new LinkedHashMap<>();
        inner.put("agreeStatus", "0");
        inner.put("agreementType", "[1,2]");
        inner.put("appInnerVersion", config.appInnerVersion);
        inner.put("appVersion", config.appVersion);
        inner.put("deviceName", config.mobileBrand + config.mobileModel);
        inner.put("deviceType", config.deviceType);
        inner.put("imeiMD5", config.imeiMd5());
        inner.put("isAuto", config.isAuto);
        inner.put("mobileBrand", config.mobileBrand);
        inner.put("mobileModel", config.mobileModel);
        inner.put("networkType", config.networkType);
        inner.put("osType", config.osType);
        inner.put("osVersion", config.osVersion);
        inner.put("random", random);
        inner.put("softType", config.softType);
        inner.put("timeStamp", ts);
        inner.put("timeZone", config.region.timeZone);

        String encryData = BydCrypto.aesEncryptHex(JsonUtil.stringify(inner), BydCrypto.pwdLoginKey(config.password));
        LinkedHashMap<String,String> signFields = new LinkedHashMap<>();
        for (Map.Entry<String,Object> e : inner.entrySet()) signFields.put(e.getKey(), String.valueOf(e.getValue()));
        signFields.put("appName", APP_NAME);
        signFields.put("countryCode", config.region.countryCode);
        signFields.put("functionType", "pwdLogin");
        signFields.put("identifier", config.username);
        signFields.put("identifierType", config.identifierType);
        signFields.put("language", config.region.language);
        signFields.put("reqTimestamp", ts);
        String sign = BydCrypto.sha1Mixed(BydCrypto.buildSignString(signFields, BydCrypto.md5Hex(config.password)));

        LinkedHashMap<String,Object> outer = new LinkedHashMap<>();
        outer.put("appName", APP_NAME);
        outer.put("countryCode", config.region.countryCode);
        outer.put("encryData", encryData);
        outer.put("functionType", "pwdLogin");
        outer.put("identifier", config.username);
        outer.put("identifierType", config.identifierType);
        outer.put("imeiMD5", config.imeiMd5());
        outer.put("isAuto", config.isAuto);
        outer.put("language", config.region.language);
        outer.put("reqTimestamp", ts);
        outer.put("sign", sign);
        outer.put("signKey", config.password);
        outer.put("ostype", config.ostype);
        outer.put("imei", config.imei);
        outer.put("mac", config.mac);
        outer.put("model", config.model);
        outer.put("sdk", config.sdk);
        outer.put("mod", config.mod);
        outer.put("serviceTime", String.valueOf(System.currentTimeMillis()));
        outer.put("checkcode", BydCrypto.computeCheckcode(outer));
        return outer;
    }

    private LinkedHashMap<String,String> buildInnerBase(String vin, String requestSerial) {
        LinkedHashMap<String,String> inner = new LinkedHashMap<>();
        inner.put("deviceType", config.deviceType);
        inner.put("imeiMD5", config.imeiMd5());
        inner.put("networkType", config.networkType);
        inner.put("random", randomHex(16));
        inner.put("timeStamp", String.valueOf(System.currentTimeMillis()));
        inner.put("version", config.appInnerVersion);
        if (vin != null && !vin.isEmpty()) inner.put("vin", vin);
        if (requestSerial != null && !requestSerial.isEmpty()) inner.put("requestSerial", requestSerial);
        return inner;
    }

    private LinkedHashMap<String,String> buildControlInner(String vin, String commandType, String commandPwd, Map<String,Object> params, String serial) {
        LinkedHashMap<String,String> inner = buildInnerBase(vin, serial);
        inner.put("commandPwd", commandPwd == null ? "" : commandPwd);
        inner.put("commandType", commandType);
        if (params != null) inner.put("controlParamsMap", JsonUtil.stringify(params));
        return inner;
    }

    private Object postTokenJson(String endpoint, LinkedHashMap<String,String> inner, String vin) throws Exception {
        return postTokenJson(endpoint, inner, vin, true);
    }

    private Object postTokenJson(String endpoint, LinkedHashMap<String,String> inner, String vin, boolean allowReauth) throws Exception {
        if (session == null || session.isExpired()) login();
        long now = System.currentTimeMillis();
        String reqTs = String.valueOf(now);
        String contentKey = session.contentKey();
        String signKey = session.signKey();
        String encryData = BydCrypto.aesEncryptHex(JsonUtil.stringify(inner), contentKey);

        LinkedHashMap<String,String> signFields = new LinkedHashMap<>(inner);
        signFields.put("countryCode", config.region.countryCode);
        signFields.put("identifier", session.userId);
        signFields.put("imeiMD5", config.imeiMd5());
        signFields.put("language", config.region.language);
        signFields.put("reqTimestamp", reqTs);
        String sign = BydCrypto.sha1Mixed(BydCrypto.buildSignString(signFields, signKey));

        LinkedHashMap<String,Object> outer = new LinkedHashMap<>();
        outer.put("countryCode", config.region.countryCode);
        outer.put("encryData", encryData);
        outer.put("identifier", session.userId);
        outer.put("imeiMD5", config.imeiMd5());
        outer.put("language", config.region.language);
        outer.put("reqTimestamp", reqTs);
        outer.put("sign", sign);
        outer.put("ostype", config.ostype);
        outer.put("imei", config.imei);
        outer.put("mac", config.mac);
        outer.put("model", config.model);
        outer.put("sdk", config.sdk);
        outer.put("mod", config.mod);
        outer.put("serviceTime", String.valueOf(System.currentTimeMillis()));
        outer.put("checkcode", BydCrypto.computeCheckcode(outer));

        JSONObject response = postSecure(endpoint, outer);
        String code = response.optString("code", "");
        if (!"0".equals(code)) {
            if (allowReauth && ("1002".equals(code) || "1005".equals(code) || "1010".equals(code))) {
                session = null;
                login();
                return postTokenJson(endpoint, inner, vin, false);
            }
            throw new BydException("BYD " + code + ": " + response.optString("message", "API error"));
        }
        String respondData = response.optString("respondData", "");
        if (respondData.isEmpty()) return new JSONObject();
        String plain = BydCrypto.aesDecryptUtf8(respondData, contentKey).trim();
        if (plain.isEmpty()) return new JSONObject();
        return new JSONTokener(plain).nextValue();
    }

    private JSONObject postSecure(String endpoint, LinkedHashMap<String,Object> outer) throws Exception {
        String encoded = codec.encodeEnvelope(JsonUtil.stringify(outer));
        LinkedHashMap<String,Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("request", encoded);
        byte[] body = JsonUtil.stringify(bodyMap).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(config.region.baseUrl + endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20_000);
        c.setReadTimeout(35_000);
        c.setRequestProperty("accept-encoding", "identity");
        c.setRequestProperty("content-type", "application/json; charset=UTF-8");
        c.setRequestProperty("user-agent", "okhttp/4.12.0");
        try {
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? c.getInputStream() : c.getErrorStream();
            String text = readText(stream);
            if (status != 200) throw new BydException("HTTP " + status + " من BYD: " + trim(text, 180));
            JSONObject wrapper = new JSONObject(text);
            String responseEnvelope = wrapper.optString("response", "");
            if (responseEnvelope.isEmpty()) throw new BydException("BYD response missing envelope");
            String decoded = codec.decodeEnvelope(responseEnvelope).trim();
            if (decoded.startsWith("F{") || decoded.startsWith("F[")) decoded = decoded.substring(1);
            return new JSONObject(decoded);
        } finally { c.disconnect(); }
    }

    private static void ensureCodeOk(String endpoint, JSONObject response) throws BydException {
        if (!"0".equals(response.optString("code", "")))
            throw new BydException(endpoint + " → " + response.optString("code") + " " + response.optString("message"));
    }

    private static boolean looksLikeRealtime(JSONObject o) {
        return o.has("elecPercent") || o.has("enduranceMileage") || o.has("totalMileage") || o.has("vehicleState") || o.has("speed");
    }

    private static boolean isTerminalControl(JSONObject o) {
        if (o.has("controlState")) return o.optInt("controlState", 0) != 0;
        if (o.has("res")) return o.optInt("res", 0) >= 2;
        return o.has("result");
    }

    private static String randomHex(int n) {
        byte[] b = new byte[n]; RNG.nextBytes(b); return BydCrypto.bytesToHex(b, true);
    }

    private static String readText(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }
    private static String trim(String s, int n) { return s == null ? "" : (s.length() <= n ? s : s.substring(0,n)); }

    public static String value(JSONObject o, String... names) {
        for (String n : names) if (o.has(n) && !o.isNull(n)) return String.valueOf(o.opt(n));
        return "—";
    }

    public static int energyType(JSONObject vehicle) {
        Object v = vehicle.opt("energyType");
        if (v == null) v = vehicle.opt("energy_type");
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    public static final class BydException extends Exception {
        public BydException(String message) { super(message); }
    }
}
