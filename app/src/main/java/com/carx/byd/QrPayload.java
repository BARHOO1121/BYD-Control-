package com.carx.byd;

import android.net.Uri;
import org.json.JSONObject;

public final class QrPayload {
    public final String raw, scanId, innerToken, vkey256, vin;
    private QrPayload(String raw,String scanId,String innerToken,String vkey256,String vin){
        this.raw=raw;this.scanId=scanId;this.innerToken=innerToken;this.vkey256=vkey256;this.vin=vin;
    }
    public static QrPayload parse(String raw){
        String s=raw==null?"":raw.trim();
        String scanId="",token="",vkey="",vin="";
        try{
            if(s.startsWith("{") && s.endsWith("}")){
                JSONObject o=new JSONObject(s);
                scanId=first(o,"scanId","scan_id","id");
                token=first(o,"scanInnerToken","innerToken","token");
                vkey=first(o,"vkey256","vKey256","vkey");
                vin=first(o,"vin","carVin","vehicleVin");
            }
        }catch(Exception ignored){}
        try{
            Uri u=Uri.parse(s);
            if(scanId.isEmpty()) scanId=param(u,"scan_id","scanId","id");
            if(token.isEmpty()) token=param(u,"scanInnerToken","innerToken","token");
            if(vkey.isEmpty()) vkey=param(u,"vkey256","vKey256","vkey");
            if(vin.isEmpty()) vin=param(u,"vin","carVin","vehicleVin");
        }catch(Exception ignored){}
        return new QrPayload(s,scanId,token,vkey,vin);
    }
    private static String param(Uri u,String...keys){for(String k:keys){String v=u.getQueryParameter(k);if(v!=null&&!v.isEmpty())return v;}return"";}
    private static String first(JSONObject o,String...keys){for(String k:keys){String v=o.optString(k,"");if(!v.isEmpty())return v;}return"";}
}
