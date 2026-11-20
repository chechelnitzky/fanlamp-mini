package cl.fanlampmini;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;

/**
 * Daily-use screen: restrained premium design, actual working Bluetooth commands.
 * Status reflects transmissions, not sensor feedback from the fan/light.
 */
public final class HomeActivity extends Activity {
    private SharedPreferences state;
    private TextView feedback, lightStatus, fanStatus, brightValue, schedulesPreview;
    private Button lightPower,fanPower;
    private final Button[] tempButtons=new Button[3];
    private final Button[] speedButtons=new Button[7];
    private FanDialView dial;
    private boolean lightOn;
    private int brightness,temperature,fanSpeed,lastFanSpeed;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        UiKit.darkBars(this);
        state=getSharedPreferences("fanlamp_home",MODE_PRIVATE);
        lightOn=state.getBoolean("light_on",false);
        brightness=state.getInt("brightness",70);
        temperature=state.getInt("temperature",1);
        fanSpeed=state.getInt("fan_speed",0);
        lastFanSpeed=state.getInt("last_speed",2);
        draw();
        permissions();
        ScheduleManager.restore(this);
    }
    private void permissions(){
        if(Build.VERSION.SDK_INT>=31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED
            ||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED
            ||checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_SCAN},30);
        }else if(Build.VERSION.SDK_INT<31 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},30);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){
        super.onRequestPermissionsResult(code,perms,grants);
        if(code==30)feedback.setText(BleController.get(this).ready()?
            "Bluetooth autorizado · listo para emitir órdenes":
            "Activa Bluetooth y autoriza los permisos para controlar el dispositivo.");
    }
    private void persist(){
        state.edit().putBoolean("light_on",lightOn).putInt("brightness",brightness)
            .putInt("temperature",temperature).putInt("fan_speed",fanSpeed)
            .putInt("last_speed",lastFanSpeed).apply();
    }
    private LinearLayout.LayoutParams full(int top){
        LinearLayout.LayoutParams p=UiKit.lp(this,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=UiKit.dp(this,top);return p;
    }
    private LinearLayout.LayoutParams weight(){
        return new LinearLayout.LayoutParams(0,UiKit.dp(this,46),1);
    }
    private void draw(){
        // v0.5: everything fits in ONE view. No ScrollView and no vertical swiping.
        LinearLayout root=UiKit.column(this);
        root.setBackgroundColor(UiKit.BG);
        root.setPadding(UiKit.dp(this,14),UiKit.dp(this,7),UiKit.dp(this,14),UiKit.dp(this,7));
        setContentView(root);UiKit.applySafeInsets(root);

        LinearLayout head=UiKit.row(this);
        LinearLayout words=UiKit.column(this);
        words.addView(UiKit.label(this,"FANLAMP MINI",10,UiKit.MINT,true));
        words.addView(UiKit.title(this,"Tu ambiente.",24));
        head.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button gear=UiKit.button(this,"⚙",false,()->startActivity(new Intent(this,MainActivity.class)));
        gear.setContentDescription("Ajustes avanzados Bluetooth");
        head.addView(gear,UiKit.lp(this,45,39));
        root.addView(head);

        feedback=UiKit.label(this,"Bluetooth listo · Control local",11,UiKit.SUB,false);
        feedback.setSingleLine(true);
        feedback.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(feedback,full(5));
        LinearLayout light=UiKit.card(this);
        light.setPadding(UiKit.dp(this,13),UiKit.dp(this,10),UiKit.dp(this,13),UiKit.dp(this,10));
        root.addView(light,full(7));
        LinearLayout lightHeader=UiKit.row(this);
        LinearLayout lightText=UiKit.column(this);
        lightText.addView(UiKit.label(this,"LUZ",11,UiKit.MINT,true));
        lightStatus=UiKit.label(this,"Último ajuste guardado",13,UiKit.TEXT,true);
        lightText.addView(lightStatus);
        lightHeader.addView(lightText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        lightPower=UiKit.button(this,"",true,this::toggleLight);
        lightPower.setContentDescription("Encender o apagar la luz");
        lightHeader.addView(lightPower,UiKit.lp(this,94,36));
        light.addView(lightHeader);

        LinearLayout dimTitle=UiKit.row(this);
        dimTitle.addView(UiKit.label(this,"Intensidad",14,UiKit.SUB,false),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        brightValue=UiKit.title(this,""+brightness+"%",16);
        dimTitle.addView(brightValue);
        light.addView(dimTitle,full(8));
        SeekBar bar=UiKit.slider(this,brightness);
        bar.setMin(1);
        bar.setContentDescription("Control de intensidad de la luz");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onProgressChanged(SeekBar b,int value,boolean user){
                brightness=Math.max(1,value);brightValue.setText(brightness+"%");
            }
            @Override public void onStopTrackingTouch(SeekBar b){
                lightOn=true;persist();refreshLight();
                feedback.setText("Enviando intensidad y temperatura...");
                BleController.get(HomeActivity.this).lightLevel(brightness,temperature,HomeActivity.this::result);
            }
        });
        light.addView(bar,full(0));
        light.addView(UiKit.label(this,"Temperatura de luz",12,UiKit.SUB,false),full(2));
        LinearLayout choice=UiKit.row(this);
        String[] modes={"Cálida","Neutra","Fría"};
        for(int i=0;i<3;i++){
            final int mode=i;
            Button b=UiKit.button(this,modes[i],false,()->selectTemp(mode));
            tempButtons[i]=b;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,UiKit.dp(this,34),1);
            p.setMargins(UiKit.dp(this,3),0,UiKit.dp(this,3),0);
            choice.addView(b,p);
        }
        light.addView(choice,full(4));

        LinearLayout fan=UiKit.card(this);
        fan.setPadding(UiKit.dp(this,13),UiKit.dp(this,10),UiKit.dp(this,13),UiKit.dp(this,10));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1);
        fp.topMargin=UiKit.dp(this,8);
        root.addView(fan,fp);
        LinearLayout fanHeader=UiKit.row(this);
        LinearLayout fanText=UiKit.column(this);
        fanText.addView(UiKit.label(this,"VENTILADOR",11,UiKit.MINT,true));
        fanStatus=UiKit.label(this,"Velocidad 0 / 6",13,UiKit.TEXT,true);fanText.addView(fanStatus);
        fanHeader.addView(fanText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        fanPower=UiKit.button(this,"",true,this::toggleFan);
        fanHeader.addView(fanPower,UiKit.lp(this,94,36));
        fan.addView(fanHeader);
        dial=new FanDialView(this);
        dial.setSpeed(fanSpeed);
        dial.setListener(this::selectSpeed);
        dial.setContentDescription("Selector circular de velocidades 0 a 6");
        LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1);
        d.topMargin=UiKit.dp(this,1);
        fan.addView(dial,d);

        LinearLayout chips=UiKit.row(this);
        for(int i=0;i<=6;i++){
            final int speed=i;
            Button b=UiKit.button(this,String.valueOf(i),false,()->selectSpeed(speed));
            b.setContentDescription(i==0?"Apagar ventilador":"Velocidad "+i);
            speedButtons[i]=b;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,UiKit.dp(this,32),1);
            p.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);chips.addView(b,p);
        }
        fan.addView(chips);

        // Compact schedules entry stays visible at the bottom, even on smaller phones.
        schedulesPreview=UiKit.label(this,"",12,UiKit.SUB,false);
        schedulesPreview.setSingleLine(true);
        schedulesPreview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(schedulesPreview,full(5));
        Button schedules=UiKit.button(this,"◷   Horarios y automatizaciones",true,
            ()->startActivity(new Intent(this,SchedulesActivity.class)));
        root.addView(schedules,UiKit.lp(this,ViewGroup.LayoutParams.MATCH_PARENT,40));
        refreshLight();refreshFan();refreshSchedules();
    }
    private void result(boolean transmitted,String msg){
        if(feedback!=null)feedback.setText(transmitted?
            "Orden Bluetooth emitida · sin confirmación del ventilador":
            "No se pudo emitir: "+msg);
    }
    private void toggleLight(){
        boolean target=!lightOn;
        feedback.setText("Transmitiendo luz...");
        BleController.get(this).light(target,(ok,msg)->{
            if(ok){lightOn=target;persist();refreshLight();}
            result(ok,msg);
        });
    }
    private void selectTemp(int t){
        temperature=t;lightOn=true;persist();refreshLight();
        feedback.setText("Transmitiendo temperatura de luz...");
        BleController.get(this).lightLevel(brightness,temperature,this::result);
    }
    private void toggleFan(){selectSpeed(fanSpeed==0?Math.max(1,lastFanSpeed):0);}
    private void selectSpeed(int requested){
        requested=Math.max(0,Math.min(6,requested));
        final int speed=requested;
        feedback.setText("Transmitiendo velocidad "+speed+"...");
        BleController.get(this).fan(speed,(ok,msg)->{
            if(ok){fanSpeed=speed;if(speed>0)lastFanSpeed=speed;persist();refreshFan();}
            result(ok,msg);
        });
    }
    private void refreshLight(){
        if(lightStatus==null)return;
        lightStatus.setText(lightOn?
            brightness+"%  ·  "+(temperature==0?"Cálida":temperature==1?"Neutra":"Fría"):
            "Última orden: apagada");
        lightPower.setText(lightOn?"Apagar":"Encender");
        for(int i=0;i<3;i++)tempButtons[i].setBackground(
            UiKit.background(i==temperature?UiKit.DARK_MINT:UiKit.RAISED,
                UiKit.dp(this,14),i==temperature?UiKit.MINT:UiKit.STROKE));
    }
    private void refreshFan(){
        if(fanStatus==null)return;
        fanStatus.setText(fanSpeed==0?"Última orden: apagado":"Velocidad "+fanSpeed+" de 6");
        fanPower.setText(fanSpeed==0?"Encender":"Apagar");
        dial.setSpeed(fanSpeed);
        for(int i=0;i<7;i++){
            speedButtons[i].setTextColor(i==fanSpeed?UiKit.BG:UiKit.TEXT);
            speedButtons[i].setBackground(UiKit.background(i==fanSpeed?UiKit.MINT:UiKit.RAISED,
                UiKit.dp(this,12),i==fanSpeed?0:UiKit.STROKE));
        }
    }
    private void refreshSchedules(){
        if(schedulesPreview==null)return;
        List<ScheduleStore.Rule> all=ScheduleStore.all(this);
        if(all.isEmpty()){schedulesPreview.setText("Horarios · Sin automatizaciones activas");return;}
        int active=0;String closest="";
        for(ScheduleStore.Rule r:all)if(r.enabled){
            active++;
            if(closest.isEmpty())closest=ScheduleStore.time(r)+" · "+r.name;
        }
        schedulesPreview.setText(active==0?"Horarios · Todos pausados":
            active+" horario"+(active==1?"":"s")+" activo"+(active==1?"":"s")+"  ·  "+closest);
    }
    @Override protected void onResume(){
        super.onResume();
        if(state!=null){
            lightOn=state.getBoolean("light_on",lightOn);
            brightness=state.getInt("brightness",brightness);
            temperature=state.getInt("temperature",temperature);
            fanSpeed=state.getInt("fan_speed",fanSpeed);
            lastFanSpeed=state.getInt("last_speed",lastFanSpeed);
        }
        if(schedulesPreview!=null){
            refreshLight();refreshFan();refreshSchedules();
        }
    }
}
