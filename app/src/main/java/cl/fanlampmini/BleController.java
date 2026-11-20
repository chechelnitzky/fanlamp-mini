package cl.fanlampmini;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide BLE advertising queue. One physical advertising transmission at a time.
 * Maintains the two independently paired FanLamp Pro identities from the HCI capture.
 * BLE advertising has no receiver ACK, so success only means Android finished broadcasting.
 */
public final class BleController {
    public interface Listener { void complete(boolean transmitted, String message); }
    public static final class Operation {
        static final int LIGHT=1, FAN=2, CWW=3;
        final int kind, a, b;
        Operation(int kind,int a,int b){this.kind=kind;this.a=a;this.b=b;}
        public static Operation light(boolean on){return new Operation(LIGHT,on?1:0,0);}
        public static Operation fan(int speed){return new Operation(FAN,speed,0);}
        public static Operation cww(int percent,int temperature){return new Operation(CWW,percent,temperature);}
    }
    private static final class Batch {
        final List<Operation> ops; final Listener listener;
        int next=0;
        Batch(List<Operation> ops,Listener listener){this.ops=ops;this.listener=listener;}
    }
    private static BleController singleton;
    public static synchronized BleController get(Context ctx){
        if(singleton==null) singleton=new BleController(ctx.getApplicationContext());
        return singleton;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BleSender sender;
    private final SecureRandom random=new SecureRandom();
    private final ArrayDeque<Batch> pending=new ArrayDeque<>();
    private final FanLampCodec.Config v3,v1;
    private Batch active;

    private BleController(Context app){
        context=app;
        prefs=app.getSharedPreferences("fanlamp_mini",Context.MODE_PRIVATE);
        BluetoothManager manager=(BluetoothManager)app.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        sender=new BleSender(adapter);
        v3=CapturedControl.v3();v1=CapturedControl.v1();
        v3.txCount=prefs.getInt("captured_tx_v3",251)&255;
        v1.txCount=prefs.getInt("captured_tx_v1",236)&255;
    }

    public boolean ready(){
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null)return false;
        if(Build.VERSION.SDK_INT>=31){
            if(context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED
                    ||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return false;
        } else if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return false;
        try{return adapter.isEnabled() && adapter.isMultipleAdvertisementSupported();}
        catch(SecurityException e){return false;}
    }

    public void light(boolean on,Listener cb){submit(java.util.Arrays.asList(Operation.light(on)),cb);}
    public void fan(int speed,Listener cb){submit(java.util.Arrays.asList(Operation.fan(speed)),cb);}
    public void lightLevel(int percent,int temp,Listener cb){
        submit(java.util.Arrays.asList(Operation.light(true),Operation.cww(percent,temp)),cb);
    }
    public void submit(List<Operation> ops,Listener cb){
        final ArrayList<Operation> copy=new ArrayList<>(ops);
        handler.post(()->{
            if(copy.isEmpty()){if(cb!=null)cb.complete(true,"Sin acciones.");return;}
            pending.addLast(new Batch(copy,cb));
            if(active==null)startNext();
        });
    }
    private void startNext(){
        active=pending.pollFirst();
        if(active!=null)sendOperation();
    }
    private void finish(boolean success,String message){
        Batch done=active;active=null;
        if(done!=null && done.listener!=null)done.listener.complete(success,message);
        startNext();
    }
    private void sendOperation(){
        if(active==null)return;
        if(!ready()){finish(false,"Bluetooth apagado, sin permiso o publicidad BLE no admitida.");return;}
        if(active.next>=active.ops.size()){
            finish(true,"Anuncios Bluetooth emitidos; el ventilador no confirma recepción.");
            return;
        }
        Operation op=active.ops.get(active.next);
        try{
            // Advanced settings may transmit using a different sender. Reload the
            // shared counters before encoding, avoiding stale sequence values.
            v3.txCount=prefs.getInt("captured_tx_v3",v3.txCount)&255;
            v1.txCount=prefs.getInt("captured_tx_v1",v1.txCount)&255;
            // Genuine V1 packets vary the per-command 8-bit seed; V3 uses its stable seed.
            v1.seed=1+random.nextInt(255);
            byte[] first=encode(v3,op),second=encode(v1,op);
            prefs.edit().putInt("captured_tx_v3",v3.txCount)
                    .putInt("captured_tx_v1",v1.txCount).apply();
            sender.send(first,450,status->handler.post(()->{
                if(active==null)return;
                if(status.startsWith("Orden enviada")){
                    handler.postDelayed(()->sender.send(second,450,result->handler.post(()->{
                        if(active==null)return;
                        if(result.startsWith("Orden enviada")){
                            active.next++;
                            handler.postDelayed(this::sendOperation,100);
                        } else if(!result.startsWith("Bluetooth está transmitiendo")){
                            finish(false,"Error al emitir V1: "+result);
                        }
                    })),90);
                } else if(!status.startsWith("Bluetooth está transmitiendo")){
                    finish(false,"Error al emitir V3: "+status);
                }
            }));
        }catch(Exception e){finish(false,"Error de codificación BLE: "+e.getMessage());}
    }
    private static byte[] encode(FanLampCodec.Config c,Operation op){
        if(op.kind==Operation.LIGHT)return FanLampCodec.light(c,op.a!=0);
        if(op.kind==Operation.FAN)return FanLampCodec.fan(c,op.a);
        if(op.kind==Operation.CWW)return FanLampCodec.lightLevel(c,op.a,op.b);
        throw new IllegalArgumentException("Comando desconocido");
    }
}
