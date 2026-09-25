package com.carx.byd;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Java WBSK codec for the mainland-China BYD app transport.
 *
 * The algorithm is a clean Java port of the CN WBSK flow documented in the
 * MIT pyBYD CN branch. Lookup-table data is downloaded once from an immutable
 * BYD-re commit and cached in app-private storage so Alpha 2 stays small.
 */
public final class WbskCodec {
    private static final int[] NIBBLE_ENCODE = {0x0,0x8,0x4,0xC,0x1,0x9,0x5,0xD,0x2,0xA,0x6,0xE,0x3,0xB,0x7,0xF};
    private static final int[] NIBBLE_DECODE = {0x0,0x4,0x8,0xC,0x2,0x6,0xA,0xE,0x1,0x5,0x9,0xD,0x3,0x7,0xB,0xF};
    private static final int[] MYSTERY_ENCODE = new int[16];
    static { for (int n=0;n<16;n++) MYSTERY_ENCODE[n] = NIBBLE_ENCODE[NIBBLE_ENCODE[n ^ 8]]; }

    private static final String COMMIT = "d275dcf58090d37e0edef86ce21822383feb7276";
    private static final String TABLES_URL = "https://raw.githubusercontent.com/AwangYes/BYD-re/" + COMMIT + "/wbsk_tables.js";
    private static final String TABLES_FILE = "wbsk_tables_" + COMMIT.substring(0,8) + ".js";

    private static final String OUTER_ENCRYPT_KEY = "4dca015d9f0488cdea45e890de3b9c4d16c9f82e1082e295c8312d34da7214b805bdec33d8473ab04c84a51eebee4fd5efee21ed403a159a083dbb2854c92719d8f24dd3002ce675c4b930fd5f410ebe56d9594532f9c109b7f2dc58eebd83a83cc948fd3dc0b696add8b06d19efa7c8c04d17f60d144d943e21ef4add5af566ef14241de9c3bb03cf9b9d3c5d042caa1fcdf222e02ba7cf577cc70375d0b4e7e3340278e56ddee1a180451b3a04f25fe34f0d1f05ec426b0de801e7d7382ecf2c3ab7be923c2d5ff0c33eaa4c45c71b258045f68bd7ad0f594ff86785611f67f30da78dfa9b427f04d625a2c61e2db62e1fe7d4";
    private static final String OUTER_DECRYPT_KEY = "72ca0163b22e2973656a67ac1ae1490a61133824a0cd235bcfcd6032dba79d3ca51b1c4d1b03068566ff084645dcc9e6e8a28b39c71e72dec3fe4074109a84f5564d3f43f4854fb634bf633dabe218a5b73470dff70b07161b76c74d92bffa15bcb7fa4fc448a0fe83b62c9dd97f36d1d1d7613028041bb1dd328397bbf8c8bf6f81321f5e2d4982761a375bded52a1de198169839fabad771bc677b57f806c1ca385f43627e9a5081c43d7c9d9fa86e7e78cc0a8050a2420a76c842abbe93eb38f2487bdf93087cd24097a16539da2f86feb693432daf8f0618cbf97ffea3a762b7b91050f8634a8d3ceb25ea7d2b3264a77337";
    private static final String INNER_ENCRYPT_KEY = "9fca018f72712b15e23c275ea5e06a92d8b98404cf0bf960955596ff47dd2adf8f9e0c3ca1363e8be88cb6fced211933e5c3484c20c7bc3ae1eb8541027fef4a20b2f302d93582f7f4349fef05c16d389956ae9f2a7aea278fd5232229e38caca017ffbaf5138d2cadbca917b4694fb2882a64809c095387b7353608ca3a17913e5863770465986995c684ea7db01e0c35c69fd169a8e14ec5123beb5b8dad6e1c5198f34ed1c44d5f9b15035673df5953e5e42351f58052c1483fa4cf93c646a396081355a46d5a7e0ce30d54049802829cd78c1a77c7db8f74acb244b73c5147f161ee25bd702112cec97c339a6b3314527d12";
    private static final String INNER_DECRYPT_KEY = "71ca0160b689febe1c11e07bc8f8cec81decf71b6c3e0be299a35211888c2fb177958e57a6e971d0a874ecb50991786faf3a34b178f13a668bd14b81a82d3f799f6f0c8bd002406c8b6fdd54b3bb30c0c7d27c906dba87decde28717a0874abacf41755646b4a2c06854615ab00ae53136cbea3302b047659e7a42f792a7369fc130d8ffdc114a7a2cf2fa669b9b337905ff58fe3cc40b9b1edf37ebe50d36b3416abbd32837895b8ea1f22b9eab35efd791d9153208630297b8b953a9ca33265854c33959979b9eb1d049326986851170f4b51d151f43a30c6298a8c03503477336b2000c49746181ca30eabded6d3088b7f615";
    private static final String OUTER_ENCRYPT_IV = "91339992399838993130933138923692";
    private static final String OUTER_DECRYPT_IV = "54cc5558c551c155c4c05cc4c158ca58";
    private static final String INNER_ENCRYPT_IV = "a8bb9ab895ba95363a81b1949da68184";

