package cr.controles.pagos;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Date;

/**
 * Recordatorios locales. Utiliza alarmas inexactas para no requerir
 * SCHEDULE_EXACT_ALARM. Android puede retrasarlas por ahorro de batería.
 */
public final class ReminderScheduler {
    static final String PREF="control_pago_reminder_data";
    static final String DATA="ledger";
    static final String IDS="scheduled";
    static final String CHANNEL="pending_payment_reminders";
    static final String ACTION="cr.controles.pagos.PAYMENT_REMINDER";
    static final String EXTRA_MONTH="month";
    static final String EXTRA_ID="commitment";
    static final String EXTRA_ANCHOR="anchor";
    static final String EXTRA_DATE="date";
    private ReminderScheduler(){}

    static void saveAndRefresh(Context context,String json){
        try{
            JSONObject obj=new JSONObject(json);
            if(!(obj.opt("commitments") instanceof JSONArray)||
               !(obj.opt("transactions") instanceof JSONArray))return;
            context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(DATA,json).apply();
            refresh(context);
        }catch(Exception ignored){}
    }

    static void refresh(Context context){
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(am==null)return;
        JSONArray old=parseArray(p.getString(IDS,"[]"));
        for(int i=0;i<old.length();i++){
            JSONObject x=old.optJSONObject(i);
            if(x==null)continue;
            PendingIntent pi=pending(context,x.optString("key"),PendingIntent.FLAG_NO_CREATE);
            if(pi!=null){am.cancel(pi);pi.cancel();}
        }
        JSONArray scheduled=new JSONArray();
        JSONObject data=parseObject(p.getString(DATA,"{}"));
        JSONArray commits=data.optJSONArray("commitments");
        if(commits==null)return;
        long now=System.currentTimeMillis();
        for(int i=0;i<commits.length();i++){
            JSONObject c=commits.optJSONObject(i);
            if(c==null||!"expense".equals(c.optString("kind")))continue;
            JSONObject settings=c.optJSONObject("reminders");
            if(settings==null)continue;
            for(String type:new String[]{"planned","due"}){
                JSONObject rule=settings.optJSONObject(type);
                if(rule==null||!rule.optBoolean("enabled",false))continue;
                int lead=Math.max(0,Math.min(31,rule.optInt("before",0)));
                int hour=Math.max(0,Math.min(23,settings.optInt("hour",9)));
                Event event=nextEvent(c,data,type,lead,hour,now);
                if(event==null)continue;
                String key=c.optString("id")+"|"+type;
                Intent intent=new Intent(context,ReminderReceiver.class);
                intent.setAction(ACTION);
                intent.setData(Uri.parse("payments-reminder://local/"+Uri.encode(key)));
                intent.putExtra(EXTRA_ID,c.optString("id"));
                intent.putExtra(EXTRA_MONTH,event.month);
                intent.putExtra(EXTRA_ANCHOR,type);
                intent.putExtra(EXTRA_DATE,event.dueDate);
                PendingIntent pi=PendingIntent.getBroadcast(context,key.hashCode(),
                   intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,event.timestamp,pi);
                JSONObject entry=new JSONObject();
                try{entry.put("key",key);scheduled.put(entry);}catch(Exception ignored){}
            }
        }
        p.edit().putString(IDS,scheduled.toString()).apply();
    }

