package cl.fanlampmini;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Persistent, locally stored weekly schedules. Day bits 0..6 = Monday..Sunday. */
public final class ScheduleStore {
    public static final class Rule {
        public int id;
        public String name="Horario";
        public boolean enabled=true;
        public int hour=22, minute=0, days=127;
        // -1 = don't touch the device, 0 = off, 1 = on.
        public int lightMode=1,fanMode=-1;
        public int brightness=60,temperature=1,speed=2;
        public Rule copy(){
            Rule r=new Rule();r.id=id;r.name=name;r.enabled=enabled;r.hour=hour;r.minute=minute;r.days=days;
            r.lightMode=lightMode;r.fanMode=fanMode;r.brightness=brightness;r.temperature=temperature;r.speed=speed;return r;
        }
        public JSONObject json(){
            JSONObject o=new JSONObject();
            try{
                o.put("id",id);o.put("name",name);o.put("enabled",enabled);
                o.put("hour",hour);o.put("minute",minute);o.put("days",days);
                o.put("lightMode",lightMode);o.put("fanMode",fanMode);
                o.put("brightness",brightness);o.put("temperature",temperature);o.put("speed",speed);
            }catch(Exception ignored){}return o;
        }
        public static Rule from(JSONObject o){
            Rule r=new Rule();
            r.id=o.optInt("id",0);r.name=o.optString("name","Horario");
            r.enabled=o.optBoolean("enabled",true);
            r.hour=o.optInt("hour",22);r.minute=o.optInt("minute",0);r.days=o.optInt("days",127);
            r.lightMode=o.optInt("lightMode",1);r.fanMode=o.optInt("fanMode",-1);
            r.brightness=o.optInt("brightness",60);r.temperature=o.optInt("temperature",1);
            r.speed=o.optInt("speed",2);return r;
        }
        public boolean valid(){
            return id>=0 && hour>=0 && hour<=23 && minute>=0 && minute<=59 &&
                days>=1 && days<=127 && lightMode>=-1 && lightMode<=1 &&
                fanMode>=-1 && fanMode<=1 && !(lightMode==-1 && fanMode==-1) &&
                brightness>=1 && brightness<=100 && temperature>=0 && temperature<=2 &&
                speed>=1 && speed<=6;
        }
    }
    private static final String[] DAYS={"Lu","Ma","Mi","Ju","Vi","Sa","Do"};
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("fanlamp_schedules",Context.MODE_PRIVATE);}
    public static synchronized List<Rule> all(Context c){
        ArrayList<Rule> out=new ArrayList<>();
        try{
            JSONArray arr=new JSONArray(prefs(c).getString("rules","[]"));
            for(int i=0;i<arr.length();i++){
                Rule r=Rule.from(arr.getJSONObject(i));
                if(r.valid())out.add(r);
            }
        }catch(Exception ignored){}
        Collections.sort(out,Comparator.comparingInt((Rule r)->r.hour).thenComparingInt(r->r.minute).thenComparingInt(r->r.id));
        return out;
    }
    public static synchronized Rule find(Context c,int id){
        for(Rule r:all(c))if(r.id==id)return r;return null;
    }
    public static synchronized Rule save(Context c,Rule rule){
        Rule copy=rule.copy();
        if(copy.name==null || copy.name.trim().isEmpty())copy.name="Horario";
        copy.name=copy.name.trim();if(copy.name.length()>40)copy.name=copy.name.substring(0,40);
        SharedPreferences p=prefs(c);
        if(copy.id==0)copy.id=p.getInt("next_id",1);
        if(!copy.valid())throw new IllegalArgumentException("Revisa días, hora y acciones.");
        List<Rule> list=all(c);
        boolean replaced=false;
        for(int i=0;i<list.size();i++)if(list.get(i).id==copy.id){list.set(i,copy);replaced=true;break;}
        if(!replaced)list.add(copy);
        JSONArray arr=new JSONArray();
        for(Rule r:list)arr.put(r.json());
        p.edit().putString("rules",arr.toString()).putInt("next_id",Math.max(p.getInt("next_id",1),copy.id+1)).commit();
        return copy;
    }
    public static synchronized void delete(Context c,int id){
        JSONArray arr=new JSONArray();
        for(Rule r:all(c))if(r.id!=id)arr.put(r.json());
        prefs(c).edit().putString("rules",arr.toString()).commit();
    }
    public static String time(Rule r){return String.format(java.util.Locale.ROOT,"%02d:%02d",r.hour,r.minute);}
    public static String days(Rule r){
        if(r.days==127)return "Todos los días";
        if(r.days==31)return "Lunes a viernes";
        if(r.days==96)return "Fin de semana";
        StringBuilder b=new StringBuilder();
        for(int i=0;i<7;i++)if((r.days&(1<<i))!=0){if(b.length()>0)b.append(" · ");b.append(DAYS[i]);}
        return b.toString();
    }
    public static String summary(Rule r){
        StringBuilder b=new StringBuilder();
        if(r.lightMode>=0){
            b.append(r.lightMode==0?"Luz apagada":"Luz "+r.brightness+"% · "+(r.temperature==0?"cálida":r.temperature==1?"neutra":"fría"));
        }
        if(r.fanMode>=0){
            if(b.length()>0)b.append("  +  ");
            b.append(r.fanMode==0?"Ventilador apagado":"Ventilador · Velocidad "+r.speed);
        }
        return b.toString();
    }
    public static List<BleController.Operation> operations(Rule r){
        ArrayList<BleController.Operation> actions=new ArrayList<>();
        if(r.lightMode>=0){
            actions.add(BleController.Operation.light(r.lightMode==1));
            if(r.lightMode==1)actions.add(BleController.Operation.cww(r.brightness,r.temperature));
        }
        if(r.fanMode>=0)actions.add(BleController.Operation.fan(r.fanMode==0?0:r.speed));
        return actions;
    }
}
