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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Java port of pyBYD's Bangcle white-box AES envelope codec.
 * Source algorithm: jkaberg/pyBYD (MIT). Tables are downloaded from a pinned commit
 * on first use and stored in the app-private files directory.
 */
public final class BangcleCodec {
    private static final byte[] ZERO_IV = new byte[16];
    private static final byte[] MAGIC = new byte[]{'B','G','T','B'};
    private static final int[] TABLE_LENGTHS = {
            0x28000, 0x3C000, 0x1000, 0x28000, 0x3C000, 0x1000, 8, 8
    };
    private static final String TABLES_URL =
            "https://raw.githubusercontent.com/jkaberg/pyBYD/ee51c6758c03e83ca144bfb12a74927863060945/src/pybyd/data/bangcle_tables.bin";
    private static final String TABLES_FILE = "bangcle_tables_v1.bin";

    public interface Progress { void onProgress(String text); }

    private final Context context;
    private Tables tables;

    public BangcleCodec(Context context) { this.context = context.getApplicationContext(); }

    public synchronized void ensureTables(Progress progress) throws Exception {
        if (tables != null) return;
        File file = new File(context.getFilesDir(), TABLES_FILE);
        if (!file.exists() || file.length() < 800_000) {
            if (progress != null) progress.onProgress("تحميل محرك تشفير BYD لأول مرة…");
            downloadTables(file);
        }
        if (progress != null) progress.onProgress("تهيئة طبقة Bangcle…");
        byte[] raw = readAll(new FileInputStream(file));
        tables = parseTables(raw);
    }

