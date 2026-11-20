package cl.fanlampmini;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;
import java.util.List;

/**
 * One alarm per enabled rule. Schedule next local-wall-clock matching weekday.
 * Uses exact-while-idle if the Android exact-alarm special access is granted;
 * otherwise inexact-while-idle is used and the UI must warn about drift.
 */
public final class ScheduleManager {
    public static final String ACTION_RUN="cl.fanlampmini.action.RUN_SCHEDULE";
    public static final String EXTRA_ID="ruleId";
    private ScheduleManager(){}

    public static boolean exactGranted(Context context){
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        return manager!=null && (Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms());
    }
    public static long nextOccurrence(ScheduleStore.Rule r,long afterMillis){
        if(r==null || r.days==0)return 0;
        long best=Long.MAX_VALUE;
        for(int delta=0;delta<=7;delta++){
            Calendar local=Calendar.getInstance();
            local.setTimeInMillis(afterMillis);
            local.add(Calendar.DAY_OF_YEAR,delta);
            int mondayFirst=(local.get(Calendar.DAY_OF_WEEK)+5)%7;
            if((r.days&(1<<mondayFirst))==0)continue;
            local.set(Calendar.HOUR_OF_DAY,r.hour);local.set(Calendar.MINUTE,r.minute);
            local.set(Calendar.SECOND,0);local.set(Calendar.MILLISECOND,0);
            long candidate=local.getTimeInMillis();
            if(candidate>afterMillis+3000 && candidate<best)best=candidate;
        }
        return best==Long.MAX_VALUE?0:best;
    }
    private static PendingIntent intent(Context context,int id,int flags){
        Intent i=new Intent(context,ScheduleReceiver.class);
        i.setAction(ACTION_RUN);i.putExtra(EXTRA_ID,id);
        return PendingIntent.getBroadcast(context,id,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void cancel(Context context,int id){
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        PendingIntent alarm=intent(context,id,PendingIntent.FLAG_NO_CREATE);
        if(alarm!=null){manager.cancel(alarm);alarm.cancel();}
    }
    public static void schedule(Context context,ScheduleStore.Rule r){
        cancel(context,r.id);
        if(!r.enabled)return;
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        long when=nextOccurrence(r,System.currentTimeMillis());
        if(when==0)return;
        PendingIntent pi=intent(context,r.id,PendingIntent.FLAG_UPDATE_CURRENT);
        try{
            if(exactGranted(context)){
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            }else{
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            }
        }catch(SecurityException e){
            // OEM restriction / permission revocation race.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        }
    }
    public static void restore(Context context){
        List<ScheduleStore.Rule> rules=ScheduleStore.all(context);
        for(ScheduleStore.Rule r:rules)schedule(context,r);
    }
}
