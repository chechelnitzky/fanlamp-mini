import cl.fanlampmini.FanLampCodec;
import cl.fanlampmini.CapturedControl;

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
        // These samples were decoded from the user's own 20 Nov HCI advertising capture.
        FanLampCodec.Config recoveredV3=CapturedControl.v3();
        check(recoveredV3.id==0x13A252E5L && recoveredV3.index==3 &&
              recoveredV3.seed==0x2B53 && recoveredV3.prefix[1]==0x82,
              "Incorrect recovered real V3 controller identity");
        recoveredV3.txCount=20; // Captured first ON command was tx=21.
        check(FanLampCodec.hex(FanLampCodec.light(recoveredV3,true)).equals(CapturedControl.V3_SAMPLE),
              "Could not reproduce the exact captured V3 packet");

        FanLampCodec.Config recoveredV1=CapturedControl.v1();
        check(recoveredV1.id==0x00AC50E5L && recoveredV1.index==3,
              "Incorrect recovered real V1 controller identity");
        recoveredV1.txCount=5; // Captured first ON command was tx=6.
        recoveredV1.seed=0x0093;
        check(FanLampCodec.hex(FanLampCodec.light(recoveredV1,true)).equals(CapturedControl.V1_SAMPLE),
              "Could not reproduce the exact captured V1 packet");

        recoveredV3.txCount=255;
        FanLampCodec.light(recoveredV3,false);
        check(recoveredV3.txCount==0,"8-bit TX counter must wrap 255 to 0");
        // Exact color/dimmer commands captured from the original FanLamp Pro UI:
        FanLampCodec.Config warmV3=CapturedControl.v3();
        warmV3.txCount=25;
        check(FanLampCodec.hex(FanLampCodec.lightCww(warmV3,0,255)).equals(
            "F00820823633FD5F5633CB4A1974770F91800041A6C6532BEF5A"),
            "V3 100% warm light did not reproduce genuine HCI advertisement");
        FanLampCodec.Config warmV1=CapturedControl.v1();
        warmV1.txCount=10; warmV1.seed=0xB9;
        check(FanLampCodec.hex(FanLampCodec.lightCww(warmV1,0,255)).equals(
            "77F8B65F2B5E00FC31515C1D5208DB0AEBFCD93EF4C8F9AA5DF1"),
            "V1 100% warm light did not reproduce genuine HCI advertisement");
        FanLampCodec.Config dimV3=CapturedControl.v3();
        dimV3.txCount=249;
        check(FanLampCodec.hex(FanLampCodec.lightCww(dimV3,0,25)).equals(
            "F008208236D3FD5F5633CB4A1974770F9180E6866CC6532B52E0"),
            "V3 dimmer 25/255 captured packet mismatch");
        FanLampCodec.Config dimV1=CapturedControl.v1();
        dimV1.txCount=234;dimV1.seed=0x5C;
        check(FanLampCodec.hex(FanLampCodec.lightCww(dimV1,0,25)).equals(
            "77F8B65F2B5E00FC31515C1D5208BC0AECFC7E99F46F2B8E1C9A"),
            "V1 dimmer 25/255 captured packet mismatch");
        for(int temp=0;temp<3;temp++){
            FanLampCodec.Config x=CapturedControl.v3();
            FanLampCodec.Decoded d=FanLampCodec.decode(FanLampCodec.lightLevel(x,35,temp));
            check(d!=null && d.command==0x21 &&
                (temp==0?d.arg1==0&&d.arg2>0:temp==2?d.arg1>0&&d.arg2==0:d.arg1>0&&d.arg2>0),
                "Light color mapping roundtrip failed: "+temp);
        }
        System.out.println("OK: original vectors + exact captured fan/light/CWW/dimmer packets + counter wrap");
    }
}
