package com.carx.byd;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BydApiClient {
    private static final String APP_NAME = "pyBYD+0.0.76";
    private static final SecureRandom RNG = new SecureRandom();

    private final BydConfig config;
    private final BangcleCodec bangcle;
    private final WbskCodec wbsk;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private BydSession session;

    public BydApiClient(Context context, BydConfig config) {
        this.config = config;
        this.bangcle = config.isChina() ? null : new BangcleCodec(context);
        this.wbsk = config.isChina() ? new WbskCodec(context) : null;
    }

    public void prepare(BangcleCodec.Progress progress) throws Exception {
        if (config.isChina()) {
            wbsk.ensureTables(progress == null ? null : progress::onProgress);
            if (progress != null) progress.onProgress("فحص محرك التشفير الصيني بدون إرسال بيانات…");
            wbsk.selfTest();
        } else {
            bangcle.ensureTables(progress);
        }
    }

    public BydSession getSession() { return session; }

    public BydSession login() throws Exception {
        return config.isChina() ? loginChina() : loginOverseas();
    }

    private BydSession loginOverseas() throws Exception {
        JSONObject response = postSecure("/app/account/login", buildOverseasLoginRequest());
        ensureCodeOk("/app/account/login", response);
        JSONObject token = decryptLoginToken(response);
        String userId=token.optString("userId","");
        String signToken=token.optString("signToken","");
        String encryToken=token.optString("encryToken", token.optString("encryptToken",""));
        if(userId.isEmpty()||signToken.isEmpty()||encryToken.isEmpty()) throw new BydException("BYD login token is incomplete");
        session=new BydSession(userId,signToken,encryToken);
        return session;
    }

    private BydSession loginChina() throws Exception {
        JSONObject response = postSecure("/app/auth/login", buildChinaLoginRequest());
        ensureCodeOk("/app/auth/login", response);
        JSONObject token = decryptLoginToken(response);
        String signToken=token.optString("signToken","");
        String encryToken=token.optString("encryToken", token.optString("encryptToken",""));
        String superId=token.optString("superId","");
        String brandUserId="";
        JSONObject rel=token.optJSONObject("superBindRelationDtoMap");
        if(rel!=null){ JSONObject b=rel.optJSONObject(config.targetBrand); if(b!=null) brandUserId=b.optString("userId",""); }
        String userId=!brandUserId.isEmpty()?brandUserId:superId;
        if(userId.isEmpty()||signToken.isEmpty()||encryToken.isEmpty()) throw new BydException("BYD China login token is incomplete");
        session=new BydSession(userId,superId,signToken,encryToken);
        return session;
    }

    private JSONObject decryptLoginToken(JSONObject response) throws Exception {
        String rd=response.optString("respondData","");
        if(rd.isEmpty()) throw new BydException("BYD login response missing respondData");
        String plain=BydCrypto.aesDecryptUtf8(rd,BydCrypto.pwdLoginKey(config.password));
        JSONObject inner=new JSONObject(plain);
        JSONObject token=inner.optJSONObject("token");
        if(token==null) throw new BydException("BYD login response missing token");
        return token;
    }

    public JSONArray getVehicles() throws Exception {
        LinkedHashMap<String,String> inner=config.isChina()?buildChinaVehicleListInner():buildInnerBase(null,null);
        String endpoint=config.isChina()?"/app/auth/getAllListByUserId":"/app/account/getAllListByUserId";
        Object decoded=postTokenJson(endpoint,inner,null);
        if(decoded instanceof JSONArray) return (JSONArray)decoded;
        if(decoded instanceof JSONObject){ JSONArray a=((JSONObject)decoded).optJSONArray("diLinkAutoInfoList"); if(a!=null)return a; }
        return new JSONArray();
    }


    /**
     * BYD China QR login flow. The endpoint names and scan fields are taken from
     * the working mainland BYD 9.16.1 app. This call only authorizes/inspects the
     * scanned QR; the user is asked for confirmation before scanLoginByAction.
     */
    public JSONObject scanLoginByAuth(String qrRaw) throws Exception {
        if(!config.isChina()) throw new BydException("مسح QR الخاص بالسيارة متاح لحساب BYD China فقط");
        QrPayload q=QrPayload.parse(qrRaw);
        if(q.raw.isEmpty()) throw new BydException("QR فارغ");
        LinkedHashMap<String,String> inner=buildInnerBase(null,null);
        inner.put("scanUrl",q.raw);
        if(!q.scanId.isEmpty()) inner.put("scanId",q.scanId);
        if(!q.innerToken.isEmpty()) inner.put("scanInnerToken",q.innerToken);
        if(!q.vkey256.isEmpty()) inner.put("vkey256",q.vkey256);
        if(!q.vin.isEmpty()) inner.put("vin",q.vin);
        Object decoded=postTokenJson("/user/scanlogin/scanLoginByAuth",inner,null);
        return asJsonObject(decoded);
    }

    public JSONObject scanLoginByAction(String scanId,String vin) throws Exception {
        if(scanId==null||scanId.trim().isEmpty()) throw new BydException("لم يرجع BYD رقم جلسة QR");
        LinkedHashMap<String,String> inner=buildInnerBase(null,null);
        inner.put("scanId",scanId.trim());
        // BYD uses a dedicated Action endpoint for the positive confirmation.
        // The separate scanLoginCancel endpoint handles rejection/cancel.
        if(vin!=null&&!vin.isEmpty()) inner.put("vin",vin);
        Object decoded=postTokenJson("/user/scanlogin/scanLoginByAction",inner,null);
        return asJsonObject(decoded);
    }

    public JSONObject scanLoginCancel(String scanId) throws Exception {
        LinkedHashMap<String,String> inner=buildInnerBase(null,null);
        if(scanId!=null&&!scanId.trim().isEmpty()) inner.put("scanId",scanId.trim());
        Object decoded=postTokenJson("/user/scanlogin/scanLoginCancel",inner,null);
        return asJsonObject(decoded);
    }

    private static JSONObject asJsonObject(Object decoded) throws Exception {
        if(decoded instanceof JSONObject) return (JSONObject)decoded;
        if(decoded instanceof JSONArray){JSONObject o=new JSONObject();o.put("items",decoded);return o;}
        JSONObject o=new JSONObject();if(decoded!=null)o.put("value",String.valueOf(decoded));return o;
    }

    public JSONObject getRealtime(String vin,int energyType)throws Exception{
        LinkedHashMap<String,String> trigger=buildInnerBase(vin,null);
        trigger.put("energyType",String.valueOf(energyType)); trigger.put("tboxVersion",config.tboxVersion);
        Object first=postTokenJson("/vehicleInfo/vehicle/vehicleRealTimeRequest",trigger,vin);
        JSONObject latest=first instanceof JSONObject?(JSONObject)first:new JSONObject(); String serial=latest.optString("requestSerial","");
        if(looksLikeRealtime(latest))return latest;
        for(int i=0;i<7&&!serial.isEmpty();i++){
            Thread.sleep(1500); LinkedHashMap<String,String> poll=buildInnerBase(vin,serial); poll.put("energyType",String.valueOf(energyType)); poll.put("tboxVersion",config.tboxVersion);
            Object o=postTokenJson("/vehicleInfo/vehicle/vehicleRealTimeResult",poll,vin); if(o instanceof JSONObject){latest=(JSONObject)o;serial=latest.optString("requestSerial",serial);if(looksLikeRealtime(latest))break;}
        }
        return latest;
    }

    public boolean verifyControlPin(String vin)throws Exception{
        if(config.controlPin.length()!=6)throw new BydException("أدخل PIN التحكم المكوّن من 6 أرقام");
        LinkedHashMap<String,String> inner=buildInnerBase(vin,null); inner.put("commandPwd",BydCrypto.md5Hex(config.controlPin)); inner.put("functionType","remoteControl");
        Object decoded=postTokenJson("/vehicle/vehicleswitch/verifyControlPassword",inner,vin);
        if(decoded instanceof JSONObject){JSONObject o=(JSONObject)decoded;String result=o.optString("result","");String code=o.optString("code","");return !"false".equalsIgnoreCase(result)&&!"0".equals(result)&&!"FAIL".equalsIgnoreCase(result)&&!"2".equals(code);} return true;
    }

    public JSONObject remoteCommand(String vin,String commandType)throws Exception{
        if(config.controlPin.length()!=6)throw new BydException("PIN التحكم غير مضبوط");
        String pwd=BydCrypto.md5Hex(config.controlPin); LinkedHashMap<String,String> inner=buildControlInner(vin,commandType,pwd,null,null);
        Object first=postTokenJson("/control/remoteControl",inner,vin); JSONObject latest=first instanceof JSONObject?(JSONObject)first:new JSONObject(); if(isTerminalControl(latest))return latest;
        String serial=latest.optString("requestSerial",""); if(serial.isEmpty())throw new BydException("تم إرسال الأمر لكن BYD لم يُرجع رقم متابعة");
        for(int i=0;i<10;i++){Thread.sleep(1500);Object r=postTokenJson("/control/remoteControlResult",buildControlInner(vin,commandType,"",null,serial),vin);if(r instanceof JSONObject){latest=(JSONObject)r;serial=latest.optString("requestSerial",serial);if(isTerminalControl(latest))return latest;}}
        return latest;
    }

    public JSONObject remoteClimate(String vin,boolean on,double temperature,int minutes)throws Exception{
        if(config.controlPin.length()!=6)throw new BydException("PIN التحكم غير مضبوط");
        String cmd=on?"OPENAIR":"CLOSEAIR",pwd=BydCrypto.md5Hex(config.controlPin); LinkedHashMap<String,Object> params=new LinkedHashMap<>();
        if(on){int t=Math.max(1,Math.min(17,(int)Math.round(temperature-14.0)));int span=minutes==10?1:minutes==15?2:minutes==20?3:minutes==25?4:5;params.put("cycleMode",2);params.put("timeSpan",span);params.put("remoteMode",4);params.put("airAccuracy",1);params.put("airConditioningMode",1);params.put("mainSettingTemp",t);params.put("copilotSettingTemp",t);params.put("airSet",null);}
        Object first=postTokenJson("/control/remoteControl",buildControlInner(vin,cmd,pwd,params,null),vin);JSONObject latest=first instanceof JSONObject?(JSONObject)first:new JSONObject();if(isTerminalControl(latest))return latest;
        String serial=latest.optString("requestSerial","");for(int i=0;i<10&&!serial.isEmpty();i++){Thread.sleep(1500);Object o=postTokenJson("/control/remoteControlResult",buildControlInner(vin,cmd,"",null,serial),vin);if(o instanceof JSONObject){latest=(JSONObject)o;if(isTerminalControl(latest))break;}}return latest;
    }

    private LinkedHashMap<String,Object> buildOverseasLoginRequest(){
        String ts=String.valueOf(System.currentTimeMillis()),random=randomHex(16);LinkedHashMap<String,Object> inner=new LinkedHashMap<>();
        inner.put("agreeStatus","0");inner.put("agreementType","[1,2]");inner.put("appInnerVersion",config.appInnerVersion);inner.put("appVersion",config.appVersion);inner.put("deviceName",config.mobileBrand+config.mobileModel);inner.put("deviceType",config.deviceType);inner.put("imeiMD5",config.imeiMd5());inner.put("isAuto",config.isAuto);inner.put("mobileBrand",config.mobileBrand);inner.put("mobileModel",config.mobileModel);inner.put("networkType",config.networkType);inner.put("osType",config.osType);inner.put("osVersion",config.osVersion);inner.put("random",random);inner.put("softType",config.softType);inner.put("timeStamp",ts);inner.put("timeZone",config.region.timeZone);
        String enc=BydCrypto.aesEncryptHex(JsonUtil.stringify(inner),BydCrypto.pwdLoginKey(config.password));LinkedHashMap<String,String> sf=stringify(inner);sf.put("appName",APP_NAME);sf.put("countryCode",config.region.countryCode);sf.put("functionType","pwdLogin");sf.put("identifier",config.username);sf.put("identifierType",config.identifierType());sf.put("language",config.region.language);sf.put("reqTimestamp",ts);String sign=BydCrypto.sha1Mixed(BydCrypto.buildSignString(sf,BydCrypto.md5Hex(config.password)));
        LinkedHashMap<String,Object> o=new LinkedHashMap<>();o.put("appName",APP_NAME);o.put("countryCode",config.region.countryCode);o.put("encryData",enc);o.put("functionType","pwdLogin");o.put("identifier",config.username);o.put("identifierType",config.identifierType());o.put("imeiMD5",config.imeiMd5());o.put("isAuto",config.isAuto);o.put("language",config.region.language);o.put("reqTimestamp",ts);o.put("sign",sign);o.put("signKey",config.password);addOverseasDevice(o);o.put("checkcode",BydCrypto.computeCheckcode(o));return o;
    }

    private LinkedHashMap<String,Object> buildChinaLoginRequest(){
        String ts=String.valueOf(System.currentTimeMillis()),random=randomHex(16);LinkedHashMap<String,Object> inner=new LinkedHashMap<>();
        inner.put("appInnerVersion",config.cnAppInnerVersion);inner.put("appVersion",config.cnAppVersion);inner.put("bluetoothMac","");inner.put("city","");inner.put("configVersion","10000");inner.put("deviceType",config.deviceType);inner.put("devicename",config.mobileBrand+config.mobileModel);inner.put("imeiMD5",config.imeiMd5());inner.put("isAuto","0");inner.put("latitude","");inner.put("longitude","");inner.put("mobileBrand",config.mobileBrand);inner.put("mobileModel",config.mobileModel);inner.put("networkOperator",config.networkOperator);inner.put("networkType",config.networkType);inner.put("osType","Android");inner.put("osVersion",config.osType);inner.put("random",random);inner.put("softType",config.softType);inner.put("timeStamp",ts);
        String enc=BydCrypto.aesEncryptHex(JsonUtil.stringify(inner),BydCrypto.pwdLoginKey(config.password));LinkedHashMap<String,String> sf=stringify(inner);sf.put("appChannel",config.appChannel);sf.put("identifier",config.username);sf.put("loginType","0");sf.put("reqTimestamp",ts);sf.put("targetBrand",config.targetBrand);String sign=BydCrypto.sha1Mixed(BydCrypto.buildSignString(sf,BydCrypto.md5Hex(config.password)));
        LinkedHashMap<String,Object> o=new LinkedHashMap<>();o.put("appChannel",config.appChannel);o.put("encryData",enc);o.put("identifier",config.username);o.put("imeiMD5",config.imeiMd5());o.put("isAuto","0");o.put("loginType",0);o.put("reqTimestamp",ts);o.put("sign",sign);o.put("targetBrand",config.targetBrand);addCnDevice(o);o.put("checkcode",BydCrypto.computeCnCheckcode(o));return o;
    }

    private LinkedHashMap<String,String> buildInnerBase(String vin,String serial){
        LinkedHashMap<String,String> i=new LinkedHashMap<>();
        if(config.isChina()){
            i.put("deviceName",config.mobileBrand+config.mobileModel);i.put("deviceType",config.deviceType);i.put("imeiMD5",config.imeiMd5());i.put("mobileBrand",config.mobileBrand);i.put("mobileModel",config.mobileModel);i.put("networkOperator",config.networkOperator);i.put("networkType",config.networkType);i.put("osType","Android");i.put("osVersion",config.osVersion);i.put("random",randomHex(16));i.put("softType",config.softType);i.put("timeStamp",String.valueOf(System.currentTimeMillis()));i.put("version",config.cnAppInnerVersion);
        }else{
            i.put("deviceType",config.deviceType);i.put("imeiMD5",config.imeiMd5());i.put("networkType",config.networkType);i.put("random",randomHex(16));i.put("timeStamp",String.valueOf(System.currentTimeMillis()));i.put("version",config.appInnerVersion);
        }
        if(vin!=null&&!vin.isEmpty())i.put("vin",vin);if(serial!=null&&!serial.isEmpty())i.put("requestSerial",serial);return i;
    }

    private LinkedHashMap<String,String> buildChinaVehicleListInner(){
        // Match BYD China reference insertion order: appUiName first, then the common inner fields.
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        out.put("appUiName","");
        out.putAll(buildInnerBase(null,null));
        return out;
    }

    private LinkedHashMap<String,String> buildControlInner(String vin,String type,String pwd,Map<String,Object> params,String serial){LinkedHashMap<String,String> i=buildInnerBase(vin,serial);i.put("commandPwd",pwd==null?"":pwd);i.put("commandType",type);if(params!=null)i.put("controlParamsMap",JsonUtil.stringify(params));return i;}

    private Object postTokenJson(String endpoint,LinkedHashMap<String,String> inner,String vin)throws Exception{return postTokenJson(endpoint,inner,vin,true);}
    private Object postTokenJson(String endpoint,LinkedHashMap<String,String> inner,String vin,boolean allowReauth)throws Exception{
        if(session==null||session.isExpired())login();String ts=String.valueOf(System.currentTimeMillis()),contentKey=session.contentKey(),signKey=session.signKey();String enc=BydCrypto.aesEncryptHex(JsonUtil.stringify(inner),contentKey);LinkedHashMap<String,Object> o=new LinkedHashMap<>();
        if(config.isChina()){
            String id=session.apiIdentifier();int idType=(vin!=null&&!vin.isEmpty())?0:2;LinkedHashMap<String,String> sf=new LinkedHashMap<>(inner);sf.put("appChannel",config.appChannel);sf.put("identifier",id);sf.put("identifierType",String.valueOf(idType));sf.put("imeiMD5",config.imeiMd5());sf.put("reqTimestamp",ts);sf.put("targetBrand",config.targetBrand);sf.put("vehicleBrand",config.vehicleBrand);if(vin!=null&&!vin.isEmpty())sf.put("objective",vin);String sign=BydCrypto.sha1Mixed(BydCrypto.buildSignString(sf,signKey));
            o.put("appChannel",config.appChannel);o.put("encryData",enc);o.put("identifier",id);o.put("identifierType",idType);o.put("imeiMD5",config.imeiMd5());o.put("objective",vin);o.put("outModelTypes",null);o.put("reqTimestamp",ts);o.put("sign",sign);o.put("softType",null);o.put("targetBrand",config.targetBrand);o.put("vehicleBrand",config.vehicleBrand);o.put("version",null);addCnDevice(o);o.put("checkcode",BydCrypto.computeCnCheckcode(o));
        }else{
            LinkedHashMap<String,String> sf=new LinkedHashMap<>(inner);sf.put("countryCode",config.region.countryCode);sf.put("identifier",session.userId);sf.put("imeiMD5",config.imeiMd5());sf.put("language",config.region.language);sf.put("reqTimestamp",ts);String sign=BydCrypto.sha1Mixed(BydCrypto.buildSignString(sf,signKey));
            o.put("countryCode",config.region.countryCode);o.put("encryData",enc);o.put("identifier",session.userId);o.put("imeiMD5",config.imeiMd5());o.put("language",config.region.language);o.put("reqTimestamp",ts);o.put("sign",sign);addOverseasDevice(o);o.put("checkcode",BydCrypto.computeCheckcode(o));
        }
        JSONObject response=postSecure(endpoint,o);String code=response.optString("code","");if(!"0".equals(code)){if(allowReauth&&("1002".equals(code)||"1005".equals(code)||"1010".equals(code))){session=null;login();return postTokenJson(endpoint,inner,vin,false);}throw new BydException("BYD "+code+": "+response.optString("message","API error"));}
        String rd=response.optString("respondData","");if(rd.isEmpty())return new JSONObject();String plain=BydCrypto.aesDecryptUtf8(rd,contentKey).trim();if(plain.isEmpty())return new JSONObject();return new JSONTokener(plain).nextValue();
    }

    private JSONObject postSecure(String endpoint,LinkedHashMap<String,Object> outer)throws Exception{
        String encoded=config.isChina()?wbsk.encodeEnvelope(JsonUtil.stringify(outer)):bangcle.encodeEnvelope(JsonUtil.stringify(outer));LinkedHashMap<String,Object> bodyMap=new LinkedHashMap<>();bodyMap.put("request",encoded);byte[] body=JsonUtil.stringify(bodyMap).getBytes(StandardCharsets.UTF_8);URL url=new URL(config.region.baseUrl+endpoint);URI uri=url.toURI();HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20_000);c.setReadTimeout(35_000);c.setRequestProperty("accept-encoding","identity");c.setRequestProperty("content-type","application/json; charset=UTF-8");c.setRequestProperty("user-agent","okhttp/4.12.0");if(config.isChina()){c.setRequestProperty("version",config.cnAppInnerVersion);c.setRequestProperty("platform","ANDROID");c.setRequestProperty("BrandFlag",config.brandFlag);}for(Map.Entry<String,List<String>> e:cookies.get(uri,Collections.emptyMap()).entrySet())for(String v:e.getValue())c.addRequestProperty(e.getKey(),v);
        try{try(OutputStream out=c.getOutputStream()){out.write(body);}int status=c.getResponseCode();cookies.put(uri,c.getHeaderFields());InputStream stream=status>=200&&status<400?c.getInputStream():c.getErrorStream();String text=readText(stream);if(status!=200)throw new BydException("HTTP "+status+" من BYD: "+trim(text,180));JSONObject wrapper=new JSONObject(text);String env=wrapper.optString("response","");if(env.isEmpty())throw new BydException("BYD response missing envelope");String decoded=config.isChina()?wbsk.decodeEnvelope(env).trim():bangcle.decodeEnvelope(env).trim();if(decoded.startsWith("F{")||decoded.startsWith("F["))decoded=decoded.substring(1);return new JSONObject(decoded);}finally{c.disconnect();}
    }

    private void addOverseasDevice(LinkedHashMap<String,Object> o){
        o.put("ostype",config.ostype);o.put("imei",config.imei);o.put("mac",config.mac);o.put("model",config.model);o.put("sdk",config.sdk);o.put("mod",config.mod);o.put("serviceTime",String.valueOf(System.currentTimeMillis()));
    }

    private void addCnDevice(LinkedHashMap<String,Object> o){
        // CN SHA-256 checkcode is over JSON text, so insertion order must mirror the reference app.
        o.put("ostype",config.ostype);o.put("imei",config.imei);o.put("mac",config.mac);o.put("model",config.model);o.put("sdk",config.sdk);o.put("serviceTime",String.valueOf(System.currentTimeMillis()));o.put("mod",config.mod);
    }
    private static LinkedHashMap<String,String> stringify(Map<String,Object> m){LinkedHashMap<String,String> o=new LinkedHashMap<>();for(Map.Entry<String,Object> e:m.entrySet())o.put(e.getKey(),e.getValue()==null?null:String.valueOf(e.getValue()));return o;}
    private static void ensureCodeOk(String endpoint,JSONObject r)throws BydException{if(!"0".equals(r.optString("code","")))throw new BydException(endpoint+" → "+r.optString("code")+" "+r.optString("message"));}
    private static boolean looksLikeRealtime(JSONObject o){return o.has("elecPercent")||o.has("enduranceMileage")||o.has("totalMileage")||o.has("vehicleState")||o.has("speed");}
    private static boolean isTerminalControl(JSONObject o){if(o.has("controlState"))return o.optInt("controlState",0)!=0;if(o.has("res"))return o.optInt("res",0)>=2;return o.has("result");}
    private static String randomHex(int n){byte[] b=new byte[n];RNG.nextBytes(b);return BydCrypto.bytesToHex(b,true);}
    private static String readText(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
    private static String trim(String s,int n){return s==null?"":(s.length()<=n?s:s.substring(0,n));}

    public static String value(JSONObject o,String...names){for(String n:names)if(o.has(n)&&!o.isNull(n))return String.valueOf(o.opt(n));return"—";}
    public static int energyType(JSONObject v){Object o=v.opt("energyType");if(o==null)o=v.opt("energy_type");try{return Integer.parseInt(String.valueOf(o));}catch(Exception e){return 0;}}
    public static final class BydException extends Exception{public BydException(String m){super(m);}}
}