    public interface Progress { void onProgress(String text); }
    private final Context context;
    private Tables tables;

    public WbskCodec(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void ensureTables(Progress progress) throws Exception {
        if (tables != null) return;
        File f = new File(context.getFilesDir(), TABLES_FILE);
        if (!f.exists() || f.length() < 10_000) {
            if (progress != null) progress.onProgress("تحميل محرك WBSK الصيني لأول مرة…");
            download(f);
        }
        if (progress != null) progress.onProgress("تهيئة تشفير BYD China…");
        String js = new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8);
        tables = parseTables(js);
    }

    public String encodeEnvelope(String plaintext) throws Exception {
        if (tables == null) throw new IllegalStateException("WBSK tables not loaded");
        return encryptWbskEnvelope(plaintext, INNER_ENCRYPT_KEY, INNER_ENCRYPT_IV, OUTER_ENCRYPT_KEY, OUTER_ENCRYPT_IV);
    }

    public String decodeEnvelope(String base64) throws Exception {
        if (tables == null) throw new IllegalStateException("WBSK tables not loaded");
        return decryptWbskEnvelope(base64, OUTER_DECRYPT_KEY, INNER_DECRYPT_KEY, OUTER_DECRYPT_IV);
    }

    /**
     * Offline golden-vector validation before any BYD credential is sent.
     * This protects the account from wasting login attempts if the Java WBSK
     * port or downloaded lookup tables are corrupt/incompatible.
     */
    public void selfTest() throws Exception {
        if (tables == null) throw new IllegalStateException("WBSK tables not loaded");
        final String envelope =
                "k0rtymBTcdIh022mIpcJEMGrPoHX2Qz2jwcluQ300c0X2oQVmVi92chOWl6iWgTiy6cteRnVFgm8SWVamBCTRTJNJ46s79Yrj3PqTACkyi178Q1iWq3onlsyenp8Rc9XEw+mqFmA9H8ls8pABVpW+tLfGipTZycvbkp9SS6wTmdmHd4+POky6LV4r3XeQ+qchV6Ldkkvd6CwHL4U5VvPiFSP26G9YoZCS5ddr8sbPnGW3cOKwMh2x2e6lZMNtTbE5dQd2sct9pm7/pKhs72EeOV36g+Y+iZvEIOVixAVNf+ipnRN/TiNA6sG36b/9vF5SBz7kCs2fmTCNf9eU4qka4t/MBPGFXV8ybNNQtfF6FZub0NrhkiPT1QDXfr2mfoe8/29Wf/kISW4E3aRKXmWW4fLuzUpdhYirjzie6ROwkoEGM9UVQVV5h3Ff4m5lcEIQQmXgoX98qyHKBpWDdq7bsTkpFY1MgzNEkH88aO6mLkv9AF9Xlm+Q9ehtVA0eYfaE0+Tf5boUD/vSTlPljxmLwTq2VfXjYIioxpMr1HGx6LRUDFzNUjL9hsM/Fi2hXRsTl194nnrrvqw1tfKim7m5k4KXMRnvcoHEiAZCaxSxZVUhyVcnPvHbmsp9/0LZPLKOrromENif2lJ4a1L7cTsmnt+OaoffcIOCouh2JY5+CgoMYwg+3wg4YUdDhogOxqJweQnlB7xFNnjCzhk0p7BuQpMq4tIa6LG3ddfL+Zug1Q4SYHWxMU5SEAdOujT4pwCuzDAxOt0cVISiDbPh7YGy5uesILUA1/JSj/IrQZg5BzzWChKPU18eoyTdSj631sJLKVolOH4UjXU0riZDuxtFtXkn9xjNKoUjX/Km0bFZT2ivHCA29kqME8vEWybrXt8Rt9+ksV52ymzLnHiU61TlYWtYU6rmTfo5pGjWRELaxhJbZBjK7Y9B87Wn2rJey/b+HO+y4WqL0IUs4mspS/ocP4UExRGQLvN+jWN2DrmMz7fi0VXYXCrnJ1Q1aCbhLvrtySBH7qOhsEBMmOqHA7n0P4be4jOyo/0WxVs0AwlQhgjPNstQErRp7A/dkY/p+uYr8OT5GsCQ+f4fsZeQ3+DIsv3sWXbn7OMIxmFaJZ5pMkCl2SbPiehUWdIL3vrcRtVR8CT3JSaI9BpOo9CRvkhZvjBYhaMwEJPljXepPxwFiiZJRzytzZAJq0HPuVamP1tE7T4tWDXgHTgycQeMHE6E+HeKrOFaCDIr09vpIhfHohvkUs0bEyiSzejmqJHyV7MW1vJ5v2V9l5SoQYMhvKuwL/MujQlsQEqi97+U25+w5Y=";
        String decoded = decodeEnvelope(envelope);
        if (!decoded.contains("\"checkcode\":\"2eb5bb65dc1c49496824ba0462f62b2aa99ffcbbe62680028a41a197a6045718\"")
                || !decoded.contains("\"version\":\"502\"")) {
            throw new IllegalStateException("WBSK golden-vector self-test failed");
        }
        String probe = "{\"carx\":\"china\",\"v\":2}";
        String roundTrip = decodeEnvelope(encodeEnvelope(probe));
        if (!probe.equals(roundTrip)) throw new IllegalStateException("WBSK round-trip self-test failed");
    }