    private static void downloadTables(File target) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        HttpURLConnection c = (HttpURLConnection) new URL(TABLES_URL).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "CarX-BYD/1.0");
        try {
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("Table download HTTP " + status);
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.getFD().sync();
            }
        } finally { c.disconnect(); }
        // Validate before replacing cached copy.
        parseTables(readAll(new FileInputStream(tmp)));
        if (target.exists() && !target.delete()) throw new IllegalStateException("Cannot replace Bangcle table cache");
        if (!tmp.renameTo(target)) throw new IllegalStateException("Cannot save Bangcle table cache");
    }

    public String encodeEnvelope(String plaintext) throws Exception {
        if (tables == null) throw new IllegalStateException("Bangcle tables not loaded");
        byte[] padded = addPkcs7(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = encryptCbc(tables, padded, ZERO_IV);
        return "F" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    public String decodeEnvelope(String envelope) throws Exception {
        if (tables == null) throw new IllegalStateException("Bangcle tables not loaded");
        String cleaned = envelope.replace(" ", "").replace("\t", "").replace("\n", "").replace("\r", "").trim();
        cleaned = cleaned.replace('-', '+').replace('_', '/');
        if (!cleaned.startsWith("F")) throw new IllegalArgumentException("Bangcle response missing F prefix");
        cleaned = cleaned.substring(1);
        while (cleaned.length() % 4 != 0) cleaned += "=";
        byte[] cipher = Base64.decode(cleaned, Base64.DEFAULT);
        if (cipher.length == 0 || cipher.length % 16 != 0) throw new IllegalArgumentException("Invalid Bangcle ciphertext");
        return new String(stripPkcs7(decryptCbc(tables, cipher, ZERO_IV)), StandardCharsets.UTF_8);
    }

    private static Tables parseTables(byte[] data) {
        if (data.length < 72) throw new IllegalArgumentException("Bangcle table file too short");
        for (int i = 0; i < 4; i++) if (data[i] != MAGIC[i]) throw new IllegalArgumentException("Bad Bangcle table magic");
        int version = u16le(data, 4), count = u16le(data, 6);
        if (version != 1 || count != 8) throw new IllegalArgumentException("Unsupported Bangcle table format");
        byte[][] table = new byte[8][];
        for (int i = 0; i < 8; i++) {
            int idx = 8 + i * 8;
            int offset = u32le(data, idx);
            int len = u32le(data, idx + 4);
            if (len != TABLE_LENGTHS[i] || offset < 0 || offset + len > data.length)
                throw new IllegalArgumentException("Invalid Bangcle table " + i);
            table[i] = Arrays.copyOfRange(data, offset, offset + len);
        }
        return new Tables(table);
    }

    private static final class Tables {
        final byte[] invRound, invXor, invFirst, round, xor, fin, permDecrypt, permEncrypt;
        Tables(byte[][] t) {
            invRound=t[0]; invXor=t[1]; invFirst=t[2]; round=t[3]; xor=t[4]; fin=t[5]; permDecrypt=t[6]; permEncrypt=t[7];
        }
    }

    private static byte[] encryptBlock(Tables t, byte[] block) {
        byte[] state = new byte[32], temp64 = new byte[64], tmp32 = new byte[32], out = new byte[16];
        prepareMatrix(block, state);
        for (int rnd = 0; rnd < 9; rnd++) {
            int lVar21 = rnd * 4;
            int permPtr = 0;
            for (int i = 0; i < 4; i++) {
                int bVar4 = t.permEncrypt[permPtr] & 0xff;
                int lVar16 = i * 8, base = i * 16;
                for (int j = 0; j < 4; j++) {
                    int uVar8 = (bVar4 + j) & 3;
                    int byteVal = state[lVar16 + uVar8] & 0xff;
                    int idx = byteVal + (i + (lVar21 + uVar8) * 4) * 256;
                    int value = u32le(t.round, idx * 4);
                    putU32le(temp64, base + j * 4, value);
                }
                permPtr += 2;
            }
            int iVar16 = 1;
            for (int lVar22 = 0; lVar22 < 4; lVar22++) {
                int pb = lVar22;
                for (int lVar10 = 0; lVar10 < 4; lVar10++) {
                    int local10 = temp64[pb] & 0xff;
                    int uVar7 = local10 & 0xF;
                    int uVar26 = local10 & 0xF0;
                    int localF0 = temp64[pb + 0x10] & 0xff;
                    int localF1 = temp64[pb + 0x20] & 0xff;
                    int localF2 = temp64[pb + 0x30] & 0xff;
                    int lVar2 = lVar10 * 0x18 + rnd * 0x60;
                    int iVar25 = iVar16;
                    for (int lVar17 = 0; lVar17 < 3; lVar17++) {
                        int inner = lVar17 == 0 ? localF0 : (lVar17 == 1 ? localF1 : localF2);
                        int uVar1 = (inner << 4) & 0xff;
                        int uVar27 = uVar7 | uVar1;
                        uVar26 = ((uVar26 >> 4) | ((inner >> 4) << 4)) & 0xff;
                        int idx1 = (lVar2 + (iVar25 - 1)) * 0x100 + uVar27;
                        uVar7 = (t.xor[idx1] & 0xff) & 0xF;
                        int idx2 = (lVar2 + iVar25) * 0x100 + uVar26;
                        int next = t.xor[idx2] & 0xff;
                        uVar26 = (next & 0xF) << 4;
                        iVar25 += 2;
                    }
                    state[lVar10 + lVar22 * 8] = (byte)((uVar26 | uVar7) & 0xff);
                    pb += 4;
                }
                iVar16 += 6;
            }
        }
        System.arraycopy(state,0,tmp32,0,32);
        int u13=3,u9=2,u11=1,u8=0;
        for (int row=0; row<4; row++) {
            int r0=(u8+row)&3; state[row]=t.fin[(tmp32[r0]&0xff)+r0*0x400];
            int r1=(u11+row)&3; state[8+row]=t.fin[(tmp32[8+r1]&0xff)+r1*0x400+0x100];
            int r2=(u9+row)&3; state[0x10+row]=t.fin[(tmp32[0x10+r2]&0xff)+r2*0x400+0x200];
            int r3=(u13+row)&3; state[0x18+row]=t.fin[(tmp32[0x18+r3]&0xff)+r3*0x400+0x300];
        }
        flattenMatrix(state,out);
        return out;
    }

    private static byte[] decryptBlock(Tables t, byte[] block) {
        byte[] state = new byte[32], temp64 = new byte[64], tmp32 = new byte[32], out = new byte[16];
        prepareMatrix(block,state);
        for (int rnd=9; rnd>=1; rnd--) {
            int lVar20=rnd, lVar21=lVar20*4, permPtr=0;
            for (int i=0;i<4;i++) {
                int bVar3=t.permDecrypt[permPtr]&0xff, lVar16=i*8, base=i*16;
                for(int j=0;j<4;j++) {
                    int uVar7=(bVar3+j)&3;
                    int byteVal=state[lVar16+uVar7]&0xff;
                    int idx=byteVal+(i+(lVar21+uVar7)*4)*256;
                    putU32le(temp64,base+j*4,u32le(t.invRound,idx*4));
                }
                permPtr+=2;
            }
            int iVar15=1;
            for(int c=0;c<4;c++) {
                int pb=c;
                for(int r=0;r<4;r++) {
                    int local10=temp64[pb]&0xff, uVar6=local10&0xF, uVar26=local10&0xF0;
                    int f0=temp64[pb+0x10]&0xff, f1=temp64[pb+0x20]&0xff, f2=temp64[pb+0x30]&0xff;
                    int lVar2=r*0x18+lVar20*0x60, iVar25=iVar15;
                    for(int z=0;z<3;z++) {
                        int inner=z==0?f0:(z==1?f1:f2);
                        int uVar1=(inner<<4)&0xff, uVar27=uVar6|uVar1;
                        uVar26=((uVar26>>4)|((inner>>4)<<4))&0xff;
                        int idx1=(lVar2+(iVar25-1))*0x100+uVar27;
                        uVar6=(t.invXor[idx1]&0xff)&0xF;
                        int idx2=(lVar2+iVar25)*0x100+uVar26;
                        int n=t.invXor[idx2]&0xff;
                        uVar26=(n&0xF)<<4;
                        iVar25+=2;
                    }
                    state[r+c*8]=(byte)((uVar26|uVar6)&0xff);
                    pb+=4;
                }
                iVar15+=6;
            }
        }
        System.arraycopy(state,0,tmp32,0,32);
        int u8=1,u10=3,u12=2;
        for(int row=0;row<4;row++) {
            int idx0=(tmp32[row]&0xff)+row*0x400; state[row]=t.invFirst[idx0];
            int row1=u10&3; state[8+row]=t.invFirst[(tmp32[8+row1]&0xff)+row1*0x400+0x100];
            int row2=u12&3; state[0x10+row]=t.invFirst[(tmp32[0x10+row2]&0xff)+row2*0x400+0x200];
            int row3=u8&3; state[0x18+row]=t.invFirst[(tmp32[0x18+row3]&0xff)+row3*0x400+0x300];
            u8++;u10++;u12++;
        }
        flattenMatrix(state,out);
        return out;
    }

    private static byte[] encryptCbc(Tables t, byte[] data, byte[] iv) {
        byte[] result=new byte[data.length], prev=Arrays.copyOf(iv,16);
        for(int off=0;off<data.length;off+=16){
            byte[] block=Arrays.copyOfRange(data,off,off+16);
            for(int i=0;i<16;i++)block[i]^=prev[i];
            byte[] enc=encryptBlock(t,block);
            System.arraycopy(enc,0,result,off,16); prev=enc;
        }
        return result;
    }

    private static byte[] decryptCbc(Tables t, byte[] data, byte[] iv) {
        byte[] result=new byte[data.length], prev=Arrays.copyOf(iv,16);
        for(int off=0;off<data.length;off+=16){
            byte[] block=Arrays.copyOfRange(data,off,off+16);
            byte[] dec=decryptBlock(t,block);
            for(int i=0;i<16;i++)dec[i]^=prev[i];
            System.arraycopy(dec,0,result,off,16); prev=block;
        }
        return result;
    }

    private static void prepareMatrix(byte[] input, byte[] output){
        for(int col=0;col<4;col++)for(int row=0;row<4;row++)output[col*8+row]=input[col+row*4];
    }
    private static void flattenMatrix(byte[] state,byte[] out){
        for(int col=0;col<4;col++)for(int row=0;row<4;row++)out[col+row*4]=state[col*8+row];
    }
    private static byte[] addPkcs7(byte[] input){
        int pad=16-(input.length%16); byte[] out=Arrays.copyOf(input,input.length+pad); Arrays.fill(out,input.length,out.length,(byte)pad); return out;
    }
    private static byte[] stripPkcs7(byte[] input){
        if(input.length==0)return input; int pad=input[input.length-1]&0xff; if(pad<1||pad>16||pad>input.length)throw new IllegalArgumentException("Bad PKCS7");
        for(int i=input.length-pad;i<input.length;i++)if((input[i]&0xff)!=pad)throw new IllegalArgumentException("Bad PKCS7");
        return Arrays.copyOf(input,input.length-pad);
    }
    private static int u16le(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
    private static int u32le(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16)|((b[o+3]&255)<<24);}
    private static void putU32le(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>>8);b[o+2]=(byte)(v>>>16);b[o+3]=(byte)(v>>>24);}
    private static byte[] readAll(InputStream in) throws Exception {
        try(InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384];int n;while((n=input.read(buf))>=0)out.write(buf,0,n);return out.toByteArray();
        }
    }
}
