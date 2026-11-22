package cr.controles.pagos;
import android.app.Activity;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.graphics.Color;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final int EXPORT=10, IMPORT=11, VOICE=12;
  private WebView web;
  private String backup;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(16,25,43));
    getWindow().setNavigationBarColor(Color.rgb(16,25,43));
    web=new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient());
    web.addJavascriptInterface(new VoiceBridge(),"AndroidAI");
    web.addJavascriptInterface(new BackupBridge(),"AndroidBackup");
    web.addJavascriptInterface(new ReminderBridge(),"AndroidReminders");
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
  public class VoiceBridge {
    @JavascriptInterface public void startSpeechRecognition(){
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
    if(result!=RESULT_OK||data==null)return;
    if(request==VOICE){
      ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if(words!=null&&!words.isEmpty())web.evaluateJavascript("receiveVoiceTranscript("+JSONObject.quote(words.get(0))+")",null);
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
    web.destroy();super.onDestroy();
  }
}