    static final class Event{
        String month,dueDate; long timestamp;
        Event(String month,String dueDate,long timestamp){this.month=month;this.dueDate=dueDate;this.timestamp=timestamp;}
    }
    static Event nextEvent(JSONObject c,JSONObject data,String type,int lead,int hour,long now){
        Calendar base=Calendar.getInstance();
        base.set(Calendar.DAY_OF_MONTH,1);
        base.set(Calendar.HOUR_OF_DAY,0);base.set(Calendar.MINUTE,0);base.set(Calendar.SECOND,0);base.set(Calendar.MILLISECOND,0);
        for(int n=0;n<15;n++){
            Calendar month= (Calendar)base.clone();month.add(Calendar.MONTH,n);
            String ym=monthString(month);
            if(ym.compareTo(c.optString("start","0000-01"))<0)continue;
            String end=c.optString("end","");
            if(!end.isEmpty()&&ym.compareTo(end)>0)break;
            int day=c.optInt(type.equals("planned")?"planned":"due",c.optInt("due",15));
            day=Math.max(1,Math.min(day,month.getActualMaximum(Calendar.DAY_OF_MONTH)));
            month.set(Calendar.DAY_OF_MONTH,day);
            String deadline=dateString(month);
            month.add(Calendar.DAY_OF_MONTH,-lead);
            month.set(Calendar.HOUR_OF_DAY,hour);month.set(Calendar.MINUTE,0);month.set(Calendar.SECOND,0);month.set(Calendar.MILLISECOND,0);
            if(month.getTimeInMillis()<=now)continue;
            if(isPaid(c,data,ym))continue;
            return new Event(ym,deadline,month.getTimeInMillis());
        }
        return null;
    }
    static boolean isPaid(JSONObject c,JSONObject data,String month){
        // El usuario confirma expresamente el pago completo; los abonos no lo liquidan.
        JSONObject monthChecks=data.optJSONObject("paymentChecks");
        if(monthChecks!=null){
            JSONObject monthData=monthChecks.optJSONObject(month);
            JSONObject check=monthData==null?null:monthData.optJSONObject(c.optString("id"));
            return check!=null && check.optBoolean("complete",false);
        }
        // Compatibilidad con registros anteriores a la versión 13.
        double target=c.optDouble("estimate",0);
        JSONObject overrides=c.optJSONObject("overrides");
        if(overrides!=null&&overrides.has(month))target=overrides.optDouble(month,target);
        if(target<=0)return true;
        double paid=0;
        JSONArray tx=data.optJSONArray("transactions");
        if(tx!=null)for(int i=0;i<tx.length();i++){
            JSONObject t=tx.optJSONObject(i);
            if(t==null||t.optBoolean("reversed",false))continue;
            if(!month.equals(t.optString("month")))continue;
            if(c.optString("id").equals(t.optString("commitmentId"))&&"expense".equals(t.optString("kind")))paid+=t.optDouble("amount",0);
        }
        return paid>=target;
    }
    static JSONObject lookup(JSONObject data,String id){
        JSONArray a=data.optJSONArray("commitments");
        if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject c=a.optJSONObject(i);
            if(c!=null&&id.equals(c.optString("id")))return c;
        }
        return null;
    }
    static void showIfPending(Context context,Intent intent){
        String id=intent.getStringExtra(EXTRA_ID),month=intent.getStringExtra(EXTRA_MONTH),anchor=intent.getStringExtra(EXTRA_ANCHOR);
        if(id==null||month==null||anchor==null)return;
        JSONObject data=parseObject(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(DATA,"{}"));
        JSONObject c=lookup(data,id);
        if(c==null||!"expense".equals(c.optString("kind")))return;
        String end=c.optString("end","");
        if(month.compareTo(c.optString("start","0000-01"))<0||(!end.isEmpty()&&month.compareTo(end)>0))return;
        JSONObject reminders=c.optJSONObject("reminders");
        JSONObject rule=reminders==null?null:reminders.optJSONObject(anchor);
        if(rule==null||!rule.optBoolean("enabled")||isPaid(c,data,month))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Pagos pendientes",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Recordatorios de compromisos todavía pendientes");
            nm.createNotificationChannel(channel);
        }
        Intent open=new Intent(context,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent click=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String title="Pago pendiente: "+c.optString("name","Compromiso");
        String body=(anchor.equals("planned")?"Fecha proyectada: ":"Fecha límite: ")+intent.getStringExtra(EXTRA_DATE) ;
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title)
              .setContentText(body).setAutoCancel(true).setContentIntent(click);
        nm.notify((id+"-"+month+"-"+anchor).hashCode(),builder.build());
    }
    static String monthString(Calendar c){return String.format(java.util.Locale.US,"%04d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1);}
    static String dateString(Calendar c){return String.format(java.util.Locale.US,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));}
    static JSONArray parseArray(String s){try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}}
    static JSONObject parseObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    static PendingIntent pending(Context context,String key,int flags){
        if(key==null||key.isEmpty())return null;
        Intent i=new Intent(context,ReminderReceiver.class);
        i.setAction(ACTION);
        i.setData(Uri.parse("payments-reminder://local/"+Uri.encode(key)));
        return PendingIntent.getBroadcast(context,key.hashCode(),i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
}