    private static void download(File target) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName()+".tmp");
        HttpURLConnection c=(HttpURLConnection)new URL(TABLES_URL).openConnection();
        c.setConnectTimeout(20_000); c.setReadTimeout(30_000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","CarX-BYD/2.0");
        try {
            int status=c.getResponseCode(); if(status<200||status>=300) throw new IllegalStateException("WBSK table HTTP "+status);
            try(InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>=0) out.write(buf,0,n); out.getFD().sync();
            }
        } finally { c.disconnect(); }
        String js=new String(readAll(new FileInputStream(tmp)),StandardCharsets.UTF_8);
        parseTables(js);
        if(target.exists()&&!target.delete()) throw new IllegalStateException("Cannot replace WBSK cache");
        if(!tmp.renameTo(target)) throw new IllegalStateException("Cannot save WBSK cache");
    }

    private static Tables parseTables(String js) {
        String[] names={"encInitXor","encRoundXor","encSbox","encFinalXor","encTe0","encTe1","encTe2","encTe3","decInitXor","decRoundXor","decInvSbox","decFinalXor","decTd0","decTd1","decTd2","decTd3"};
        Map<String,String> values=new HashMap<>();
        for(String name:names){
            Matcher m=Pattern.compile("\\\""+Pattern.quote(name)+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(js);
            if(!m.find()) throw new IllegalArgumentException("Missing WBSK table "+name);
            values.put(name,m.group(1));
        }
        return new Tables(
                b64(values.get("encInitXor"),256),b64(values.get("encRoundXor"),256),b64(values.get("encSbox"),256),b64(values.get("encFinalXor"),256),
                u32(values.get("encTe0")),u32(values.get("encTe1")),u32(values.get("encTe2")),u32(values.get("encTe3")),
                b64(values.get("decInitXor"),256),b64(values.get("decRoundXor"),256),b64(values.get("decInvSbox"),256),b64(values.get("decFinalXor"),256),
                u32(values.get("decTd0")),u32(values.get("decTd1")),u32(values.get("decTd2")),u32(values.get("decTd3"))
        );
    }

    private static byte[] b64(String s,int expected){ byte[] b=Base64.decode(s,Base64.DEFAULT); if(b.length!=expected) throw new IllegalArgumentException("Bad WBSK table size"); return b; }
    private static long[] u32(String s){ byte[] b=b64(s,1024); long[] out=new long[256]; ByteBuffer bb=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN); for(int i=0;i<256;i++) out[i]=bb.getInt()&0xffffffffL; return out; }

    private static final class Tables {
        final byte[] encInitXor,encRoundXor,encSbox,encFinalXor,decInitXor,decRoundXor,decInvSbox,decFinalXor;
        final long[] encTe0,encTe1,encTe2,encTe3,decTd0,decTd1,decTd2,decTd3;
        Tables(byte[] a,byte[] b,byte[] c,byte[] d,long[] e,long[] f,long[] g,long[] h,byte[] i,byte[] j,byte[] k,byte[] l,long[] m,long[] n,long[] o,long[] p){encInitXor=a;encRoundXor=b;encSbox=c;encFinalXor=d;encTe0=e;encTe1=f;encTe2=g;encTe3=h;decInitXor=i;decRoundXor=j;decInvSbox=k;decFinalXor=l;decTd0=m;decTd1=n;decTd2=o;decTd3=p;}
    }

    private static int u8(byte b){return b&0xff;}
    private static int protXor(byte[] table,int a,int b){ int hi=u8(table[((a>>4)<<4)^(b>>4)])&0xF0; int lo=(u8(table[((a&0xF)<<4)^(b&0xF)])>>4)&0x0F; return hi|lo; }

    private static final class ParsedKey { final byte[] data; final int rounds; ParsedKey(byte[] d,int r){data=d;rounds=r;} }
    private static ParsedKey parseWbcKey(String hex){
        byte[] raw=BydCrypto.hexToBytes(hex); if(raw.length<5) throw new IllegalArgumentException("WBC key too short");
        int mode=u8(raw[0])^u8(raw[3]); byte[] data=new byte[raw.length-4]; for(int i=4;i<raw.length;i++) data[i-4]=(byte)(u8(raw[i])^u8(raw[i%3]));
        int bits;
        switch(mode){
            case 0:case 1:case 4:case 5:case 0xA:case 0xB:case 0xC:case 0xD:case 0x16:case 0x17: bits=0x80; break;
            case 2:case 3:case 8:case 9:case 0xE:case 0xF:case 0x14:case 0x15: bits=0xC0; break;
            case 6:case 7:case 0x12:case 0x13: bits=0x40; break;
            case 0x10:case 0x11: bits=0x100; break;
            default: throw new IllegalArgumentException("Unknown WBC mode "+mode);
        }
        return new ParsedKey(data,(bits>>5)+6);
    }

    private byte[] encryptBlock(byte[] in,byte[] key,int rounds){
        byte[] state=new byte[16],t1=new byte[16],t2=new byte[16],out=new byte[16];
        for(int i=0;i<16;i++) state[i]=(byte)protXor(tables.encInitXor,u8(in[i]),u8(key[i]));
        int[] te1i={5,9,13,1},te2i={10,14,2,6},te3i={15,3,7,11};
        for(int r=1;r<rounds;r++){
            for(int c=0;c<4;c++) putBe(t1,c*4,tables.encTe0[u8(state[c*4])]);
            for(int c=0;c<4;c++) putBe(t2,c*4,tables.encTe1[u8(state[te1i[c]])]);
            for(int i=0;i<16;i++) t1[i]=(byte)protXor(tables.encRoundXor,u8(t1[i]),u8(t2[i]));
            for(int c=0;c<4;c++) putBe(t2,c*4,tables.encTe2[u8(state[te2i[c]])]);
            for(int i=0;i<16;i++) t1[i]=(byte)protXor(tables.encRoundXor,u8(t1[i]),u8(t2[i]));
            for(int c=0;c<4;c++) putBe(t2,c*4,tables.encTe3[u8(state[te3i[c]])]);
            for(int i=0;i<16;i++) t1[i]=(byte)protXor(tables.encRoundXor,u8(t1[i]),u8(t2[i]));
            int off=r*16; for(int i=0;i<16;i++) state[i]=(byte)protXor(tables.encRoundXor,u8(t1[i]),u8(key[off+i]));
        }
        int[] sr={0,5,10,15,4,9,14,3,8,13,2,7,12,1,6,11};
        for(int i=0;i<16;i++) t1[i]=tables.encSbox[u8(state[sr[i]])];
        int off=rounds*16; for(int i=0;i<16;i++) out[i]=(byte)protXor(tables.encFinalXor,u8(t1[i]),u8(key[off+i]));
        return out;
    }

    private byte[] decryptBlock(byte[] in,byte[] key,int rounds){
        byte[] state=new byte[16],t1=new byte[16],t2=new byte[16],out=new byte[16];
        for(int i=0;i<16;i++) state[i]=(byte)protXor(tables.decInitXor,u8(in[i]),u8(key[i]));
        int[] td1i={13,1,5,9},td2i={10,14,2,6},td3i={7,11,15,3};
        for(int r=1;r<rounds;r++){
            for(int c=0;c<4;c++) putBe(t1,c*4,tables.decTd0[u8(state[c*4])]);
            for(int c=0;c<4;c++) putBe(t2,c*4,tables.decTd1[u8(state[td1i[c]])]);
            for(int i=0;i<16;i++) t1[i]=(byte)protXor(tables.decRoundXor,u8(t1[i]),u8(t2[i]));
            for(int c=0;c<4;c++) putBe(t2,c*4,tables.decTd2[u8(state[td2i[c]])]);
            for(int i=0;i<16;i++) t1[i]=(byte)protXor(tables.decRoundXor,u8(t1[i]),u8(t2[i]));
            for(int c=0;c<4;c++) putBe(t2,c*4,tables.decTd3[u8(state[td3i[c]])]);
            for(int i=0;i<16;i++) t1[i]=(byte)protXor(tables.decRoundXor,u8(t1[i]),u8(t2[i]));
            int off=r*16; for(int i=0;i<16;i++) state[i]=(byte)protXor(tables.decRoundXor,u8(t1[i]),u8(key[off+i]));
        }
        int[] inv={0,13,10,7,4,1,14,11,8,5,2,15,12,9,6,3};
        for(int i=0;i<16;i++) t1[i]=tables.decInvSbox[u8(state[inv[i]])];
        int off=rounds*16; for(int i=0;i<16;i++) out[i]=(byte)protXor(tables.decFinalXor,u8(t1[i]),u8(key[off+i]));
        return out;
    }

    private static void putBe(byte[] a,int off,long v){a[off]=(byte)(v>>>24);a[off+1]=(byte)(v>>>16);a[off+2]=(byte)(v>>>8);a[off+3]=(byte)v;}

    private byte[] encryptCbc(byte[] input,byte[] key,int rounds,byte[] iv){
        byte[] out=new byte[input.length],prev=Arrays.copyOf(iv,16);
        for(int off=0;off<input.length;off+=16){byte[] b=Arrays.copyOfRange(input,off,off+16);for(int i=0;i<16;i++)b[i]^=prev[i];byte[] e=encryptBlock(b,key,rounds);System.arraycopy(e,0,out,off,16);prev=e;} return out;
    }
    private byte[] decryptCbc(byte[] input,byte[] key,int rounds,byte[] iv){
        byte[] out=new byte[input.length],prev=Arrays.copyOf(iv,16);
        for(int off=0;off<input.length;off+=16){byte[] b=Arrays.copyOfRange(input,off,off+16);byte[] d=decryptBlock(b,key,rounds);for(int i=0;i<16;i++)out[off+i]=(byte)(d[i]^prev[i]);prev=b;} return out;
    }

    private static byte[] nibbleEncode(byte[] in){byte[] o=new byte[in.length];for(int i=0;i<in.length;i++){int b=u8(in[i]);o[i]=(byte)((NIBBLE_ENCODE[b>>>4]<<4)|NIBBLE_ENCODE[b&15]);}return o;}
    private static byte[] nibbleDecode(byte[] in){byte[] o=new byte[in.length];for(int i=0;i<in.length;i++){int b=u8(in[i]);o[i]=(byte)((NIBBLE_DECODE[b>>>4]<<4)|NIBBLE_DECODE[b&15]);}return o;}
    private static byte[] wbcInputEncode(byte[] in){byte[] o=new byte[in.length];for(int i=0;i<in.length;i++){int b=u8(in[i]);o[i]=(byte)((MYSTERY_ENCODE[b>>>4]<<4)|MYSTERY_ENCODE[b&15]);}return o;}
    private static byte[] wbcOutputDecode(byte[] in){byte[] o=new byte[in.length];for(int i=0;i<in.length;i++){int b=u8(in[i]);o[i]=(byte)((NIBBLE_ENCODE[NIBBLE_ENCODE[b>>>4]]<<4)|NIBBLE_ENCODE[NIBBLE_ENCODE[b&15]]);}return o;}
    private static byte[] stripPkcs7(byte[] b){if(b.length==0)return b;int p=u8(b[b.length-1]);if(p<1||p>16)return b;for(int i=b.length-p;i<b.length;i++)if(u8(b[i])!=p)return b;return Arrays.copyOf(b,b.length-p);}
    private static byte[] addWbcPkcs7(byte[] b){int rem=b.length%16,p=rem==0?16:16-rem;int pb=(MYSTERY_ENCODE[p>>>4]<<4)|MYSTERY_ENCODE[p&15];byte[] o=Arrays.copyOf(b,b.length+p);Arrays.fill(o,b.length,o.length,(byte)pb);return o;}
    private static byte[] concat(byte[] a,byte[] b){byte[] o=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,o,a.length,b.length);return o;}

    private String encryptWbskEnvelope(String plaintext,String innerKeyHex,String innerIvHex,String outerKeyHex,String outerIvHex){
        byte[] innerPadded=addWbcPkcs7(wbcInputEncode(plaintext.getBytes(StandardCharsets.UTF_8)));
        ParsedKey ik=parseWbcKey(innerKeyHex); byte[] innerIv=BydCrypto.hexToBytes(innerIvHex);
        byte[] innerEncrypted=encryptCbc(innerPadded,ik.data,ik.rounds,innerIv);
        byte[] innerRaw=wbcOutputDecode(innerEncrypted); String innerB64=Base64.encodeToString(innerRaw,Base64.NO_WRAP);
        byte[] outerPlain=concat(innerB64.getBytes(StandardCharsets.ISO_8859_1),wbcOutputDecode(innerIv));
        byte[] outerMystery=addWbcPkcs7(wbcInputEncode(outerPlain)); ParsedKey ok=parseWbcKey(outerKeyHex); byte[] outerIv=BydCrypto.hexToBytes(outerIvHex);
        return Base64.encodeToString(wbcOutputDecode(encryptCbc(outerMystery,ok.data,ok.rounds,outerIv)),Base64.NO_WRAP);
    }

    private String decryptWbskEnvelope(String b64,String outerKeyHex,String innerKeyHex,String outerIvHex){
        byte[] raw=Base64.decode(b64.trim(),Base64.DEFAULT); byte[] encoded=concat(nibbleEncode(raw),new byte[256]); ParsedKey ok=parseWbcKey(outerKeyHex);
        byte[] dec=decryptCbc(encoded,ok.data,ok.rounds,BydCrypto.hexToBytes(outerIvHex)); byte[] outerContent=stripPkcs7(nibbleDecode(Arrays.copyOf(dec,raw.length)));
        if(outerContent.length<16) throw new IllegalStateException("WBSK outer content too short"); int n=outerContent.length; String innerB64=new String(outerContent,0,n-16,StandardCharsets.ISO_8859_1);
        byte[] innerIv=Arrays.copyOfRange(dec,n-16,n); byte[] innerRaw=Base64.decode(innerB64,Base64.DEFAULT); byte[] innerEncoded=concat(nibbleEncode(innerRaw),new byte[256]); ParsedKey ik=parseWbcKey(innerKeyHex);
        byte[] innerDec=decryptCbc(innerEncoded,ik.data,ik.rounds,innerIv); byte[] innerContent=stripPkcs7(nibbleDecode(Arrays.copyOf(innerDec,innerRaw.length)));
        return new String(innerContent,StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in)throws Exception{try(InputStream src=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=src.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}
}
