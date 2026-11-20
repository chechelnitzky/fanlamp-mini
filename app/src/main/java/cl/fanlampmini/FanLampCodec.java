package cl.fanlampmini;

import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal independent Java implementation of the FanLamp Pro BLE advertising protocols.
 * Based on NicoIIT/ha-ble-adv (MIT license, see THIRD_PARTY_NOTICES.md).
 * No connection to the fan is established: each command is a BLE advertising broadcast.
 */
public final class FanLampCodec {
    public enum Version { V3, V2, V1 }

    public static final class Config {
        public Version version = Version.V3;
        public long id;                 // unsigned 32-bit ID
        public int index;
        public int seed;                 // unsigned 16-bit seed
        public int txCount;
        public int[] prefix = {0x20, 0x80, 0x00};
        public boolean signed = true;

        public Config copy() {
            Config c = new Config();
            c.version=version; c.id=id; c.index=index; c.seed=seed; c.txCount=txCount;
            c.prefix=prefix.clone(); c.signed=signed;
            return c;
        }
    }

    public static final class Decoded {
        public Config config;
        public int command;
        public int arg0;
        public int arg1;
        public int arg2;
        public int param;
    }

    private static final SecureRandom RAND = new SecureRandom();
    private static final int[] XBOXES = {
        0xB7,0xFD,0x93,0x26,0x36,0x3F,0xF7,0xCC,0x34,0xA5,0xE5,0xF1,0x71,0xD8,0x31,0x15,
        0x04,0xC7,0x23,0xC3,0x18,0x96,0x05,0x9A,0x07,0x12,0x80,0xE2,0xEB,0x27,0xB2,0x75,
        0xD0,0xEF,0xAA,0xFB,0x43,0x4D,0x33,0x85,0x45,0xF9,0x02,0x7F,0x50,0x3C,0x9F,0xA8,
        0x51,0xA3,0x40,0x8F,0x92,0x9D,0x38,0xF5,0xBC,0xB6,0xDA,0x21,0x10,0xFF,0xF3,0xD2,
        0xE0,0x32,0x3A,0x0A,0x49,0x06,0x24,0x5C,0xC2,0xD3,0xAC,0x62,0x91,0x95,0xE4,0x79,
        0xE7,0xC8,0x37,0x6D,0x8D,0xD5,0x4E,0xA9,0x6C,0x56,0xF4,0xEA,0x65,0x7A,0xAE,0x08,
        0xE1,0xF8,0x98,0x11,0x69,0xD9,0x8E,0x94,0x9B,0x1E,0x87,0xE9,0xCE,0x55,0x28,0xDF,
        0x8C,0xA1,0x89,0x0D,0xBF,0xE6,0x42,0x68,0x41,0x99,0x2D,0x0F,0xB0,0x54,0xBB,0x16
    };
    private static final byte[] V1_FIXED = {(byte)0xB6,0x5F,0x2B,0x5E,0x00,(byte)0xFC,0x31,0x51};

    private FanLampCodec() {}

    public static Config fresh(Version v) {
        Config c = new Config();
        c.version=v;
        c.id=v==Version.V1 ? (RAND.nextInt(0x10000) & 0xF0FF) | ((long)RAND.nextInt(256)<<16)
                           : (RAND.nextLong() & 0xFFFFFFFFL);
        c.index=RAND.nextInt(4);
        c.seed=RAND.nextInt(0xFFF5)+1;
        c.prefix=v==Version.V3 ? new int[]{0x20,0x80,0} : new int[]{0x10,0x80,0};
        c.signed=v==Version.V3;
        return c;
    }

    // Light commands: 0x10 ON, 0x11 OFF. Fan v2/v3: 0x31 arg0=0x20 arg1=speed (0..6).
    // Fan v1: 0x32 arg0=speed arg1=6, OFF 0x31 arg0=arg1=0.
    public static byte[] light(Config c, boolean on) {
        return encode(c, on ? 0x10 : 0x11, 0,0,0,0);
    }
    public static byte[] fan(Config c, int speed) {
        if (speed < 0 || speed > 6) throw new IllegalArgumentException("Velocidad inválida");
        if (c.version==Version.V1)
            return speed==0 ? encode(c,0x31,0,0,0,0) : encode(c,0x32,speed,6,0,0);
        return encode(c,0x31,0x20,speed,0,0);
    }
    public static byte[] pair(Config c) { return encode(c,0x28,0,0,0,0); }

    public static byte[] encode(Config c, int command, int arg0, int arg1, int arg2, int param) {
        if (c.index<0 || c.index>255 || c.seed<1 || c.seed>0xFFF5 ||
                (c.id & ~0xFFFFFFFFL)!=0 || c.prefix.length!=3)
            throw new IllegalArgumentException("Configuración fuera de rango");
        // The user's HCI capture contains V3 counters 250/251 and V1 counters 235/236.\n        // The packet field is an unsigned byte; previous modulo-125 was incorrect.\n        c.txCount=(c.txCount+1)&0xFF;
        if (c.version==Version.V1) return encodeV1(c,command,arg0,arg1,arg2,param);
        return encodeV2(c,command,arg0,arg1,arg2,param);
    }

