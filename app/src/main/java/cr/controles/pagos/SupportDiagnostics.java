package cr.controles.pagos;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.content.pm.PackageInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Diagnosticos locales de minimo privilegio. No guardar mensajes de excepcion,
 * trazas, datos financieros, credenciales, UID, correo ni tokens.
 * Solo se comparten mediante accion expresa del usuario.
 */
final class SupportDiagnostics {
  private static final String PREFS="pagocheck_support_diagnostics";
  private static final String KEY="incidents";
  private static final int LIMIT=15;
  private SupportDiagnostics(){}

  static void record(Activity activity,String module,String operation,String category,String code){
    try{
      JSONObject incident=new JSONObject();
      incident.put("id","PC-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));
      incident.put("utc",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.ROOT)
        {{setTimeZone(java.util.TimeZone.getTimeZone("UTC"));}}.format(new Date()));
      incident.put("module",safe(module));
      incident.put("action",safe(operation));
      incident.put("category",safe(category));
      incident.put("code",safe(code));
      SharedPreferences prefs=activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
      JSONArray old=new JSONArray(prefs.getString(KEY,"[]"));
      JSONArray newest=new JSONArray();
      newest.put(incident);
      for(int i=0;i<old.length()&&i<LIMIT-1;i++)newest.put(old.getJSONObject(i));
      prefs.edit().putString(KEY,newest.toString()).apply();
    }catch(Exception ignored){ }
  }

  private static String safe(String value){
    if(value==null)return "UNKNOWN";
    String result=value.replaceAll("[^A-Za-z0-9_.-]","_");
    if(result.length()>70)result=result.substring(0,70);
    return result.isEmpty()?"UNKNOWN":result;
  }

  static void share(Activity activity){
    try{
      SharedPreferences prefs=activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE);
      JSONArray entries=new JSONArray(prefs.getString(KEY,"[]"));
      PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
      StringBuilder report=new StringBuilder();
      report.append("PAGOCHECK IA — INFORME TECNICO VOLUNTARIO\n")
        .append("Version: ").append(info.versionName).append("\n")
        .append("Android API: ").append(Build.VERSION.SDK_INT).append("\n")
        .append("Paquete: ").append(activity.getPackageName()).append("\n")
        .append("Cantidad de incidencias: ").append(entries.length()).append("\n")
        .append("Sin datos financieros ni identificadores personales.\n\n");
      for(int i=0;i<entries.length();i++){
        JSONObject e=entries.getJSONObject(i);
        report.append(e.optString("id")).append(" | ").append(e.optString("utc"))
          .append(" | ").append(e.optString("module")).append("/")
          .append(e.optString("action")).append(" | ")
          .append(e.optString("category")).append(" | ")
          .append(e.optString("code")).append("\n");
      }
      Intent share=new Intent(Intent.ACTION_SEND);
      share.setType("text/plain");
      share.putExtra(Intent.EXTRA_SUBJECT,"PagoCheck IA - Informe tecnico");
      share.putExtra(Intent.EXTRA_TEXT,report.toString());
      activity.startActivity(Intent.createChooser(share,"Compartir diagnostico"));
    }catch(Exception e){
      new android.app.AlertDialog.Builder(activity)
        .setTitle("PagoCheck IA · Soporte")
        .setMessage("No fue posible generar el informe tecnico.")
        .setPositiveButton("Entendido",null).show();
    }
  }
  static void clear(Activity activity){
    activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit().remove(KEY).apply();
  }
}
