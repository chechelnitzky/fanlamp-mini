package cl.fanlampmini;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restores weekly alarms after reboot/clock changes and launches the BLE foreground service. */
public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        if(ScheduleManager.ACTION_RUN.equals(action)){
            int id=intent.getIntExtra(ScheduleManager.EXTRA_ID,0);
            ScheduleStore.Rule rule=ScheduleStore.find(context,id);
            if(rule==null || !rule.enabled){ScheduleManager.cancel(context,id);return;}
            ScheduleManager.schedule(context,rule); // Always reschedule next occurrence first.
            context.getSharedPreferences("fanlamp_schedule_diagnostics",Context.MODE_PRIVATE).edit()
                    .putLong("last_trigger",System.currentTimeMillis()).putInt("last_rule",id).apply();
            Intent service=new Intent(context,ScheduledBleService.class);
            service.putExtra(ScheduleManager.EXTRA_ID,id);
            try{
                if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);
                else context.startService(service);
            }catch(Exception e){
                context.getSharedPreferences("fanlamp_schedule_diagnostics",Context.MODE_PRIVATE).edit()
                    .putString("last_result","Android bloqueó el servicio: "+e.getClass().getSimpleName()).apply();
            }
        }else if(Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)){
            ScheduleManager.restore(context);
        }
    }
}
