package cr.controles.pagos;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * Vinculación futura de Premium con una identidad Firebase.
 * No toca ni sincroniza el registro financiero local.
 * Todos los accesos remotos permanecen sin configurar por defecto.
 */
final class LicenseAccountManager {
  interface Listener { void onStatus(String status); }
  private final Activity activity;
  private final Listener listener;
  private FirebaseAuth auth;
  private String endpoint;
  private String message="Cuenta Premium no configurada";
  private boolean licenseVerified=false;
  private boolean checking=false;
  private long authRevision=0;
  boolean hasVerifiedLicense(){return licenseVerified&&auth!=null&&auth.getCurrentUser()!=null&&auth.getCurrentUser().isEmailVerified();}
  boolean readyForPurchaseVerification(){return auth!=null&&endpoint!=null&&auth.getCurrentUser()!=null&&auth.getCurrentUser().isEmailVerified();}

  LicenseAccountManager(Activity activity,Listener listener){
    this.activity=activity;this.listener=listener;
    try{
      String appId=activity.getString(R.string.firebase_pc_app_id).trim();
      String apiKey=activity.getString(R.string.firebase_pc_api_key).trim();
      String projectId=activity.getString(R.string.firebase_pc_project_id).trim();
      String address=activity.getString(R.string.license_pc_server_url).trim();
      if(appId.isEmpty()||apiKey.isEmpty()||projectId.isEmpty()){
        message="La cuenta de Premium estará disponible al configurar Firebase.";
        publish();return;
      }
      FirebaseApp app=FirebaseApp.getApps(activity).stream()
        .filter(x->"pagocheck-license".equals(x.getName())).findFirst().orElse(null);
      if(app==null){
        FirebaseOptions options=new FirebaseOptions.Builder().setApplicationId(appId)
          .setApiKey(apiKey).setProjectId(projectId).build();
        app=FirebaseApp.initializeApp(activity,options,"pagocheck-license");
      }
      auth=FirebaseAuth.getInstance(app);
      // Nunca aceptar direcciones HTTP ni destinos que incluyan credenciales.
      if(address.startsWith("https://")){
        URL url=new URL(address);
        if(url.getUserInfo()==null && url.getHost()!=null && !url.getHost().isEmpty()
          && url.getPort()==-1 && (url.getPath().isEmpty()||"/".equals(url.getPath()))
          && (url.getQuery()==null)&&url.getRef()==null)
          endpoint="https://"+url.getHost();
      }
      message="Firebase preparado; inicia sesión para verificar tu cuenta";
    }catch(Exception e){
      auth=null;endpoint=null;message="Configuración de cuenta Premium incompleta";
    }
    publish();
  }
  boolean configured(){return auth!=null;}
  void status(){publish();}
  void showLogin(boolean register){
    if(auth==null){publish();return;}
    activity.runOnUiThread(()->{
      LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
      int pad=(int)(20*activity.getResources().getDisplayMetrics().density);
      layout.setPadding(pad,pad,pad,pad);
      EditText email=new EditText(activity);email.setHint("Correo electrónico");
      email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
      EditText password=new EditText(activity);password.setHint("Contraseña (8 caracteres o más)");
      password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
      layout.addView(email);layout.addView(password);
      new AlertDialog.Builder(activity).setTitle(register?"Crear cuenta Premium":"Iniciar sesión Premium")
        .setView(layout)
        .setPositiveButton(register?"Crear cuenta":"Entrar",(d,w)->{
          String address=email.getText().toString().trim(),secret=password.getText().toString();
          if(!address.contains("@")||secret.length()<8){message="Verifica el correo y la contraseña";publish();return;}
          if(register){
            auth.createUserWithEmailAndPassword(address,secret).addOnCompleteListener(activity,result->{
              if(result.isSuccessful()){
                FirebaseUser user=auth.getCurrentUser();
                if(user!=null)user.sendEmailVerification();
                message="Cuenta creada. Verifica tu correo antes de usar Premium.";
              }else message="No fue posible crear la cuenta. Comprueba los datos o inténtalo de nuevo.";
              licenseVerified=false;publish();
            });
          }else{
            auth.signInWithEmailAndPassword(address,secret).addOnCompleteListener(activity,result->{
              message=result.isSuccessful()?"Sesión iniciada; comprueba tu correo y tu licencia.":"No se pudo iniciar sesión. Comprueba correo y contraseña.";
              licenseVerified=false;publish();
              if(result.isSuccessful())refreshLicense();
            });
          }
        }).setNegativeButton("Cancelar",null).show();
    });
  }
  void signOut(){
    authRevision++;
    if(auth!=null)auth.signOut();
    checking=false;licenseVerified=false;message="Sesión cerrada";publish();
  }
  void refreshLicense(){
    long revision=++authRevision;
    licenseVerified=false;
    if(auth==null){message="Configura Firebase para consultar la licencia.";publish();return;}
    FirebaseUser user=auth.getCurrentUser();
    if(user==null){message="Inicia sesión para recuperar tu compra.";publish();return;}
    if(!user.isEmailVerified()){
      user.reload().addOnCompleteListener(activity,result->{
        if(!result.isSuccessful()||auth.getCurrentUser()==null||!auth.getCurrentUser().isEmailVerified()){
          message="Verifica el correo de tu cuenta antes de recuperar Premium.";publish();
        }else refreshLicense();
      });return;
    }
    if(endpoint==null){message="Falta configurar el servidor HTTPS de licencias.";publish();return;}
    checking=true;message="Consultando el servidor seguro de licencias...";publish();
    user.getIdToken(true).addOnCompleteListener(activity,result->{
      if(!result.isSuccessful()||result.getResult()==null){checking=false;message="No fue posible autenticar la consulta.";publish();return;}
      final String idToken=result.getResult().getToken();
      new Thread(()->{
        boolean accepted=false;String detail="No se pudo verificar la licencia.";
        HttpsURLConnection conn=null;
        try{
          URL url=new URL(endpoint+"/v1/license/status");
          conn=(HttpsURLConnection)url.openConnection();
          conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
          conn.setRequestMethod("GET");conn.setUseCaches(false);
          conn.setRequestProperty("Authorization","Bearer "+idToken);
          int status=conn.getResponseCode();
          if(status==200){
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
              byte[] block=new byte[1024];int n;
              while((n=in.read(block))!=-1){out.write(block,0,n);if(out.size()>8192)throw new Exception("Respuesta demasiado grande");}
              JSONObject response=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
              accepted=response.optBoolean("premium",false)&&"pagocheck_premium_unico".equals(response.optString("productId"))
                &&"verificada".equals(response.optString("reason"));
              detail=accepted?"Licencia verificada por el servidor.":"No existe una licencia Premium verificada para esta cuenta.";
            }
          }else detail="Servidor de licencias no disponible ("+status+").";
        }catch(Exception e){detail="Sin conexión segura con el servidor de licencias.";}
        finally{if(conn!=null)conn.disconnect();}
        final boolean valid=accepted;final String info=detail;
        activity.runOnUiThread(()->{
          // No conservar derechos de un usuario diferente tras un cierre de sesión.
          if(revision!=authRevision)return;
          licenseVerified=valid&&auth.getCurrentUser()!=null&&user.getUid().equals(auth.getCurrentUser().getUid());
          checking=false;message=info;publish();
        });
      },"pc-license-check").start();
    });
  }
  /**
   * Reenvío del token de compra al servidor. El token y el Firebase ID token
   * jamás llegan a JavaScript, a los respaldos ni a los registros de finanzas.
   */
  void verifyPurchase(String purchaseToken){
    if(purchaseToken==null||purchaseToken.length()<20||purchaseToken.length()>4096||
       !purchaseToken.matches("[A-Za-z0-9._:-]+")){
      message="Token de compra inválido.";publish();return;
    }
    if(!readyForPurchaseVerification()){
      message="Inicia sesión, verifica el correo y configura el servidor para recuperar Premium.";
      licenseVerified=false;publish();return;
    }
    FirebaseUser user=auth.getCurrentUser();
    final long revision=++authRevision;
    checking=true;licenseVerified=false;message="Verificando compra con Google Play de forma segura...";publish();
    user.getIdToken(true).addOnCompleteListener(activity,result->{
      if(revision!=authRevision)return;
      if(!result.isSuccessful()||result.getResult()==null){
        checking=false;message="No fue posible autenticar la compra.";publish();return;
      }
      final String bearer=result.getResult().getToken();
      new Thread(()->{
        boolean accepted=false;
        String detail="No se pudo verificar la compra.";
        HttpsURLConnection conn=null;
        try{
          URL url=new URL(endpoint+"/v1/license/verify");
          conn=(HttpsURLConnection)url.openConnection();
          conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
          conn.setRequestMethod("POST");
          conn.setUseCaches(false);
          conn.setDoOutput(true);
          conn.setRequestProperty("Authorization","Bearer "+bearer);
          conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
          JSONObject request=new JSONObject();
          request.put("purchaseToken",purchaseToken);
          byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);
          if(bytes.length>8192)throw new Exception("Solicitud demasiado grande");
          try(java.io.OutputStream out=conn.getOutputStream()){out.write(bytes);}
          int status=conn.getResponseCode();
          if(status==200){
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
              byte[] block=new byte[1024];int n;
              while((n=in.read(block))!=-1){
                out.write(block,0,n);
                if(out.size()>8192)throw new Exception("Respuesta demasiado grande");
              }
              JSONObject response=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
              accepted=response.optBoolean("premium",false)&&
                "pagocheck_premium_unico".equals(response.optString("productId"))&&
                "verificada".equals(response.optString("reason"));
              detail=accepted?"Compra Premium verificada.":"Compra no validada por el servidor.";
            }
          }else detail="El servidor de licencias no pudo confirmar la compra ("+status+").";
        }catch(Exception e){
          detail="No fue posible verificar la compra mediante una conexión segura.";
        }finally{if(conn!=null)conn.disconnect();}
        final boolean valid=accepted;final String messageText=detail;
        activity.runOnUiThread(()->{
          if(revision!=authRevision)return;
          licenseVerified=valid&&auth.getCurrentUser()!=null&&
            user.getUid().equals(auth.getCurrentUser().getUid())&&
            auth.getCurrentUser().isEmailVerified();
          checking=false;message=messageText;publish();
        });
      },"pc-purchase-verify").start();
    });
  }
  private void publish(){
    JSONObject o=new JSONObject();
    try{
      FirebaseUser user=auth==null?null:auth.getCurrentUser();
      o.put("configured",auth!=null);
      o.put("serverConfigured",endpoint!=null);
      o.put("signedIn",user!=null);
      o.put("emailVerified",user!=null&&user.isEmailVerified());
      o.put("email",user==null?"":String.valueOf(user.getEmail()));
      o.put("checking",checking);
      o.put("licenseVerified",licenseVerified);
      o.put("message",message);
    }catch(Exception ignored){}
    listener.onStatus(o.toString());
  }
}