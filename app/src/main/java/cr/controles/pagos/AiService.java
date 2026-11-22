package cr.controles.pagos;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** BYOK proof-of-concept: keys never exposed to WebView. */
final class AiService {
  private static final String PREF="pagocheck_private_ai",ALIAS="pagocheck_key_v1";
  private static final String PROMPT="Extrae UN movimiento financiero de la instrucción o imagen. Devuelve SOLO JSON con description (texto), amount (entero), currency (CRC u otra), date (AAAA-MM-DD o vacío), kind (income/expense), category (texto), paidEvidence (confirmed/probable/not_confirmed), note (texto). NO inventes montos ni fechas; si hay varios movimientos, coloca amount=0. La factura por sí sola NO demuestra un pago: paidEvidence=not_confirmed. No incluyas datos personales, cuentas ni tarjetas en el texto.";
  private final Context context;
  AiService(Context c){context=c.getApplicationContext();}
  private SharedPreferences prefs(){return context.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
  private SecretKey key()throws Exception{
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    if(!store.containsAlias(ALIAS)){
      KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
      gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
      gen.generateKey();store.load(null);
    }
    return (SecretKey)store.getKey(ALIAS,null);
  }
  void save(String provider,String token)throws Exception{
    if(!provider.equals("gemini")&&!provider.equals("openai"))throw new Exception("Proveedor inválido");
    if(token==null||token.trim().length()<15||token.length()>500)throw new Exception("Clave API inválida");
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
    prefs().edit().putString("provider",provider).putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP))
      .putString("secret",Base64.encodeToString(c.doFinal(token.trim().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).apply();
  }
  boolean isConfigured(){return prefs().contains("secret");}
  String provider(){return prefs().getString("provider","");}
  void clear(){prefs().edit().clear().apply();}
  private String token()throws Exception{
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs().getString("iv",""),Base64.NO_WRAP)));
    return new String(c.doFinal(Base64.decode(prefs().getString("secret",""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
  }
  JSONObject analyze(String instruction,String mime,byte[] picture)throws Exception{
    if(!isConfigured())throw new Exception("Configure su clave API propia en Planes e IA");
    if(picture!=null&&(picture.length>3000000||!(mime.equals("image/jpeg")||mime.equals("image/png")||mime.equals("image/webp"))))throw new Exception("Imagen incompatible o superior a 3 MB");
    String userText=PROMPT+"\nInformación: "+(instruction==null?"":instruction);
    JSONObject output;
    if(provider().equals("gemini")){
      JSONArray parts=new JSONArray().put(new JSONObject().put("text",userText));
      if(picture!=null)parts.put(new JSONObject().put("inline_data",new JSONObject().put("mime_type",mime).put("data",Base64.encodeToString(picture,Base64.NO_WRAP))));
      JSONObject body=new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("parts",parts))).put("generationConfig",new JSONObject().put("temperature",0.1).put("responseMimeType","application/json"));
      output=post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent","x-goog-api-key",token(),body);
      JSONArray results=output.optJSONArray("candidates");if(results==null||results.length()==0)throw new Exception("Gemini no devolvió resultado");
      output=new JSONObject(results.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"));
    }else if(provider().equals("openai")){
      JSONArray items=new JSONArray().put(new JSONObject().put("type","text").put("text",userText));
      if(picture!=null)items.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:"+mime+";base64,"+Base64.encodeToString(picture,Base64.NO_WRAP))));
      JSONObject body=new JSONObject().put("model","gpt-4o-mini").put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",items))).put("response_format",new JSONObject().put("type","json_object"));
      JSONObject result=post("https://api.openai.com/v1/chat/completions","Authorization","Bearer "+token(),body);
      output=new JSONObject(result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
    }else throw new Exception("Proveedor inválido");
    long amount=output.optLong("amount",0);
    if(amount<0||amount>1000000000)amount=0;
    String day=output.optString("date","");
    if(!day.matches("\\d{4}-\\d{2}-\\d{2}"))day="";
    String k=output.optString("kind","expense");
    if(!"income".equals(k))k="expense";
    return new JSONObject().put("description",limit(output.optString("description",""),100)).put("amount",amount)
      .put("currency",limit(output.optString("currency",""),10)).put("date",day).put("kind",k)
      .put("category",limit(output.optString("category",""),60)).put("note",limit(output.optString("note",""),240))
      .put("paidEvidence",limit(output.optString("paidEvidence","not_confirmed"),30)).put("provider",provider());
  }
  private JSONObject post(String url,String header,String secret,JSONObject payload)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(20000);c.setReadTimeout(55000);c.setRequestMethod("POST");
    c.setRequestProperty("Content-Type","application/json");c.setRequestProperty(header,secret);c.setDoOutput(true);
    try{
      try(OutputStream os=c.getOutputStream()){os.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
      int status=c.getResponseCode();
      if(status<200||status>=300){
        if(status==401||status==403)throw new Exception("Clave API rechazada ("+status+")");
        if(status==402||status==429)throw new Exception("Cuota o facturación insuficiente ("+status+")");
        throw new Exception("Error de servicio de IA ("+status+")");
      }
      try(InputStream in=c.getInputStream()){
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>300000)throw new Exception("Respuesta demasiado grande");out.write(buf,0,n);}
        return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
      }
    }finally{c.disconnect();}
  }
  private String limit(String s,int n){return s.length()>n?s.substring(0,n):s;}
}
