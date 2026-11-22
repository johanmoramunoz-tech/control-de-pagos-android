package cr.controles.pagos;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Deterministic financial extraction after OCR or speech-to-text.
 * All output is UNVERIFIED and must be confirmed by the user before recording.
 * No images or statements are sent to any server by this class.
 */
final class LocalReceiptParser {
  private static final Pattern MONEY = Pattern.compile("(?i)(?:₡|CRC\\s*|COLONES\\s*)?\\s*(\\d{1,3}(?:[.,\\s]\\d{3})+(?:[.,]\\d{2})?|\\d{1,9}(?:[.,]\\d{2})?)");
  private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b");
  private static final Pattern ISO_DATE = Pattern.compile("\\b(20\\d{2})-(\\d{2})-(\\d{2})\\b");
  private static final Pattern AMOUNT_WORDS = Pattern.compile("(?i)(?:₡|CRC|COLONES|MONTO|POR|DE|PAGU[EÉ]|RECIB[IÍ]|COBR[EÉ])\\s*(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{1,9})(?:\\s*COLONES)?");
  static final class Candidate {
    final long amount; final int score; final String source;
    Candidate(long n,int s,String line){amount=n;score=s;source=line;}
  }
  static JSONObject parseReceipt(String ocr) throws Exception {
    String raw=ocr==null?"":ocr.trim();
    if(raw.isEmpty())throw new Exception("No se detectó texto legible. Intenta fotografiar el comprobante con más luz.");
    String[] lines=raw.split("\\r?\\n");
    List<Candidate> found=new ArrayList<>();
    boolean clearlyUsd=Pattern.compile("(?i)\\bUSD\\b|US\\$|D[OÓ]LARES|\\bDOLLARS\\b").matcher(raw).find();
    boolean crc=Pattern.compile("(?i)₡|\\bCRC\\b|\\bCOLONES\\b").matcher(raw).find();
    String merchant="",date="",category="Otros gastos";
    for(int i=0;i<lines.length;i++){
      String line=lines[i].trim();String normalized=fold(line);
      if(merchant.isEmpty()&&i<8&&line.length()>3&&line.length()<70
           &&!normalized.matches(".*(FACTURA|CEDULA|TELEFONO|FECHA|NUMERO|CAJA|CLIENTE|TICKET|RECIBO|AUTORIZACION|\\d{3,}).*"))
         merchant=line;
      if(date.isEmpty())date=extractDate(line);
      if(normalized.matches(".*(SUPER|MERCADO|ABARROTE|ALIMENT|PANADER|RESTAURANT|SODA|PIZZA|COMIDA).*"))category="Alimentación";
      else if(normalized.matches(".*(GASOLIN|COMBUSTIBLE|ESTACION DE SERVICIO).*"))category="Combustible";
      else if(normalized.matches(".*(FARMACIA|MEDIC|CLINICA|HOSPITAL).*"))category="Salud";
      else if(normalized.matches(".*(ELECTRIC|AGUA|INTERNET|TELEFON|CABLE).*"))category="Servicios";
      int score=0;
      if(normalized.matches(".*\\b(TOTAL A PAGAR|IMPORTE TOTAL|TOTAL PAGADO|MONTO TOTAL|TOTAL NETO)\\b.*"))score+=15;
      else if(normalized.matches(".*\\bTOTAL\\b.*"))score+=10;
      else if(normalized.matches(".*\\bMONTO\\b.*"))score+=4;
      if(normalized.matches(".*(SUBTOTAL|SUB TOTAL|SUB-TOTAL|ANTES DE IMPUESTO|DESCUENTO|CAMBIO|VUELTO|EFECTIVO RECIBIDO|SALDO ANTERIOR|IMPUESTO|IVA|BASE IMPONIBLE).*"))score-=20;
      if(normalized.matches(".*(CEDULA|TELEFONO|FACTURA N|NUMERO DE|AUTORIZACION|TARJETA|CODIGO|REFERENCIA|TERMINAL|TRANSACCION).*"))score-=20;
      if(normalized.contains("₡")||normalized.matches(".*\\bCRC\\b.*"))score+=3;
      if(i>=lines.length/2)score+=1;
      Matcher m=MONEY.matcher(line);
      while(m.find()){
        String token=m.group(1);long number=parseMoney(token);
        if(number>0&&number<=1000000000L&&score>=0){
          // Discard isolated counters on nondescriptive lines.
          if(score==0&&number<100)continue;
          found.add(new Candidate(number,score,line));
        }
      }
    }
    Collections.sort(found,(a,b)->Integer.compare(b.score,a.score));
    JSONArray options=new JSONArray();List<Long> unique=new ArrayList<>();
    for(Candidate c:found){
      if(!unique.contains(c.amount)){unique.add(c.amount);
        options.put(new JSONObject().put("amount",c.amount).put("line",shorten(c.source,110)).put("score",c.score));
      }
      if(options.length()>=6)break;
    }
    long selected=0;
    String issue="El monto debe verificarse manualmente.";
    if(!found.isEmpty()){
      Candidate best=found.get(0);
      boolean competing=false;
      for(Candidate c:found){
        if(c.amount!=best.amount&&c.score>=best.score-2){competing=true;break;}
      }
      if(best.score>=8&&!competing){selected=best.amount;issue="Posible total localizado. Confirma el importe.";}
      else issue="Se detectaron importes ambiguos. Elige el total correcto o escríbelo.";
    }else issue="No fue posible identificar importes; revisa la factura.";
    if(clearlyUsd&&!crc){selected=0;issue="Se detectó una moneda distinta de colones. No se hará conversión automática.";}
    return new JSONObject().put("kind","expense").put("description",shorten(merchant.isEmpty()?"Compra según comprobante":merchant,100))
      .put("amount",selected).put("currency",clearlyUsd&&!crc?"USD":crc?"CRC":"unknown")
      .put("date",date).put("category",category).put("paidEvidence","not_confirmed")
      .put("note",issue).put("candidates",options).put("provider","OCR local")
      .put("requiresConfirmation",true);
  }
  static JSONObject parseSpeech(String spoken) throws Exception {
    String raw=spoken==null?"":spoken.trim();
    if(raw.isEmpty())throw new Exception("No se reconoció ninguna frase.");
    String norm=fold(raw);
    boolean income=norm.matches(".*\\b(RECIBI|COBRE|GANE|INGRESO|SALARIO|DEPOSITO RECIBIDO|ME PAGARON|ME INGRESARON)\\b.*");
    boolean expense=norm.matches(".*\\b(PAGUE|GASTE|COMPRE|COMPRA|GASTO|PAGO|ABONE|COMBUSTIBLE)\\b.*");
    String category="Otros gastos";
    if(norm.matches(".*(COMBUSTIBLE|GASOLINA|DIESEL).*"))category="Combustible";
    else if(norm.matches(".*(ALIMENT|COMIDA|SUPERMERCADO|RESTAURANTE|DESAYUNO|ALMUERZO|CENA).*"))category="Alimentación";
    else if(norm.matches(".*(ALQUILER|RENTA|HIPOTECA).*"))category="Vivienda";
    else if(norm.matches(".*(FARMACIA|MEDICAMENT|CLINICA|CONSULTA MEDICA).*"))category="Salud";
    else if(norm.matches(".*(TRANSPORTE|TAXI|AUTOBUS|UBER).*"))category="Transporte";
    else if(norm.matches(".*(INTERNET|AGUA|LUZ|ELECTRICIDAD|TELEFONO).*"))category="Servicios";
    if(income)category=norm.contains("SALARIO")?"Salario":"Otros ingresos";
    Matcher m=AMOUNT_WORDS.matcher(raw);long amount=0;
    if(m.find())amount=parseMoney(m.group(1));
    if(amount==0){
      Matcher all=MONEY.matcher(raw);if(all.find())amount=parseMoney(all.group(1));
    }
    String currency=(norm.contains("DOLAR")||norm.contains("USD"))?"USD":"CRC";
    if(amount<=0||amount>1000000000L)amount=0;
    if(!currency.equals("CRC"))amount=0;
    String date=extractDate(raw);
    return new JSONObject().put("kind",income?"income":"expense")
      .put("description",shorten(raw,100)).put("amount",amount).put("currency",currency)
      .put("date",date).put("category",category).put("paidEvidence","not_confirmed")
      .put("note",income&&expense?"La frase mezcla ingreso y egreso; revisa el tipo.":amount==0?"No se identificó un monto inequívoco en colones.":"Revisa y confirma lo interpretado.")
      .put("candidates",new JSONArray()).put("provider","Dictado Android + reglas locales")
      .put("requiresConfirmation",true);
  }
  private static String fold(String s){
    return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT);
  }
  private static String shorten(String x,int n){return x.length()>n?x.substring(0,n):x;}
  private static String extractDate(String s){
    Matcher iso=ISO_DATE.matcher(s);if(iso.find())return iso.group();
    Matcher m=DATE.matcher(s);if(!m.find())return "";
    int d=Integer.parseInt(m.group(1)),month=Integer.parseInt(m.group(2)),y=Integer.parseInt(m.group(3));
    if(y<100)y+=2000;
    if(y<2000||y>2100||month<1||month>12||d<1||d>31)return "";
    return String.format(Locale.ROOT,"%04d-%02d-%02d",y,month,d);
  }
  private static long parseMoney(String text){
    if(text==null)return 0;
    String t=text.replace(" ","").trim();
    if(t.matches("\\d{1,3}([.,]\\d{3})+[.,]\\d{2}"))t=t.substring(0,t.length()-3);
    else if(t.matches("\\d+[.,]\\d{2}"))t=t.substring(0,t.length()-3);
    t=t.replaceAll("[.,]","");
    try{return Long.parseLong(t);}catch(Exception e){return 0;}
  }
}
