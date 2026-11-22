package cr.controles.pagos;

import android.app.Activity;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/**
 * Preparación Google Play Billing v8.2.1. Ninguna compra comercial se habilita
 * hasta conectar un servidor que valide purchaseToken con Google Play Developer API,
 * permita gestionar devoluciones y vincule el derecho a una cuenta.
 */
final class PlayBillingManager implements PurchasesUpdatedListener {
    static final String PREMIUM_ID="pagocheck_premium_unico";
    static final boolean LIVE_PURCHASES_ENABLED=false;
    interface Listener { void onUpdate(String json); }
    interface VerifiedPurchaseRequest { void onPurchaseToken(String token); }
    private final Activity activity;
    private final Listener listener;
    private final VerifiedPurchaseRequest verification;
    private final BillingClient client;
    private ProductDetails product;
    private String offerToken;
    private String price;
    private String state="disconnected";
    private String message="Google Play no conectado";
    private boolean ownedOnPlay=false;
    private boolean pending=false;

    PlayBillingManager(Activity activity,Listener listener,VerifiedPurchaseRequest verification){
        this.activity=activity;this.listener=listener;this.verification=verification;
        client=BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        publish();
    }
    void connect(){
        if(client.isReady()){refresh();return;}
        state="connecting";message="Conectando con Google Play";publish();
        client.startConnection(new BillingClientStateListener(){
            @Override public void onBillingSetupFinished(BillingResult result){
                if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                    state="unavailable";message="Google Play no disponible ("+result.getResponseCode()+")";publish();return;
                }
                state="connected";message="Google Play conectado";publish();
                refresh();
            }
            @Override public void onBillingServiceDisconnected(){
                state="disconnected";message="Conexión con Google Play interrumpida";publish();
            }
        });
    }
    void refresh(){
        if(!client.isReady()){connect();return;}
        queryProduct();
        QueryPurchasesParams args=QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryPurchasesAsync(args,(result,purchases)->{
            if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                message="No fue posible recuperar las compras de Google Play";publish();return;
            }
            updatePurchases(purchases);
        });
    }
    /**
     * Recuperación explícita de compras. Sólo se envía un token confirmado
     * a la capa nativa de licencias; nunca a JavaScript.
     */
    void verifyOwnedPurchases(){
        if(!client.isReady()){message="Google Play no conectado";publish();return;}
        QueryPurchasesParams args=QueryPurchasesParams.newBuilder()
          .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryPurchasesAsync(args,(result,purchases)->{
            if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
                message="No fue posible consultar compras anteriores";publish();return;
            }
            updatePurchases(purchases);
            if(purchases!=null)for(Purchase purchase:purchases){
                if(purchase.getProducts().contains(PREMIUM_ID)&&
                   purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED){
                    verification.onPurchaseToken(purchase.getPurchaseToken());
                    return;
                }
            }
        });
    }
    private void queryProduct(){
        QueryProductDetailsParams.Product q=QueryProductDetailsParams.Product.newBuilder()
          .setProductId(PREMIUM_ID)
          .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryProductDetailsAsync(
          QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(q)).build(),
          (result,details)->{
            product=null;offerToken=null;price=null;
            if(result.getResponseCode()==BillingClient.BillingResponseCode.OK){
                for(ProductDetails p:details.getProductDetailsList()){
                    if(!PREMIUM_ID.equals(p.getProductId()))continue;
                    product=p;
                    List<ProductDetails.OneTimePurchaseOfferDetails> offers=p.getOneTimePurchaseOfferDetailsList();
                    if(offers!=null&&!offers.isEmpty()){
                        offerToken=offers.get(0).getOfferToken();
                        price=offers.get(0).getFormattedPrice();
                    }else if(p.getOneTimePurchaseOfferDetails()!=null){
                        offerToken=p.getOneTimePurchaseOfferDetails().getOfferToken();
                        price=p.getOneTimePurchaseOfferDetails().getFormattedPrice();
                    }
                    break;
                }
            }
            publish();
          });
    }
    private void updatePurchases(List<Purchase> purchases){
        boolean owned=false,waiting=false;
        if(purchases!=null)for(Purchase purchase:purchases){
            if(!purchase.getProducts().contains(PREMIUM_ID))continue;
            if(purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED)owned=true;
            else if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING)waiting=true;
        }
        ownedOnPlay=owned;pending=waiting;
        // IMPORTANTE: un estado PURCHASED local NO equivale a una licencia validada.
        // No registrar ni reconocer la compra hasta que exista la verificación de servidor.
        if(owned)message="Google Play informa una compra; pendiente de verificación segura";
        else if(waiting)message="Pago pendiente de confirmación";
        else message="No se encontró una compra activa para esta cuenta de Google Play";
        publish();
    }
    void purchase(){
        // No cobrar sin entregar un derecho seguro verificado y reconocido.
        if(!LIVE_PURCHASES_ENABLED){message="La compra está desactivada hasta habilitar verificación de licencias";publish();return;}
        if(!client.isReady()||product==null||offerToken==null){
            message="Producto Premium no disponible en Google Play";publish();return;
        }
        BillingFlowParams.ProductDetailsParams detail=BillingFlowParams.ProductDetailsParams.newBuilder()
          .setProductDetails(product).setOfferToken(offerToken).build();
        BillingFlowParams flow=BillingFlowParams.newBuilder()
          .setProductDetailsParamsList(Collections.singletonList(detail)).build();
        BillingResult result=client.launchBillingFlow(activity,flow);
        if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK){
            message="Google Play no pudo iniciar la compra ("+result.getResponseCode()+")";publish();
        }
    }
    @Override public void onPurchasesUpdated(BillingResult result,List<Purchase> purchases){
        if(result.getResponseCode()==BillingClient.BillingResponseCode.OK){
            updatePurchases(purchases);
            if(purchases!=null)for(Purchase purchase:purchases){
                if(purchase.getProducts().contains(PREMIUM_ID)&&
                   purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED)
                    verification.onPurchaseToken(purchase.getPurchaseToken());
            }
            // Reconsultar evita inferir propiedad únicamente del callback.
            if(client.isReady())refresh();
        }else if(result.getResponseCode()!=BillingClient.BillingResponseCode.USER_CANCELED){
            message="Operación no completada en Google Play ("+result.getResponseCode()+")";publish();
        }
    }
    private void publish(){
        JSONObject o=new JSONObject();
        try{
            o.put("connected",client.isReady());
            o.put("state",state);
            o.put("message",message);
            o.put("productId",PREMIUM_ID);
            o.put("price",price==null?JSONObject.NULL:price);
            o.put("productConfigured",product!=null&&offerToken!=null);
            o.put("purchaseEnabled",LIVE_PURCHASES_ENABLED);
            o.put("ownedReportedByPlay",ownedOnPlay);
            o.put("pending",pending);
            o.put("licenseVerified",false);
        }catch(Exception ignored){}
        listener.onUpdate(o.toString());
    }
    void close(){client.endConnection();}
}