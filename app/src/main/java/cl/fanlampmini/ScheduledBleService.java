package cl.fanlampmini;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground BLE advertising task for schedules while the app's UI is closed.
 * A scheduled alarm starts this service; if the phone is off/out of range or
 * Bluetooth is disabled there is no way to guarantee remote execution.
 */
public final class ScheduledBleService extends Service {
    private static final String CHANNEL="fanlamp_schedule";
    private static final int NOTIFICATION_ID=41;
    private int outstanding;
    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Horarios FanLamp",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Comandos Bluetooth de los horarios programados");
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(manager!=null)manager.createNotificationChannel(c);
        }
    }
    private void foreground(String label){
        Intent open=new Intent(this,HomeActivity.class);
        PendingIntent action=PendingIntent.getActivity(this,0,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FanLamp · "+label)
            .setContentText("Enviando horario Bluetooth")
            .setContentIntent(action).setOngoing(true)
            .setOnlyAlertOnce(true);
        if(Build.VERSION.SDK_INT>=29){
            startForeground(NOTIFICATION_ID,b.build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        }else startForeground(NOTIFICATION_ID,b.build());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        int id=intent==null?0:intent.getIntExtra(ScheduleManager.EXTRA_ID,0);
        ScheduleStore.Rule r=ScheduleStore.find(this,id);
        try{foreground(r==null?"Horario":"Horario: "+r.name);}
        catch(Exception e){
            log("No pudo iniciar servicio visible: "+e.getClass().getSimpleName());
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if(r==null || !r.enabled){
            stopSelfResult(startId);return START_NOT_STICKY;
        }
        outstanding++;
        BleController.get(this).submit(ScheduleStore.operations(r),(transmitted,message)->{
            log((transmitted?"Emitido":"Falló")+" · "+r.name+" · "+message);
            outstanding--;
            if(outstanding<=0)stopSelf();
        });
        return START_NOT_STICKY;
    }
    private void log(String message){
        getSharedPreferences("fanlamp_schedule_diagnostics",Context.MODE_PRIVATE).edit()
            .putLong("last_result_time",System.currentTimeMillis())
            .putString("last_result",message).apply();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