    private static byte[] encodeV2(Config c, int cmd, int a0, int a1, int a2, int param) {
        // Upstream encodes a 22-byte body, encrypts it to 24 bytes (CRC included),
        // and prepends the fixed F0 08 advertising header -> 26 bytes.
        byte[] p=new byte[22];
        for(int i=0;i<3;i++)p[i]=(byte)c.prefix[i];
        p[3]=(byte)c.txCount;
        p[4]=0; p[5]=4; // 0x0400, little-endian device type
        for(int i=0;i<4;i++)p[6+i]=(byte)(c.id>>>(8*i));
        p[10]=(byte)c.index; p[11]=(byte)cmd;
        p[12]=0; p[13]=(byte)param;
        p[14]=(byte)a0; p[15]=(byte)a1; p[16]=(byte)a2;
        int signature=c.signed?sign(Arrays.copyOfRange(p,1,17),c.txCount,c.seed):0;
        p[17]=(byte)signature; p[18]=(byte)(signature>>>8); p[19]=0;
        p[20]=(byte)c.seed; p[21]=(byte)(c.seed>>>8);
        byte[] out=new byte[26];
        out[0]=(byte)0xF0; out[1]=0x08;
        out[2]=p[0]; out[3]=p[1];
        int salt=(p[1]&3)<<5;
        for(int i=2;i<20;i++)out[i+2]=(byte)(p[i]^XBOXES[((c.seed+i-2+9)&31)+salt]^(c.seed&255));
        out[22]=p[20];out[23]=p[21];
        int crc=crc16(Arrays.copyOfRange(out,2,24),c.seed^0xFFFF);
        out[24]=(byte)crc;out[25]=(byte)(crc>>>8);
        return out;
    }

    private static byte[] encodeV1(Config c, int cmd, int a0, int a1, int a2, int param) {
        byte[] d = new byte[14];
        int uid=((int)c.id & 0xF0FF)|((c.index & 0x0F)<<8);
        d[0]=(byte)cmd; d[1]=(byte)uid; d[2]=(byte)(uid>>>8);
        boolean pair=cmd==0x28;
        d[3]=pair?(byte)c.id:(byte)a0;
        d[4]=pair?(byte)((c.id>>>8)&0xF0):(byte)a1;
        d[5]=(byte)((cmd==0x22) ? a2 : (cmd==0x12 || cmd==0x13 || cmd==0x1E || cmd==0x1F ? 0:0x83));
        d[6]=(byte)c.txCount; d[7]=(byte)param;
        int seed8=c.seed&255;
        d[8]=(byte)(seed8 ^ (c.id>>>16));
        d[9]=(byte)seed8;
        d[10]=(byte)(c.seed>>>8); d[11]=(byte)c.seed;
        int crc=crc16(Arrays.copyOf(d,12),c.seed^0xFFFF);
        d[12]=(byte)(crc>>>8); d[13]=(byte)crc; // upstream to_bytes(2) big endian
        int crc2=crc16(d,0xA5BE);
        byte[] raw=new byte[26];
        raw[0]=0x77; raw[1]=(byte)0xF8;
        System.arraycopy(V1_FIXED,0,raw,2,V1_FIXED.length);
        byte[] tail=new byte[16];
        System.arraycopy(d,0,tail,0,14);
        tail[14]=(byte)(crc2>>>8); tail[15]=(byte)crc2;
        // bit reversal is per-byte, not byte-order reversal.
        for(int i=0;i<16;i++) tail[i]=(byte)(Integer.reverse((i<14?d[i]:(i==14?(crc2>>>8):crc2))&255)>>>24);
        byte[] whitened=whiten(tail,0x0C);
        System.arraycopy(whitened,0,raw,10,16);
        return raw;
    }

    /** Validate/decode a BLE ADV payload. Only recognized FanLamp Pro advertisements are returned. */
    public static Decoded decode(byte[] raw) {
        if(raw==null || raw.length!=26) return null;
        if(ub(raw[0])==0xF0 && ub(raw[1])==0x08) return decodeV2(raw);
        if(ub(raw[0])==0x77 && ub(raw[1])==0xF8) return decodeV1(raw);
        return null;
    }

