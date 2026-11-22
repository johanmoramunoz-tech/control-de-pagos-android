package cr.controles.pagos;
import android.app.Activity;
import android.app.AlertDialog;
import android.webkit.JsResult;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.graphics.Color;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.content.ActivityNotFoundException;

public class MainActivity extends Activity {
  private static final int EXPORT=10, IMPORT=11, VOICE=12, LOCAL_GALLERY=14, LOCAL_PHOTO=15, LOCAL_VOICE=16, REPORT_EXPORT=17;
  private WebView web;
  private String backup;
  private String reportContent;
  private Uri pendingPhoto;
  private PlayBillingManager playBilling;
  private LicenseAccountManager licenseAccount;
  private boolean isDebugBuild(){return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
  // Control de evaluación únicamente. No equivale a una licencia comercial.
  private boolean premiumPreview(){return isDebugBuild()&&getSharedPreferences("pagocheck_preview",MODE_PRIVATE).getBoolean("premium_enabled",false);}
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(16,25,43));
    getWindow().setNavigationBarColor(Color.rgb(16,25,43));
    if(state!=null){String image=state.getString("pagocheck_camera_uri");if(image!=null)pendingPhoto=Uri.parse(image);}
    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    // La interfaz local no necesita navegar a sitios externos. No abrir contenido remoto
    // dentro de un WebView que expone puentes nativos con datos financieros.
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
        Uri target=request.getUrl();
        return !"https".equalsIgnoreCase(target.getScheme())||
               !"controlpagos.local".equalsIgnoreCase(target.getHost());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
        Uri target=Uri.parse(url);
        return !"https".equalsIgnoreCase(target.getScheme())||
               !"controlpagos.local".equalsIgnoreCase(target.getHost());
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      // Los diálogos estándar del WebView muestran «JavaScript»; usamos la identidad de PagoCheck IA.
      @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){
        new AlertDialog.Builder(MainActivity.this)
          .setTitle("PagoCheck IA · Atención")
          .setMessage(message)
          .setPositiveButton("Entendido",(dialog,which)->result.confirm())
          .setOnCancelListener(dialog->result.cancel())
          .show();
        return true;
      }
      @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
        new AlertDialog.Builder(MainActivity.this)
          .setTitle("PagoCheck IA · Confirmación")
          .setMessage(message)
          .setPositiveButton("Confirmar",(dialog,which)->result.confirm())
          .setNegativeButton("Cancelar",(dialog,which)->result.cancel())
          .setOnCancelListener(dialog->result.cancel())
          .show();
        return true;
      }
    });
    web.addJavascriptInterface(new VoiceBridge(),"AndroidAI");
    web.addJavascriptInterface(new BackupBridge(),"AndroidBackup");
    web.addJavascriptInterface(new ReminderBridge(),"AndroidReminders");
    web.addJavascriptInterface(new LocalPremiumBridge(),"AndroidLocal");
    web.addJavascriptInterface(new PlayBillingBridge(),"AndroidPlayBilling");
    web.addJavascriptInterface(new LicenseAccountBridge(),"AndroidLicenseAccount");
    licenseAccount=new LicenseAccountManager(this,json->sendAiCallback("receiveLicenseAccountStatus",json));
    playBilling=new PlayBillingManager(this,json->sendAiCallback("receivePlayBillingStatus",json));
    ReminderScheduler.refresh(this);
    setContentView(web);
    try(InputStream in=getAssets().open("index.html")) {
      byte[] data=new byte[in.available()];
      int n=0, r;
      while(n<data.length && (r=in.read(data,n,data.length-n))>0)n+=r;
      web.loadDataWithBaseURL("https://controlpagos.local/",new String(data,StandardCharsets.UTF_8),"text/html","UTF-8",null);
    }catch(Exception e){Toast.makeText(this,"Error al abrir Control de Pagos",Toast.LENGTH_LONG).show();}
  }
  public class ReminderBridge {
    @JavascriptInterface public void sync(String state){
      ReminderScheduler.saveAndRefresh(getApplicationContext(),state);
    }
    @JavascriptInterface public void requestPermission(){
      runOnUiThread(()->{
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
          requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},77);
        }
      });
    }
  }
  private void sendAiCallback(String function,String value){
    runOnUiThread(()->{if(web!=null)web.evaluateJavascript(function+"("+JSONObject.quote(value)+")",null);});
  }
  public class LocalPremiumBridge {
    @JavascriptInterface public void status(){
      JSONObject o=new JSONObject();
      try{o.put("available",premiumPreview()).put("preview",isDebugBuild())
          .put("recognition","ML Kit OCR integrado").put("billingActive",false);}
      catch(Exception ignored){}
      sendAiCallback("receiveLocalStatus",o.toString());
    }
    @JavascriptInterface public void setTestPremium(boolean enabled){
      // Solo existe para las compilaciones debug firmadas de evaluación.
      if(!isDebugBuild())return;
      getSharedPreferences("pagocheck_preview",MODE_PRIVATE).edit().putBoolean("premium_enabled",enabled).apply();
      status();
    }
    @JavascriptInterface public void gallery(){
      if(!premiumPreview()){sendAiCallback("receiveLocalError","Premium no activado.");return;}
      runOnUiThread(()->{
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"});
        try{startActivityForResult(intent,LOCAL_GALLERY);}
        catch(Exception e){sendAiCallback("receiveLocalError","No fue posible abrir la galería.");}
      });
    }
    @JavascriptInterface public void camera(){
      if(!premiumPreview()){sendAiCallback("receiveLocalError","Premium no activado.");return;}
      runOnUiThread(()->{
        try{
          File photo=File.createTempFile("pagocheck_recibo_",".jpg",getCacheDir());
          pendingPhoto=FileProvider.getUriForFile(MainActivity.this,getPackageName()+".fileprovider",photo);
          Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
          intent.putExtra(MediaStore.EXTRA_OUTPUT,pendingPhoto);
          intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
          startActivityForResult(intent,LOCAL_PHOTO);
        }catch(Exception e){sendAiCallback("receiveLocalError","La cámara no está disponible.");}
      });
    }
    @JavascriptInterface public void speech(){
      if(!premiumPreview()){sendAiCallback("receiveLocalError","Premium no activado.");return;}
      runOnUiThread(()->{
        try{
          Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
          intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
          intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-CR");
          intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
          intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Indica el importe y el concepto");
          startActivityForResult(intent,LOCAL_VOICE);
        }catch(Exception e){sendAiCallback("receiveLocalError","No hay reconocimiento de voz compatible instalado.");}
      });
    }
  }
  public class LicenseAccountBridge {
    @JavascriptInterface public void status(){
      runOnUiThread(()->{if(licenseAccount!=null)licenseAccount.status();});
    }
    @JavascriptInterface public void signIn(){
      runOnUiThread(()->{if(licenseAccount!=null)licenseAccount.showLogin(false);});
    }
    @JavascriptInterface public void register(){
      runOnUiThread(()->{if(licenseAccount!=null)licenseAccount.showLogin(true);});
    }
    @JavascriptInterface public void signOut(){
      runOnUiThread(()->{if(licenseAccount!=null)licenseAccount.signOut();});
    }
    @JavascriptInterface public void refreshLicense(){
      runOnUiThread(()->{if(licenseAccount!=null)licenseAccount.refreshLicense();});
    }
  }
  public class PlayBillingBridge {
    @JavascriptInterface public void status(){runOnUiThread(()->{if(playBilling!=null)playBilling.connect();});}
    @JavascriptInterface public void refresh(){runOnUiThread(()->{if(playBilling!=null)playBilling.refresh();});}
    @JavascriptInterface public void purchase(){runOnUiThread(()->{if(playBilling!=null)playBilling.purchase();});}
  }
  @Override protected void onResume(){
    super.onResume();
    if(playBilling!=null)playBilling.connect();
  }
  private void scanLocal(Uri uri){
    if(!premiumPreview()||uri==null)return;
    sendAiCallback("receiveLocalProgress","Leyendo fotografía en este dispositivo...");
    LocalOcr.analyze(this,uri,new LocalOcr.Listener(){
      @Override public void success(String proposal){sendAiCallback("receiveLocalProposal",proposal);}
      @Override public void failure(String reason){sendAiCallback("receiveLocalError",reason);}
    });
  }
  @Override protected void onSaveInstanceState(Bundle state){
    if(pendingPhoto!=null)state.putString("pagocheck_camera_uri",pendingPhoto.toString());
    super.onSaveInstanceState(state);
  }
  public class VoiceBridge {
    @JavascriptInterface public void startSpeechRecognition(){
      if(!premiumPreview()){sendAiCallback("receiveLocalError","El dictado asistido pertenece a Premium.");return;}
      runOnUiThread(()->{
        try{
          Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
          i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
          i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-CR");
          i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Indica el movimiento");
          startActivityForResult(i,VOICE);
        }catch(Exception e){Toast.makeText(MainActivity.this,"Reconocimiento de voz no disponible",Toast.LENGTH_LONG).show();}
      });
    }
  }
  public class BackupBridge {
    @JavascriptInterface public void exportData(String json){
      runOnUiThread(()->{
        backup=json;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"respaldo-control-pagos.json");
        startActivityForResult(i,EXPORT);
      });
    }
    @JavascriptInterface public void exportReport(String content,String mime,String filename){
      if(content==null||content.length()>8000000||!(mime.equals("text/html")||mime.equals("text/csv")))return;
      String suggested=filename==null?"pagocheck-informe.csv":filename.replaceAll("[^A-Za-z0-9_.-]","_");
      runOnUiThread(()->{
        reportContent=content;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE,suggested);
        startActivityForResult(i,REPORT_EXPORT);
      });
    }
    @JavascriptInterface public void importData(){
      runOnUiThread(()->{
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i,IMPORT);
      });
    }
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK)return;
    if(request==LOCAL_PHOTO){scanLocal(pendingPhoto);return;}
    if(data==null)return;
    if(request==LOCAL_GALLERY){scanLocal(data.getData());return;}
    if(request==LOCAL_VOICE){
      ArrayList<String> speech=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if(speech==null||speech.isEmpty()){sendAiCallback("receiveLocalError","No se reconoció ninguna frase.");return;}
      try{sendAiCallback("receiveLocalProposal",LocalReceiptParser.parseSpeech(speech.get(0)).toString());}
      catch(Exception e){sendAiCallback("receiveLocalError",e.getMessage());}
      return;
    }
    if(request==VOICE){
      ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if(words!=null&&!words.isEmpty()){
        String phrase=words.get(0);
        web.evaluateJavascript("receiveVoiceTranscript("+JSONObject.quote(phrase)+")",null);
      }
      return;
    }
    Uri uri=data.getData();
    if(uri==null)return;
    try{
      if(request==EXPORT&&backup!=null){
        try(OutputStream out=getContentResolver().openOutputStream(uri)){
          if(out==null)throw new Exception("Archivo inaccesible");
          out.write(backup.getBytes(StandardCharsets.UTF_8));
        }
        backup=null;
        Toast.makeText(this,"Respaldo guardado",Toast.LENGTH_SHORT).show();
      }else if(request==REPORT_EXPORT&&reportContent!=null){
        try(OutputStream out=getContentResolver().openOutputStream(uri)){
          if(out==null)throw new Exception("Ubicación de destino inaccesible");
          out.write(reportContent.getBytes(StandardCharsets.UTF_8));
        }
        reportContent=null;
        Toast.makeText(this,"Informe guardado",Toast.LENGTH_SHORT).show();
      }else if(request==IMPORT){
        StringBuilder content=new StringBuilder();
        try(InputStream in=getContentResolver().openInputStream(uri)){
          if(in==null)throw new Exception("Archivo inaccesible");
          byte[] buf=new byte[4096];int n;
          while((n=in.read(buf))!=-1){
            content.append(new String(buf,0,n,StandardCharsets.UTF_8));
            if(content.length()>5000000)throw new Exception("Archivo demasiado grande");
          }
        }
        web.evaluateJavascript("receiveBackup("+JSONObject.quote(content.toString())+")",null);
      }
    }catch(Exception e){Toast.makeText(this,"Error: "+e.getMessage(),Toast.LENGTH_LONG).show();}
  }
  @Override public void onBackPressed(){web.evaluateJavascript("closeDialogOrBack()",null);}
  @Override protected void onDestroy(){
    web.removeJavascriptInterface("AndroidAI");
    web.removeJavascriptInterface("AndroidBackup");
    web.removeJavascriptInterface("AndroidReminders");
    web.removeJavascriptInterface("AndroidLocal");
    web.removeJavascriptInterface("AndroidPlayBilling");
    web.removeJavascriptInterface("AndroidLicenseAccount");
    if(playBilling!=null)playBilling.close();
    web.destroy();super.onDestroy();
  }
}