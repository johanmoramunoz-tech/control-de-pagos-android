package cr.controles.pagos;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;

public class LocalReceiptParserTest {
 @Test public void totalBeatsSubtotalAndChange() throws Exception {
  JSONObject r=LocalReceiptParser.parseReceipt("SUPERMERCADO CENTRAL\nFECHA 22/11/2026\nSUBTOTAL ₡25.000\nIVA ₡3.250\nTOTAL A PAGAR ₡28.250\nEFECTIVO RECIBIDO ₡30.000\nCAMBIO ₡1.750");
  assertEquals(28250L,r.getLong("amount"));
  assertEquals("2026-11-22",r.getString("date"));
  assertEquals("not_confirmed",r.getString("paidEvidence"));
  assertEquals("CRC",r.getString("currency"));
 }
 @Test public void ambiguousTotalsRequireReview() throws Exception {
  JSONObject r=LocalReceiptParser.parseReceipt("FACTURA\nTOTAL ₡20.000\nTOTAL ₡25.000");
  assertEquals(0L,r.getLong("amount"));
  assertTrue(r.getJSONArray("candidates").length()>=2);
 }
 @Test public void noAmountDoesNotFabricateCharge() throws Exception {
  JSONObject r=LocalReceiptParser.parseReceipt("TIENDA EJEMPLO\nGracias por su visita");
  assertEquals(0L,r.getLong("amount"));
 }
 @Test public void voiceExpenseInColones() throws Exception {
  JSONObject r=LocalReceiptParser.parseSpeech("Pagué 25000 colones por combustible");
  assertEquals(25000L,r.getLong("amount"));
  assertEquals("expense",r.getString("kind"));
  assertEquals("Combustible",r.getString("category"));
 }
 @Test public void voiceIncome() throws Exception {
  JSONObject r=LocalReceiptParser.parseSpeech("Recibí 600000 colones de salario");
  assertEquals(600000L,r.getLong("amount"));
  assertEquals("income",r.getString("kind"));
 }
 @Test public void foreignCurrencyRequiresManualConversion() throws Exception {
  JSONObject r=LocalReceiptParser.parseReceipt("RECEIPT\nTOTAL USD 20.00");
  assertEquals(0L,r.getLong("amount"));
  assertEquals("USD",r.getString("currency"));
 }
}