    private static Decoded decodeV2(byte[] raw) {
        int seed=ub(raw[22]) | (ub(raw[23])<<8);
        int crc=ub(raw[24]) | (ub(raw[25])<<8);
        if(crc16(Arrays.copyOfRange(raw,2,24),seed^0xFFFF)!=crc)return null;
        byte[] p=new byte[22];
        p[0]=raw[2];p[1]=raw[3];
        int salt=(p[1]&3)<<5;
        for(int i=2;i<20;i++)p[i]=(byte)(raw[i+2]^XBOXES[((seed+i-2+9)&31)+salt]^(seed&255));
        p[20]=raw[22];p[21]=raw[23];
        if(ub(p[4])!=0 || ub(p[5])!=4)return null;
        int signature=ub(p[17]) | (ub(p[18])<<8);
        if(signature!=0 && signature!=sign(Arrays.copyOfRange(p,1,17),ub(p[3]),seed))return null;
        Decoded d=new Decoded();Config c=new Config();
        c.version=signature==0?Version.V2:Version.V3; c.signed=signature!=0;
        c.prefix=new int[]{ub(p[0]),ub(p[1]),ub(p[2])};
        c.txCount=ub(p[3]);c.seed=seed;c.id=u32le(p,6);c.index=ub(p[10]);
        d.config=c;d.command=ub(p[11]);d.param=ub(p[13]);
        d.arg0=ub(p[14]);d.arg1=ub(p[15]);d.arg2=ub(p[16]);
        return d;
    }

    private static Decoded decodeV1(byte[] raw) {
        if(!Arrays.equals(Arrays.copyOfRange(raw,2,10),V1_FIXED)) return null;
        byte[] d=whiten(Arrays.copyOfRange(raw,10,26),0x0C);
        for(int i=0;i<d.length;i++) d[i]=(byte)(Integer.reverse(ub(d[i]))>>>24);
        if(crc16(Arrays.copyOf(d,14),0xA5BE)!=((ub(d[14])<<8)|ub(d[15]))) return null;
        int seed=(ub(d[10])<<8) | ub(d[11]);
        if(crc16(Arrays.copyOf(d,12),seed^0xFFFF)!=((ub(d[12])<<8)|ub(d[13]))) return null;
        int grp=ub(d[1]) | (ub(d[2])<<8);
        Config c=new Config(); c.version=Version.V1; c.signed=false;
        c.id=(grp&0xF0FF) | ((long)((seed&255)^ub(d[8]))<<16);
        c.index=(grp&0x0F00)>>8; c.seed=seed; c.txCount=ub(d[6]);
        Decoded x=new Decoded(); x.config=c;x.command=ub(d[0]);x.param=ub(d[7]);
        x.arg0=ub(d[3]);x.arg1=ub(d[4]);x.arg2=ub(d[5]);
        return x;
    }

    public static byte[] payloadFromScanRecord(byte[] scan) {
        if(scan==null)return null;
        for(int i=0;i<scan.length;) {
            int len=scan[i]&255;
            if(len==0 || i+len>=scan.length)break;
            if((scan[i+1]&255)==0x03 && len==27) {
                return Arrays.copyOfRange(scan,i+2,i+28);
            }
            i+=len+1;
        }
        return null;
    }

    public static byte[] parseHex(String v) {
        String s=v.replaceAll("[^0-9a-fA-F]","");
        if(s.length()%2!=0) throw new IllegalArgumentException("Hexadecimal incompleto");
        byte[] b=new byte[s.length()/2];
        for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);
        return b;
    }
    public static String hex(byte[] b) {
        StringBuilder s=new StringBuilder();
        for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02X",v&255));
        return s.toString();
    }
    public static String hexId(long id) { return String.format(java.util.Locale.ROOT,"%08X",id & 0xFFFFFFFFL); }
    private static int ub(byte b) { return b&255; }
    private static long u32le(byte[] b,int pos) {return (long)ub(b[pos])|((long)ub(b[pos+1])<<8)|((long)ub(b[pos+2])<<16)|((long)ub(b[pos+3])<<24);}

    private static int crc16(byte[] bytes, int seed) {
        int c=seed&0xFFFF;
        for(byte v:bytes){c ^= ub(v)<<8;for(int i=0;i<8;i++)c=((c&0x8000)!=0 ? (c<<1)^0x1021:c<<1)&0xFFFF;}
        return c;
    }
    private static byte[] whiten(byte[] bytes,int seed) {
        byte[] b=bytes.clone();int r=seed;
        for(int i=0;i<b.length;i++){
            int x=0;
            for(int j=0;j<8;j++){
                r<<=1;
                if((r&0x80)!=0){r^=0x11;x|=1<<j;}
                r&=0x7F;
            }
            b[i]=(byte)(ub(b[i])^x);
        }
        return b;
    }
    private static int sign(byte[] plain16,int tx,int seed) {
        try {
            byte[] key={(byte)seed,(byte)(seed>>>8),(byte)tx,0x0D,(byte)0xBF,(byte)0xE6,0x42,0x68,0x41,(byte)0x99,0x2D,0x0F,(byte)0xB0,0x54,(byte)0xBB,0x16};
            Cipher aes=Cipher.getInstance("AES/ECB/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"));
            byte[] result=aes.doFinal(plain16);
            int n=ub(result[0]) | (ub(result[1])<<8);
            return n==0?0xFFFF:n;
        } catch(Exception e){throw new IllegalStateException("Fallo AES",e);}
    }
}
