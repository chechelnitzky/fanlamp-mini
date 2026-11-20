import cl.fanlampmini.FanLampCodec;

/** Runs with regular javac/java, no Android SDK needed. */
public final class CodecSelfTest {
    static void check(boolean ok, String message) {if(!ok)throw new AssertionError(message);}
    static void vector(FanLampCodec.Version version,long id,int index,int seed,int tx,int cmd,String expected) {
        FanLampCodec.Config c=FanLampCodec.fresh(version);
        c.version=version;c.id=id;c.index=index;c.seed=seed;c.txCount=tx-1;
        c.prefix=version==FanLampCodec.Version.V3?new int[]{0x20,0x80,0}:new int[]{0x10,0x80,0};
        c.signed=version==FanLampCodec.Version.V3;
        byte[] out=FanLampCodec.encode(c,cmd,0,0,0,0);
        check(FanLampCodec.hex(out).equalsIgnoreCase(expected.replace(".","")), "Mismatch: "+version+" expected="+expected+" actual="+FanLampCodec.hex(out));
        FanLampCodec.Decoded d=FanLampCodec.decode(out);
        check(d!=null && d.config.id==id && d.command==cmd && d.config.seed==seed, "Roundtrip failed: "+version);
    }
    public static void main(String[] args) {
        vector(FanLampCodec.Version.V3,0xD2135C22L,2,0x2B53,117,0x28,"F0.08.20.80.B8.01.E1.22.C6.F2.D3.A7.67.44.A4.9F.67.F6.B6.78.45.8B.53.2B.B9.1B");
        vector(FanLampCodec.Version.V2,0xD2135C22L,2,0x2B53,117,0x28,"F0.08.10.80.B8.01.E1.22.C6.F2.D3.A7.67.44.A4.9F.67.F6.B6.A2.22.8B.53.2B.E1.B5");
        vector(FanLampCodec.Version.V1,0x003D5022L,2,0x0006,102,0x28,"77.F8.B6.5F.2B.5E.00.FC.31.51.CC.FE.D2.4C.2E.0A.5D.FC.AD.C3.F4.35.F3.2B.AF.85");
        check(FanLampCodec.decode(FanLampCodec.parseHex("02 01 19 1B 03 F0 08 20 80 B8 65 E1 22 C6 F2 D3 A7 67 7D A4 9F 67 F6 B6 6B 65 8B 53 2B 2E AA"))==null,
                "Raw scan record is not a standalone payload");
        byte[] rawScan=FanLampCodec.parseHex("02 01 19 1B 03 F0 08 20 80 B8 65 E1 22 C6 F2 D3 A7 67 7D A4 9F 67 F6 B6 6B 65 8B 53 2B 2E AA");
        byte[] extracted=FanLampCodec.payloadFromScanRecord(rawScan);
        check(extracted!=null && extracted.length==26,"Could not extract BLE advertising structure");
        FanLampCodec.Decoded recorded=FanLampCodec.decode(extracted);
        check(recorded!=null && recorded.command==0x11 && recorded.config.id==0xD2135C22L,"Could not decode original V3 packet");
        System.out.println("OK: 3 official encode vectors, 3 roundtrips, 1 genuine scan packet");
    }
}
