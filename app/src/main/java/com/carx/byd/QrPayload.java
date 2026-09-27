package com.carx.byd;

import android.net.Uri;
import org.json.JSONObject;

public final class QrPayload {
    public final String raw, scanId, scanIdent, innerToken, vkey256, tokenTime, vin, scanVinProvider;

    private QrPayload(String raw,String scanId,String scanIdent,String innerToken,String vkey256,String tokenTime,String vin,String scanVinProvider){
        this.raw=raw;
        this.scanId=scanId;
        this.scanIdent=scanIdent;
        this.innerToken=innerToken;
        this.vkey256=vkey256;
        this.tokenTime=tokenTime;
        this.vin=vin;
        this.scanVinProvider=scanVinProvider;
    }

    public static QrPayload parse(String raw){
        String s=raw==null?"":raw.trim();
        String scanId="",scanIdent="",token="",vkey="",tokenTime="",vin="",vinProvider="";
        try{
            if(s.startsWith("{") && s.endsWith("}")){
                JSONObject o=new JSONObject(s);
                scanId=first(o,"scanId","scan_id","id");
                scanIdent=first(o,"scanIdent","scan_ident","ident");
                token=first(o,"scanInnerToken","innerToken","token");
                vkey=first(o,"vkey256","vKey256","vkey","vKey");
                tokenTime=first(o,"tokenTime","token_time","timeStamp","timestamp");
                vin=first(o,"vin","carVin","vehicleVin","scanVin");
                vinProvider=first(o,"scanVinProvider","vinProvider");
            }
        }catch(Exception ignored){}
        try{
            Uri u=Uri.parse(s);
            if(scanId.isEmpty()) scanId=param(u,"scan_id","scanId","id");
            if(scanIdent.isEmpty()) scanIdent=param(u,"scanIdent","scan_ident","ident");
            if(token.isEmpty()) token=param(u,"scanInnerToken","innerToken","token");
            if(vkey.isEmpty()) vkey=param(u,"vkey256","vKey256","vkey","vKey");
            if(tokenTime.isEmpty()) tokenTime=param(u,"tokenTime","token_time","timeStamp","timestamp");
            if(vin.isEmpty()) vin=param(u,"vin","carVin","vehicleVin","scanVin");
            if(vinProvider.isEmpty()) vinProvider=param(u,"scanVinProvider","vinProvider");
        }catch(Exception ignored){}
        return new QrPayload(s,scanId,scanIdent,token,vkey,tokenTime,vin,vinProvider);
    }

    public String safeShape(){
        return "scanId="+yn(scanId)+
                ", scanIdent="+yn(scanIdent)+
                ", innerToken="+yn(innerToken)+
                ", vkey="+yn(vkey256)+
                ", tokenTime="+yn(tokenTime)+
                ", vin="+yn(vin)+
                ", vinProvider="+yn(scanVinProvider)+
                ", rawLen="+raw.length();
    }

    private static String yn(String s){return s==null||s.isEmpty()?"no":"yes";}
    private static String param(Uri u,String...keys){for(String k:keys){String v=u.getQueryParameter(k);if(v!=null&&!v.isEmpty())return v;}return"";}
    private static String first(JSONObject o,String...keys){for(String k:keys){String v=o.optString(k,"");if(!v.isEmpty())return v;}return"";}
}
