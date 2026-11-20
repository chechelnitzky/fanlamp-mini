package cl.fanlampmini;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.Locale;
import java.util.UUID;

/** Transmits a 26-byte FanLamp command in a complete 16-bit Service UUID BLE ADV list (AD type 0x03). */
public final class BleSender {
    public interface Listener { void result(String status); }
    private final BluetoothAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AdvertiseCallback running;
    private boolean busy;

    public BleSender(BluetoothAdapter adapter) {this.adapter=adapter;}

    public void send(byte[] payload, Listener listener) {
        if(busy){listener.result("Hay una orden Bluetooth en curso; intenta nuevamente.");return;}
        if(payload==null || payload.length!=26){listener.result("Error: longitud del paquete inválida.");return;}
        if(adapter==null || !adapter.isEnabled()){listener.result("Activa Bluetooth primero.");return;}
        if(!adapter.isMultipleAdvertisementSupported()){
            listener.result("Este teléfono no admite publicidad BLE necesaria para controlar el ventilador.");return;
        }
        BluetoothLeAdvertiser advertiser=adapter.getBluetoothLeAdvertiser();
        if(advertiser==null){listener.result("El transmisor BLE no está disponible.");return;}

        // 13 UUIDs x 2 bytes = 26 bytes + 2-byte AD header + 3-byte GAP flags = 31-byte legacy limit.
        AdvertiseData.Builder data=new AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false);
        for(int i=0;i<26;i+=2){
            int shortUuid=(payload[i]&255)|((payload[i+1]&255)<<8);
            UUID uuid=UUID.fromString(String.format(Locale.ROOT,"0000%04x-0000-1000-8000-00805f9b34fb",shortUuid));
            data.addServiceUuid(new ParcelUuid(uuid));
        }
        AdvertiseSettings settings=new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build();

        busy=true;
        running=new AdvertiseCallback(){
            @Override public void onStartSuccess(AdvertiseSettings actualSettings){
                listener.result("Bluetooth está transmitiendo. Comprueba si respondió el ventilador.");
                handler.postDelayed(()->stop(advertiser,listener),1100);
            }
            @Override public void onStartFailure(int errorCode){
                busy=false;running=null;
                listener.result("No se pudo emitir la orden BLE. Error Android: "+errorCode+
                    (errorCode==AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ? " (datos exceden el límite del chip)" : ""));
            }
        };
        try{advertiser.startAdvertising(settings,data.build(),running);}
        catch(Exception e){busy=false;running=null;listener.result("Error BLE: "+e.getMessage());}
    }

    private void stop(BluetoothLeAdvertiser advertiser,Listener listener){
        if(running!=null){
            try{advertiser.stopAdvertising(running);}catch(Exception ignored){}
            running=null;
        }
        busy=false;
        listener.result("Orden enviada (sin confirmación del receptor). Prueba los botones.");
    }

    public void close(){
        handler.removeCallbacksAndMessages(null);
        if(running!=null && adapter!=null){
            BluetoothLeAdvertiser a=adapter.getBluetoothLeAdvertiser();
            if(a!=null)try{a.stopAdvertising(running);}catch(Exception ignored){}
        }
        running=null;busy=false;
    }
}
