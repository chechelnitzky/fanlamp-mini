package cl.fanlampmini;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.Locale;

/** FanLamp Mini / primera versión: manual Bluetooth BLE control only. No scheduled automation yet. */
public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothAdapter bluetooth;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BleSender sender;
    private SharedPreferences prefs;
    private FanLampCodec.Config config;
    private Spinner protocol;
    private EditText idField,indexField,seedField,prefixField,rawField;
    private TextView status, detected;
    private LinearLayout page;
    private boolean scanning;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        prefs=getSharedPreferences("fanlamp_mini",MODE_PRIVATE);
        BluetoothManager manager=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bluetooth=manager==null?null:manager.getAdapter();
        sender=new BleSender(bluetooth);
        readConfig();
        draw();
        requestBluetoothPermissions();
    }
    private void readConfig(){
        String name=prefs.getString("v",null);
        if(name==null){config=FanLampCodec.fresh(FanLampCodec.Version.V3);persist();return;}
        try{
            config=FanLampCodec.fresh(FanLampCodec.Version.valueOf(name));
            config.id=Long.parseUnsignedLong(prefs.getString("id","0"),16);
            config.index=prefs.getInt("index",0);
            config.seed=prefs.getInt("seed",0x1234);
            config.txCount=prefs.getInt("tx",0);
            config.signed=prefs.getBoolean("signed",config.version==FanLampCodec.Version.V3);
            String[] chunks=prefs.getString("prefix","20 80 00").split(" ");
            if(chunks.length==3){config.prefix=new int[]{Integer.parseInt(chunks[0],16),Integer.parseInt(chunks[1],16),Integer.parseInt(chunks[2],16)};}
        } catch(Exception e){config=FanLampCodec.fresh(FanLampCodec.Version.V3);}
    }
    private void persist(){
        prefs.edit().putString("v",config.version.name())
            .putString("id",FanLampCodec.hexId(config.id)).putInt("index",config.index)
            .putInt("seed",config.seed).putInt("tx",config.txCount)
            .putBoolean("signed",config.signed)
            .putString("prefix",String.format(Locale.ROOT,"%02X %02X %02X",config.prefix[0],config.prefix[1],config.prefix[2]))
            .apply();
    }
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(30,39,58));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(7),0,dp(6));
        return t;
    }
    private EditText field(String value,String hint){
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setHint(hint);
        e.setTextSize(16);e.setPadding(dp(8),dp(5),dp(8),dp(5));
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        return e;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void section(String title){page.addView(text(title,19,true));}
    private void row(Button...buttons){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
        for(Button b:buttons)r.addView(b,new LinearLayout.LayoutParams(0,dp(55),1));
        page.addView(r);
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(247,249,252));
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(24),dp(18),dp(22));
        scroll.addView(page);setContentView(scroll);
        page.addView(text("FanLamp Mini",28,true));
        page.addView(text("Prueba Bluetooth • no necesita internet ni hardware adicional",13,false));
        status=text("Preparado para probar. No hay conexión confirmada con el ventilador.",14,false);
        status.setTextColor(Color.rgb(35,95,115));page.addView(status);

        section("1. Control");
        row(button("💡 Luz encender",()->sendLight(true)),button("Luz apagar",()->sendLight(false)));
        page.addView(text("Velocidad del ventilador",15,true));
        row(button("Apagar",()->sendFan(0)),button("1",()->sendFan(1)),button("2",()->sendFan(2)),button("3",()->sendFan(3)));
        row(button("4",()->sendFan(4)),button("5",()->sendFan(5)),button("6",()->sendFan(6)));

        section("2. Identificación del control");
        page.addView(text("Primero intenta capturar un paquete de un control cercano. Alternativamente puedes emparejar un identificador de prueba con tu ventilador.",13,false));
        row(button("Buscar comandos BLE",this::toggleScan),button("Pegar paquete HEX",this::parsePasted));
        rawField=field("","Anuncio BLE HEX de 26 bytes (opcional)");page.addView(rawField);
        detected=text("No se han detectado comandos compatibles.",13,false);page.addView(detected);

        section("3. Datos del protocolo");
        page.addView(text("Variante FanLamp Pro",13,false));
        protocol=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"V3 (firmado)","V2","V1"});
        protocol.setAdapter(a);protocol.setSelection(config.version==FanLampCodec.Version.V3?0:config.version==FanLampCodec.Version.V2?1:2);
        page.addView(protocol);
        page.addView(text("ID del control (8 caracteres hex)",13,false));
        idField=field(FanLampCodec.hexId(config.id),"D2135C22");page.addView(idField);
        page.addView(text("Índice del control (0–255)",13,false));
        indexField=field(String.valueOf(config.index),"0");indexField.setInputType(InputType.TYPE_CLASS_NUMBER);page.addView(indexField);
        page.addView(text("Seed / clave de sesión (4 caracteres hex)",13,false));
        seedField=field(String.format(Locale.ROOT,"%04X",config.seed),"2B53");page.addView(seedField);
        page.addView(text("Prefijo V2/V3 (6 caracteres hex)",13,false));
        prefixField=field(String.format(Locale.ROOT,"%02X%02X%02X",config.prefix[0],config.prefix[1],config.prefix[2]),"208000");page.addView(prefixField);
        row(button("Guardar configuración",()->saveFields(true)),button("Generar control nuevo",this::newConfig));
        page.addView(text("ATENCIÓN: un ID nuevo normalmente requiere emparejamiento. Eso podría hacer que el controlador antiguo deje de funcionar en algunos modelos.",13,false));
        page.addView(button("Emparejar (solo si el ventilador está en modo emparejamiento)",this::askPair));
        page.addView(text("Versión inicial: no incluye horarios todavía. Las órdenes se envían únicamente al pulsar un botón.",12,false));
        page.addView(text("Basado en NicoIIT/ha-ble-adv (licencia MIT). No es la aplicación oficial FanLamp Pro.",11,false));
    }
    private boolean saveFields(boolean show){
        try{
            int selection=protocol.getSelectedItemPosition();
            FanLampCodec.Version v=selection==0?FanLampCodec.Version.V3:selection==1?FanLampCodec.Version.V2:FanLampCodec.Version.V1;
            String id=idField.getText().toString().trim().replace("0x","").replace("0X","");
            String seed=seedField.getText().toString().trim().replace("0x","").replace("0X","");
            String prefix=prefixField.getText().toString().trim().replaceAll("[^0-9A-Fa-f]","");
            if(id.isEmpty() || id.length()>8 || seed.isEmpty() || seed.length()>4 || prefix.length()!=6)throw new IllegalArgumentException("ID / seed / prefijo hex inválidos");
            long ident=Long.parseUnsignedLong(id,16);
            int ix=Integer.parseInt(indexField.getText().toString().trim());
            int se=Integer.parseInt(seed,16);
            if(ix<0 || ix>255 || se==0 || se>0xFFF5)throw new IllegalArgumentException("Índice (0–255) o seed inválido");
            if(v==FanLampCodec.Version.V1 && (ix>15 || (ident&0xFF000000L)!=0))throw new IllegalArgumentException("V1: índice 0–15 e ID hasta 24 bits");
            if(config.version!=v){
                // When switching between V2/V3, migrate only standard prefix values.
                if(config.version==FanLampCodec.Version.V3 && v==FanLampCodec.Version.V2 && prefix.equalsIgnoreCase("208000"))prefix="108000";
                if(config.version==FanLampCodec.Version.V2 && v==FanLampCodec.Version.V3 && prefix.equalsIgnoreCase("108000"))prefix="208000";
                config.version=v;
                config.signed=v==FanLampCodec.Version.V3;
            }
            config.id=ident;config.index=ix;config.seed=se;
            config.prefix=new int[]{Integer.parseInt(prefix.substring(0,2),16),Integer.parseInt(prefix.substring(2,4),16),Integer.parseInt(prefix.substring(4,6),16)};
            prefixField.setText(prefix);
            persist();if(show)inform("Configuración guardada.");return true;
        }catch(Exception e){inform("Revisa los datos: "+e.getMessage());return false;}
    }
    private void populate(){
        protocol.setSelection(config.version==FanLampCodec.Version.V3?0:config.version==FanLampCodec.Version.V2?1:2);
        idField.setText(FanLampCodec.hexId(config.id));indexField.setText(String.valueOf(config.index));
        seedField.setText(String.format(Locale.ROOT,"%04X",config.seed));
        prefixField.setText(String.format(Locale.ROOT,"%02X%02X%02X",config.prefix[0],config.prefix[1],config.prefix[2]));
    }
    private void newConfig(){
        new AlertDialog.Builder(this).setTitle("Generar otro control")
            .setMessage("Se reemplazará el ID guardado. Anota el anterior si ya controlaba tu ventilador. ¿Continuar?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Generar",(d,w)->{
                FanLampCodec.Version v=protocol.getSelectedItemPosition()==0?FanLampCodec.Version.V3:protocol.getSelectedItemPosition()==1?FanLampCodec.Version.V2:FanLampCodec.Version.V1;
                config=FanLampCodec.fresh(v);persist();populate();inform("Nuevo control creado. Todavía NO está emparejado.");
            }).show();
    }
    private void sendLight(boolean on){
        if(!ready() || !saveFields(false))return;
        send(FanLampCodec.light(config,on));
    }
    private void sendFan(int speed){
        if(!ready() || !saveFields(false))return;
        send(FanLampCodec.fan(config,speed));
    }
    private void askPair(){
        new AlertDialog.Builder(this).setTitle("Emparejar con el ventilador")
            .setMessage("Asegúrate de saber cómo poner tu ventilador en modo emparejamiento. En algunos modelos emparejar uno nuevo puede invalidar el mando existente. No cortes la energía ni cambies ajustes eléctricos desde esta app.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Enviar señal",(d,w)->{
                if(!ready() || !saveFields(false))return;
                send(FanLampCodec.pair(config));
            }).show();
    }
    private void send(byte[] payload){
        persist();inform("Se intentará transmitir: "+FanLampCodec.hex(payload));
        sender.send(payload,msg->runOnUiThread(()->inform(msg)));
    }
    private boolean ready(){
        if(bluetooth==null){inform("Tu dispositivo no tiene adaptador Bluetooth.");return false;}
        if(!hasPermissions()){requestBluetoothPermissions();return false;}
        if(!bluetooth.isEnabled()){inform("Activa Bluetooth y prueba de nuevo.");return false;}
        return true;
    }
    private boolean hasPermissions(){
        if(Build.VERSION.SDK_INT>=31){
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void requestBluetoothPermissions(){
        if(hasPermissions())return;
        if(Build.VERSION.SDK_INT>=31){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_CONNECT},7);
        }else{
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},7);
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==7){inform(hasPermissions()?"Permisos Bluetooth autorizados.":"Necesitas autorizar Bluetooth en Ajustes para usar esta app.");}
    }
    private void toggleScan(){
        if(scanning){stopScan();return;}
        if(!ready())return;
        scanner=bluetooth.getBluetoothLeScanner();
        if(scanner==null){inform("No es posible iniciar el escaneo BLE.");return;}
        scanCallback=new ScanCallback(){
            @Override public void onScanResult(int callbackType,ScanResult result){
                if(result.getScanRecord()==null)return;
                byte[] data=FanLampCodec.payloadFromScanRecord(result.getScanRecord().getBytes());
                FanLampCodec.Decoded found=FanLampCodec.decode(data);
                if(found==null)return;
                runOnUiThread(()->{
                    if(!scanning)return;
                    stopScan();
                    detected.setText("Comando encontrado: 0x"+Integer.toHexString(found.command).toUpperCase(Locale.ROOT)+" • "+found.config.version+" • ID "+FanLampCodec.hexId(found.config.id));
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("¡Se detectó un control FanLamp!")
                        .setMessage("Puedes reutilizar el ID, seed y versión capturados para probar los botones. ¿Guardar esta configuración?")
                        .setNegativeButton("No",null)
                        .setPositiveButton("Usar este control",(d,w)->{
                            config=found.config;persist();populate();inform("Control detectado cargado.");
                        }).show();
                });
            }
            @Override public void onScanFailed(int error){runOnUiThread(()->{stopScan();inform("Falló el escaneo: "+error);});}
        };
        try{
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            scanning=true;inform("Escaneando BLE durante 25 segundos. Pulsa un mando Bluetooth cercano. Un teléfono puede no detectar sus propias señales.");
            handler.postDelayed(this::stopScan,25000);
        }catch(Exception e){inform("No se pudo escanear: "+e.getMessage());}
    }
    private void stopScan(){
        if(scanning && scanner!=null && scanCallback!=null){
            try{scanner.stopScan(scanCallback);}catch(Exception ignored){}
            scanning=false;
            inform("Escaneo terminado. Si no detectó nada, prueba con un mando externo o emparejamiento manual.");
        }
    }
    private void parsePasted(){
        try{
            byte[] data=FanLampCodec.parseHex(rawField.getText().toString());
            if(data.length!=26){
                byte[] parsed=FanLampCodec.payloadFromScanRecord(data);
                if(parsed!=null)data=parsed;
            }
            FanLampCodec.Decoded decoded=FanLampCodec.decode(data);
            if(decoded==null){inform("No se reconoció un anuncio válido FanLamp V1/V2/V3 (26 bytes). ");return;}
            config=decoded.config;persist();populate();detected.setText("Importado: "+FanLampCodec.hexId(config.id));
            inform("Configuración extraída del paquete.");
        }catch(Exception e){inform("HEX inválido: "+e.getMessage());}
    }
    private void inform(String message){if(status!=null)status.setText(message);}
    @Override protected void onDestroy(){stopScan();handler.removeCallbacksAndMessages(null);sender.close();super.onDestroy();}
}
