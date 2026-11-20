package cl.fanlampmini;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Editor for a weekly rule: independent light and fan actions, either or both. */
public final class ScheduleEditorActivity extends Activity {
    private ScheduleStore.Rule rule;
    private EditText name;
    private TextView timeLabel,brightValue;
    private CheckBox applyLight,applyFan;
    private LinearLayout lightOptions,fanOptions;
    private final Button[] days=new Button[7],temps=new Button[3],fanSpeeds=new Button[6];
    private Button lightOnButton,lightOffButton,fanOnButton,fanOffButton;
    private boolean lightOn=true,fanOn=true;
    private int dayMask,brightness,temperature,speed,hour,minute;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);UiKit.darkBars(this);
        int id=getIntent().getIntExtra(ScheduleManager.EXTRA_ID,0);
        rule=ScheduleStore.find(this,id);
        if(rule==null)rule=new ScheduleStore.Rule();
        dayMask=rule.days;brightness=rule.brightness;temperature=rule.temperature;
        speed=rule.speed;hour=rule.hour;minute=rule.minute;
        lightOn=rule.lightMode!=0;fanOn=rule.fanMode!=0;
        draw();refresh();
    }
    private LinearLayout.LayoutParams full(int top){
        LinearLayout.LayoutParams p=UiKit.lp(this,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=UiKit.dp(this,top);return p;
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);
        LinearLayout root=UiKit.column(this);root.setPadding(UiKit.dp(this,20),UiKit.dp(this,24),UiKit.dp(this,20),UiKit.dp(this,38));
        scroll.addView(root);setContentView(scroll);UiKit.applySafeInsets(root);
        LinearLayout header=UiKit.row(this);
        Button back=UiKit.button(this,"‹",false,this::finish);
        back.setContentDescription("Volver");header.addView(back,UiKit.lp(this,48,48));
        TextView title=UiKit.title(this,rule.id==0?"Nuevo horario":"Editar horario",25);
        LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        t.leftMargin=UiKit.dp(this,15);header.addView(title,t);
        root.addView(header);

        LinearLayout main=UiKit.card(this);root.addView(main,full(21));
        main.addView(UiKit.label(this,"Nombre",13,UiKit.SUB,true));
        name=new EditText(this);
        name.setText(rule.id==0?"":rule.name);
        name.setHint("Ej. Dormir, mañana, luz suave");
        name.setSingleLine(true);name.setTextColor(UiKit.TEXT);
        name.setHintTextColor(UiKit.SUB);name.setTextSize(16);
        name.setPadding(UiKit.dp(this,12),UiKit.dp(this,14),UiKit.dp(this,12),UiKit.dp(this,14));
        name.setBackground(UiKit.background(UiKit.RAISED,UiKit.dp(this,13),UiKit.STROKE));
        main.addView(name,full(9));
        main.addView(UiKit.label(this,"Hora",13,UiKit.SUB,true),full(20));
        timeLabel=UiKit.title(this,"",30);
        Button timeButton=UiKit.button(this,"Cambiar hora",false,()->
            new TimePickerDialog(this,(view,h,m)->{hour=h;minute=m;timeLabel.setText(clock());},
                hour,minute,true).show());
        LinearLayout clock=UiKit.row(this);
        clock.addView(timeLabel,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        clock.addView(timeButton,UiKit.lp(this,135,46));
        main.addView(clock,full(6));

        main.addView(UiKit.label(this,"Se repite",13,UiKit.SUB,true),full(21));
        LinearLayout daysRow=UiKit.row(this);
        String[] labels={"L","M","M","J","V","S","D"};
        String[] accessible={"lunes","martes","miércoles","jueves","viernes","sábado","domingo"};
        for(int i=0;i<7;i++){
            final int day=i;
            Button d=UiKit.button(this,labels[i],false,()->{
                dayMask^=(1<<day);refresh();
            });
            d.setContentDescription("Seleccionar "+accessible[i]);
            days[i]=d;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,UiKit.dp(this,43),1);
            p.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);
            daysRow.addView(d,p);
        }
        main.addView(daysRow,full(10));

        LinearLayout light=UiKit.card(this);root.addView(light,full(15));
        applyLight=new CheckBox(this);applyLight.setText("Configurar luz");
        applyLight.setTextColor(UiKit.TEXT);applyLight.setTextSize(18);
        applyLight.setButtonTintList(ColorStateList.valueOf(UiKit.MINT));
        applyLight.setChecked(rule.lightMode!=-1);light.addView(applyLight);
        lightOptions=UiKit.column(this);light.addView(lightOptions);
        lightOptions.addView(UiKit.label(this,"Al ejecutar el horario",13,UiKit.SUB,false),full(13));
        LinearLayout lightPower=UiKit.row(this);
        lightOnButton=UiKit.button(this,"Encender",false,()->{lightOn=true;refresh();});
        lightOffButton=UiKit.button(this,"Apagar",false,()->{lightOn=false;refresh();});
        addHalf(lightPower,lightOnButton,lightOffButton);lightOptions.addView(lightPower,full(7));

        lightOptions.addView(UiKit.label(this,"Intensidad",13,UiKit.SUB,true),full(22));
        LinearLayout br=UiKit.row(this);
        br.addView(UiKit.label(this,"Brillo",14,UiKit.TEXT,false),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        brightValue=UiKit.title(this,brightness+"%",16);br.addView(brightValue);
        lightOptions.addView(br,full(5));
        SeekBar bar=UiKit.slider(this,brightness);bar.setMin(1);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int value,boolean user){
                brightness=Math.max(1,value);brightValue.setText(brightness+"%");
            }
            public void onStopTrackingTouch(SeekBar s){}
        });
        lightOptions.addView(bar,full(8));
        lightOptions.addView(UiKit.label(this,"Temperatura",13,UiKit.SUB,true),full(18));
        LinearLayout tempRow=UiKit.row(this);
        String[] modes={"Cálida","Neutra","Fría"};
        for(int i=0;i<3;i++){
            final int value=i;temps[i]=UiKit.button(this,modes[i],false,()->{temperature=value;refresh();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,UiKit.dp(this,43),1);
            p.setMargins(UiKit.dp(this,3),0,UiKit.dp(this,3),0);tempRow.addView(temps[i],p);
        }
        lightOptions.addView(tempRow,full(9));
        applyLight.setOnCheckedChangeListener((v,b)->refresh());

        LinearLayout fan=UiKit.card(this);root.addView(fan,full(15));
        applyFan=new CheckBox(this);applyFan.setText("Configurar ventilador");
        applyFan.setTextColor(UiKit.TEXT);applyFan.setTextSize(18);
        applyFan.setButtonTintList(ColorStateList.valueOf(UiKit.MINT));
        applyFan.setChecked(rule.fanMode!=-1);fan.addView(applyFan);
        fanOptions=UiKit.column(this);fan.addView(fanOptions);
        fanOptions.addView(UiKit.label(this,"Al ejecutar el horario",13,UiKit.SUB,false),full(13));
        LinearLayout fanPower=UiKit.row(this);
        fanOnButton=UiKit.button(this,"Encender",false,()->{fanOn=true;refresh();});
        fanOffButton=UiKit.button(this,"Apagar",false,()->{fanOn=false;refresh();});
        addHalf(fanPower,fanOnButton,fanOffButton);fanOptions.addView(fanPower,full(7));
        fanOptions.addView(UiKit.label(this,"Velocidad",13,UiKit.SUB,true),full(21));
        LinearLayout speedRow=UiKit.row(this);
        for(int i=1;i<=6;i++){
            final int v=i;
            Button b=UiKit.button(this,String.valueOf(i),false,()->{speed=v;refresh();});
            fanSpeeds[i-1]=b;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,UiKit.dp(this,44),1);
            p.setMargins(UiKit.dp(this,3),0,UiKit.dp(this,3),0);speedRow.addView(b,p);
        }
        fanOptions.addView(speedRow,full(9));
        applyFan.setOnCheckedChangeListener((v,b)->refresh());

        Button save=UiKit.button(this,"Guardar horario",true,this::save);
        root.addView(save,full(24));
        root.addView(UiKit.label(this,
            "El teléfono debe permanecer encendido, cerca del ventilador y con Bluetooth activado. Si Android no concede el permiso de alarmas exactas, los disparos pueden retrasarse.",
            12,UiKit.SUB,false),full(15));
    }
    private String clock(){return String.format(java.util.Locale.ROOT,"%02d:%02d",hour,minute);}
    private void addHalf(LinearLayout row,Button a,Button b){
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,UiKit.dp(this,43),1);
        left.rightMargin=UiKit.dp(this,5);row.addView(a,left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,UiKit.dp(this,43),1);
        right.leftMargin=UiKit.dp(this,5);row.addView(b,right);
    }
    private void selected(Button b,boolean yes){
        b.setTextColor(yes?UiKit.BG:UiKit.TEXT);
        b.setBackground(UiKit.background(yes?UiKit.MINT:UiKit.RAISED,
            UiKit.dp(this,13),yes?0:UiKit.STROKE));
    }
    private void refresh(){
        if(timeLabel==null)return;
        timeLabel.setText(clock());
        for(int i=0;i<7;i++)selected(days[i],(dayMask&(1<<i))!=0);
        lightOptions.setVisibility(applyLight.isChecked()?View.VISIBLE:View.GONE);
        fanOptions.setVisibility(applyFan.isChecked()?View.VISIBLE:View.GONE);
        selected(lightOnButton,lightOn);selected(lightOffButton,!lightOn);
        selected(fanOnButton,fanOn);selected(fanOffButton,!fanOn);
        for(int i=0;i<3;i++)selected(temps[i],i==temperature);
        for(int i=0;i<6;i++)selected(fanSpeeds[i],i+1==speed);
    }
    private void save(){
        if(dayMask==0){message("Selecciona al menos un día.");return;}
        if(!applyLight.isChecked()&&!applyFan.isChecked()){
            message("Elige luz, ventilador o ambos.");return;
        }
        rule.name=name.getText().toString().trim();
        rule.hour=hour;rule.minute=minute;rule.days=dayMask;
        rule.lightMode=applyLight.isChecked()?(lightOn?1:0):-1;
        rule.fanMode=applyFan.isChecked()?(fanOn?1:0):-1;
        rule.brightness=brightness;rule.temperature=temperature;rule.speed=speed;
        try{
            rule=ScheduleStore.save(this,rule);
            ScheduleManager.schedule(this,rule);
            finish();
        }catch(Exception e){message(e.getMessage());}
    }
    private void message(String text){
        new AlertDialog.Builder(this).setTitle("Revisa tu horario").setMessage(text)
            .setPositiveButton("Entendido",null).show();
    }
}
