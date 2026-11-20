package cl.fanlampmini;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Real weekly schedules, with per-rule enable/edit/delete and exact-alarm settings. */
public final class SchedulesActivity extends Activity {
    private LinearLayout root;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);UiKit.darkBars(this);render();
    }
    private LinearLayout.LayoutParams full(int top){
        LinearLayout.LayoutParams p=UiKit.lp(this,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=UiKit.dp(this,top);return p;
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(UiKit.BG);scroll.setFillViewport(true);
        root=UiKit.column(this);root.setPadding(UiKit.dp(this,20),UiKit.dp(this,24),UiKit.dp(this,20),UiKit.dp(this,38));
        scroll.addView(root);setContentView(scroll);
        LinearLayout header=UiKit.row(this);
        Button back=UiKit.button(this,"‹",false,this::finish);
        back.setContentDescription("Volver");header.addView(back,UiKit.lp(this,48,48));
        LinearLayout h=UiKit.column(this);
        h.addView(UiKit.label(this,"AUTOMATIZACIONES",11,UiKit.MINT,true));
        h.addView(UiKit.title(this,"Mis horarios",29));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        hp.leftMargin=UiKit.dp(this,14);header.addView(h,hp);
        root.addView(header);
        root.addView(UiKit.label(this,
            "Cada horario ejecuta únicamente las funciones elegidas: luz, ventilador o ambos. Se repite los días seleccionados.",
            14,UiKit.SUB,false),full(13));
        Button create=UiKit.button(this,"+   Crear horario",true,()->startActivity(new Intent(this,ScheduleEditorActivity.class)));
        root.addView(create,full(22));
        if(!ScheduleManager.exactGranted(this) && Build.VERSION.SDK_INT>=31){
            LinearLayout warning=UiKit.card(this);
            warning.addView(UiKit.title(this,"Activar horarios exactos",16));
            warning.addView(UiKit.label(this,"Android puede retrasar las acciones mientras este permiso esté desactivado.",13,UiKit.SUB,false),full(8));
            Button enable=UiKit.button(this,"Dar permiso en Android",false,this::openExactSettings);
            warning.addView(enable,full(9));root.addView(warning,full(17));
        }else{
            root.addView(UiKit.label(this,"✓  Horarios exactos habilitados en Android",12,UiKit.MINT,false),full(15));
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            Button notify=UiKit.button(this,"Permitir aviso cuando se ejecute un horario",false,()->
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},48));
            root.addView(notify,full(10));
        }
        List<ScheduleStore.Rule> rules=ScheduleStore.all(this);
        if(rules.isEmpty()){
            LinearLayout empty=UiKit.card(this);
            empty.addView(UiKit.title(this,"Aún no hay horarios",19));
            empty.addView(UiKit.label(this,"Por ejemplo, de lunes a viernes a las 22:30: luz cálida al 30% y ventilador en velocidad 2.",14,UiKit.SUB,false),full(10));
            root.addView(empty,full(21));
        }
        for(ScheduleStore.Rule r:rules)addRuleCard(r);
        root.addView(UiKit.label(this,
            "Importante: la app debe seguir instalada, con permisos Bluetooth; el teléfono tiene que estar encendido y cerca del ventilador. El envío BLE no confirma que el aparato haya ejecutado el comando.",
            12,UiKit.SUB,false),full(28));
        String outcome=getSharedPreferences("fanlamp_schedule_diagnostics",Context.MODE_PRIVATE).getString("last_result","");
        if(!outcome.isEmpty())root.addView(UiKit.label(this,"Última ejecución: "+outcome,12,UiKit.SUB,false),full(10));
    }
    private void addRuleCard(ScheduleStore.Rule r){
        LinearLayout card=UiKit.card(this);
        LinearLayout title=UiKit.row(this);
        LinearLayout data=UiKit.column(this);
        data.addView(UiKit.label(this,ScheduleStore.time(r),30,UiKit.TEXT,true));
        data.addView(UiKit.label(this,r.name,15,UiKit.MINT,true));
        title.addView(data,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Switch enabled=new Switch(this);enabled.setChecked(r.enabled);
        enabled.setContentDescription((r.enabled?"Pausar ":"Activar ")+r.name);
        enabled.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{UiKit.MINT,UiKit.SUB}));
        enabled.setOnCheckedChangeListener((button,value)->{
            r.enabled=value;ScheduleStore.save(this,r);
            ScheduleManager.schedule(this,r);render();
        });
        title.addView(enabled);card.addView(title);
        card.addView(UiKit.label(this,ScheduleStore.days(r),13,UiKit.SUB,false),full(7));
        card.addView(UiKit.label(this,ScheduleStore.summary(r),14,UiKit.TEXT,false),full(13));
        LinearLayout tools=UiKit.row(this);
        Button edit=UiKit.button(this,"Editar",false,()->{
            Intent intent=new Intent(this,ScheduleEditorActivity.class);
            intent.putExtra(ScheduleManager.EXTRA_ID,r.id);startActivity(intent);
        });
        Button remove=UiKit.button(this,"Eliminar",false,()->new AlertDialog.Builder(this)
            .setTitle("Eliminar horario")
            .setMessage("¿Eliminar «"+r.name+"»? Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Eliminar",(dialog,which)->{
                ScheduleManager.cancel(this,r.id);ScheduleStore.delete(this,r.id);render();
            }).show());
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,UiKit.dp(this,42),1);
        a.rightMargin=UiKit.dp(this,6);tools.addView(edit,a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,UiKit.dp(this,42),1);
        b.leftMargin=UiKit.dp(this,6);tools.addView(remove,b);
        card.addView(tools,full(17));root.addView(card,full(15));
    }
    private void openExactSettings(){
        if(Build.VERSION.SDK_INT>=31){
            try{
                Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:"+getPackageName()));
                startActivity(i);
            }catch(Exception e){
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+getPackageName())));
            }
        }
    }
    @Override protected void onResume(){
        super.onResume();
        ScheduleManager.restore(this);
        if(root!=null)render();
    }
}
