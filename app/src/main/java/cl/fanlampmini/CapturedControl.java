package cl.fanlampmini;

/**
 * Local controller profiles decoded from the user's own FanLamp Pro HCI capture.
 * The sample bytes are provided only to reconstruct the controller profile;
 * outgoing commands are newly encoded with incremented counters.
 *
 * Based on the FanLamp codecs published by NicoIIT/ha-ble-adv (MIT).
 */
public final class CapturedControl {
    private CapturedControl() {}

    // Real advertising payloads (26 data bytes after GAP flags + AD type 0x03).
    public static final String V3_SAMPLE =
        "F0082082363CFD5F5633CB4A1945770F9180FFEE7EC6532B22E1";
    public static final String V1_SAMPLE =
        "77F8B65F2B5E00FC3151D01D5208240A5BFC8D6AF49C348A68DE";

    public static FanLampCodec.Config v3() {
        FanLampCodec.Decoded decoded = FanLampCodec.decode(FanLampCodec.parseHex(V3_SAMPLE));
        if (decoded == null || decoded.config.version != FanLampCodec.Version.V3
                || decoded.config.id != 0x13A252E5L || decoded.config.index != 3
                || decoded.config.seed != 0x2B53
                || decoded.config.prefix[0] != 0x20 || decoded.config.prefix[1] != 0x82) {
            throw new IllegalStateException("Captured FanLamp Pro V3 packet failed validation");
        }
        FanLampCodec.Config c = decoded.config;
        c.txCount = 251; // Last V3 transmission observed in this capture.
        return c;
    }

    public static FanLampCodec.Config v1() {
        FanLampCodec.Decoded decoded = FanLampCodec.decode(FanLampCodec.parseHex(V1_SAMPLE));
        if (decoded == null || decoded.config.version != FanLampCodec.Version.V1
                || decoded.config.id != 0x00AC50E5L || decoded.config.index != 3) {
            throw new IllegalStateException("Captured FanLamp Pro V1 packet failed validation");
        }
        FanLampCodec.Config c = decoded.config;
        c.txCount = 236; // Last V1 transmission observed in this capture.
        return c;
    }
}
