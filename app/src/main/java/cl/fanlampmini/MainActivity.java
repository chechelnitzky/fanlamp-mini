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
import android.content.ClipData;
import android.content.ClipboardManager;
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
    private TextView status, detected, scanDetails, pairingDetails;
    private LinearLayout page;
    private boolean scanning, sequenceRunning;
    private int bleTotal, bleUnique;
    private final java.util.LinkedHashSet<String> scanSeen = new java.util.LinkedHashSet<>();
    private final ArrayList<String> scanSamples = new ArrayList<>();
    private FanLampCodec.Config[] trialProfiles;

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
        row(button("Escanear señales BLE (25 s)",this::toggleScan),button("Copiar diagnóstico",this::copyScanDetails));
        page.addView(text("El escáner muestra anuncios BLE cercanos aunque no sean compatibles. El ventilador normalmente NO aparece, porque solo recibe señales. Un control físico podría ser RF o usar otro protocolo.",12,false));
        detected=text("Sin iniciar escaneo.",13,false);page.addView(detected);
        scanDetails=text("Sin datos todavía.",11,false);scanDetails.setTextIsSelectable(true);page.addView(scanDetails);
        rawField=field("","Anuncio BLE HEX (opcional)");page.addView(rawField);
        page.addView(button("Interpretar paquete HEX",this::parsePasted));

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
        page.addView(text("Prepara el ventilador para emparejar y pulsa una sola vez. Se envía inmediatamente, sin ventana de confirmación. El mensaje «Orden enviada» no significa que el ventilador la haya aceptado.",12,false));
        page.addView(button("Emparejar ahora (1 toque / protocolo seleccionado)",this::askPair));
        section("4. Prueba automática (V3 → V2 → V1)");
        page.addView(text("Pulsa el primer botón justo al restablecer electricidad. La app generará un solo ID de prueba para las tres variantes y enviará 3 órdenes consecutivas. Si la luz parpadea, hubo una reacción; no hay confirmación por Bluetooth. ATENCIÓN: podría reemplazar el emparejamiento anterior de algunos ventiladores.",12,false));
        page.addView(button("Emparejar V3 + V2 + V1 (1 toque)",this::autoPair));
        page.addView(button("Probar encendido de luz con V3 + V2 + V1",this::autoLight));
        row(button("Probar solo V3",()->testVersion(FanLampCodec.Version.V3)),
            button("Probar solo V2",()->testVersion(FanLampCodec.Version.V2)),
            button("Probar solo V1",()->testVersion(FanLampCodec.Version.V1)));
        pairingDetails=text("Sin prueba automática. Probar luz solo después de emparejar; cada variante usa la misma configuración de prueba.",12,false);
        page.addView(pairingDetails);
        page.addView(text("Versión de diagnóstico 0.2.0: sin horarios todavía. El escaneo desde este mismo teléfono no permite leer las emisiones de FanLamp Pro en ese teléfono.",12,false));
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
        // Enviar de inmediato: los equipos pueden tener una ventana de emparejamiento breve.
        if(!ready() || !saveFields(false))return;
        send(FanLampCodec.pair(config));
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
    // Serial commands: BLE Advertiser can only transmit one legacy payload at a time.
    private static final FanLampCodec.Version[] ORDER={
        FanLampCodec.Version.V3, FanLampCodec.Version.V2, FanLampCodec.Version.V1
    };
    private FanLampCodec.Config makeProfile(FanLampCodec.Version v) {
        FanLampCodec.Config c=config.copy();
        c.version=v;
        c.signed=v==FanLampCodec.Version.V3;
        c.prefix=v==FanLampCodec.Version.V3?new int[]{0x20,0x80,0}:new int[]{0x10,0x80,0};
        // The V1 encoder only uses the lowest 24 bits and a 4-bit index.
        if(v==FanLampCodec.Version.V1){c.id &= 0x00FFFFFFL;c.index &= 0x0F;}
        return c;
    }
    private void profilesFromCurrent(){
        trialProfiles=new FanLampCodec.Config[]{makeProfile(ORDER[0]),makeProfile(ORDER[1]),makeProfile(ORDER[2])};
    }
    private void autoPair(){
        if(sequenceRunning){inform("Ya está funcionando una secuencia Bluetooth.");return;}
        if(!ready())return;
        // All three codecs must share the SAME control ID, as in the original FanLamp Pro app.
        // Generate a V1-compatible ID (24 bits) so a single ID works with all three.
        FanLampCodec.Config shared=FanLampCodec.fresh(FanLampCodec.Version.V1);
        shared.version=FanLampCodec.Version.V3;
        shared.signed=true;
        shared.prefix=new int[]{0x20,0x80,0};
        config=shared;persist();populate();
        profilesFromCurrent();
        pairingDetails.setText("Nuevo control de prueba ID "+FanLampCodec.hexId(config.id)+". Intentando 3 protocolos. Si el ventilador responde, conserva este ID.");
        sequenceRunning=true;
        cycle(0,true);
    }
    private void autoLight(){
        if(sequenceRunning){inform("Ya está funcionando una secuencia Bluetooth.");return;}
        if(!ready())return;
        if(trialProfiles==null){
            if(!saveFields(false))return;
            profilesFromCurrent();
        }
        sequenceRunning=true;
        pairingDetails.setText("Probando encendido de la luz con V3, V2 y V1. Si responde, usa los botones individuales para averiguar cuál fue.");
        cycle(0,false);
    }
    private void testVersion(FanLampCodec.Version v){
        if(sequenceRunning){inform("Espera a que termine la prueba automática.");return;}
        if(!ready())return;
        if(trialProfiles==null){
            if(!saveFields(false))return;
            profilesFromCurrent();
        }
        FanLampCodec.Config chosen=null;
        for(FanLampCodec.Config c:trialProfiles)if(c.version==v){chosen=c;break;}
        if(chosen==null)return;
        config=chosen.copy();persist();populate();
        pairingDetails.setText("Enviando «Luz encender» SOLO con "+v+". Si responde, esta es la variante que debes usar.");
        send(FanLampCodec.light(config,true));
        for(int i=0;i<trialProfiles.length;i++)if(trialProfiles[i].version==v)trialProfiles[i]=config.copy();
    }
    private void cycle(int pos,boolean pairing){
        if(!sequenceRunning)return;
        if(pos>=ORDER.length){
            sequenceRunning=false;
            pairingDetails.setText(pairing?"Enviadas 3 variantes de emparejamiento. El ventilador no confirma cuál aceptó; prueba los botones «Probar solo V3/V2/V1».":
                    "Se enviaron 3 órdenes de encender luz. Si una funcionó, prueba cada variante individual para identificarla.");
            inform("Secuencia terminada (sin confirmación del ventilador).");
            return;
        }
        FanLampCodec.Config c=trialProfiles[pos];
        String label=(pairing?"Emparejando ":"Luz ON ")+c.version+" ("+(pos+1)+"/3)";
        pairingDetails.setText(label+" · protocolo BLE ADV");
        byte[] packet=pairing?FanLampCodec.pair(c):FanLampCodec.light(c,true);
        sender.send(packet,750,msg->runOnUiThread(()->{
            if(!sequenceRunning)return;
            if(msg.startsWith("Orden enviada")){
                handler.postDelayed(()->cycle(pos+1,pairing),120);
            }else if(msg.startsWith("Bluetooth está transmitiendo")){
                inform(label+" — enviando anuncio...");
            }else{
                sequenceRunning=false;
                pairingDetails.setText("Error en "+c.version+": "+msg);
                inform("Secuencia interrumpida: "+msg);
            }
        }));
    }
    private String adTypes(byte[] raw){
        if(raw==null)return "?";
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<raw.length;){
            int len=raw[i]&255;
            if(len==0||i+len>=raw.length)break;
            if(sb.length()>0)sb.append(",");
            sb.append(String.format(Locale.ROOT,"%02X",raw[i+1]&255));
            i+=len+1;
        }
        return sb.toString();
    }
    private void copyScanDetails(){
        if(scanSamples.isEmpty()){inform("Primero escanea las señales BLE durante 25 segundos.");return;}
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null)return;
        String diagnostic="FanLamp Mini 0.2.0. "+bleTotal+" anuncios / "+bleUnique+" contenidos distintos.\n"+
                android.text.TextUtils.join("\n",scanSamples);
        clipboard.setPrimaryClip(ClipData.newPlainText("BLE diagnóstico (revisar antes de compartir)",diagnostic));
        inform("Diagnóstico copiado. Revísalo antes de compartirlo; puede incluir identificadores de dispositivos cercanos.");
    }
    private void toggleScan(){
        if(scanning){stopScan();return;}
        if(!ready())return;
        scanner=bluetooth.getBluetoothLeScanner();
        if(scanner==null){inform("No fue posible iniciar el escaneo BLE.");return;}
        bleTotal=0;bleUnique=0;scanSeen.clear();scanSamples.clear();
        detected.setText("Preparado para buscar señales BLE cercanas.");
        scanDetails.setText("Escaneando...");
        scanCallback=new ScanCallback(){
            @Override public void onScanResult(int callbackType,ScanResult result){
                if(result.getScanRecord()==null)return;
                byte[] raw=result.getScanRecord().getBytes();
                if(raw==null||raw.length==0)return;
                byte[] data=FanLampCodec.payloadFromScanRecord(raw);
                FanLampCodec.Decoded found=FanLampCodec.decode(data);
                runOnUiThread(()->{
                    if(!scanning)return;
                    bleTotal++;
                    String hex=FanLampCodec.hex(raw);
                    if(scanSeen.add(hex)){
                        bleUnique++;
                        String observation="RSSI "+result.getRssi()+" dBm; tipos AD ["+adTypes(raw)+"]; HEX: "+hex;
                        if(scanSamples.size()>=12)scanSamples.remove(0);
                        scanSamples.add(observation);
                    }
                    detected.setText("Anuncios BLE observados: "+bleTotal+" · distintos: "+bleUnique+" · compatibles: "+(found!=null?"sí":"aún no"));
                    if(bleUnique>0)scanDetails.setText(android.text.TextUtils.join("\n\n",scanSamples));
                    if(found!=null){
                        stopScan();
                        detected.setText("Señal compatible: "+found.config.version+" ID "+FanLampCodec.hexId(found.config.id));
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Se detectó un anuncio FanLamp compatible")
                            .setMessage("Detecté un paquete FanLamp. ¿Quieres reutilizar este ID y esta clave? Hazlo solo si procede de un control que ya maneja TU ventilador.")
                            .setNegativeButton("No",null)
                            .setPositiveButton("Usar datos",(d,w)->{
                                config=found.config;persist();populate();trialProfiles=null;
                                inform("Datos cargados desde un anuncio BLE.");
                            }).show();
                    }
                });
            }
            @Override public void onScanFailed(int error){
                runOnUiThread(()->{stopScan();inform("Falló escaneo Android BLE: "+error);});
            }
        };
        try{
            scanning=true;
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            inform("Escaneando BLE durante 25 s. Pulsa algunos botones del control físico. No esperes que aparezca el ventilador.");
            handler.postDelayed(this::stopScan,25000);
        }catch(Exception e){scanning=false;inform("No se pudo escanear: "+e.getMessage());}
    }
    private void stopScan(){
        if(scanning && scanner!=null && scanCallback!=null){
            try{scanner.stopScan(scanCallback);}catch(Exception ignored){}
            scanning=false;
            inform("Escaneo finalizado: "+bleTotal+" anuncios. Que no aparezca tu mando no demuestra que esté averiado: podría no ser BLE.");
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
