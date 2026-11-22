const test=require('node:test'),assert=require('node:assert/strict');
const {hashToken,assessPurchase,secureResponse,parseAuthHeader,PRODUCT_ID}=require('../billing-server/verify-purchase.cjs');
function purchase(state,product=PRODUCT_ID,ack='ACKNOWLEDGEMENT_STATE_PENDING',cons='CONSUMPTION_STATE_YET_TO_BE_CONSUMED'){
 return {purchaseStateContext:{purchaseState:state},productLineItem:[{productId:product,productOfferDetails:{consumptionState:cons}}],acknowledgementState:ack};
}
test('compra verificada del producto esperado requiere estado PURCHASED',()=>{
 const v=assessPurchase(purchase('PURCHASED'));assert.equal(v.valid,true);assert.equal(v.acknowledged,false);
});
test('compra pendiente no entrega Premium ni debe reconocerse',()=>{
 assert.deepEqual(assessPurchase(purchase('PENDING')),{valid:false,reason:'pending'});
});
test('compra cancelada o devuelta no entrega Premium',()=>{
 assert.deepEqual(assessPurchase(purchase('CANCELLED')),{valid:false,reason:'cancelled'});
});
test('producto diferente o consumo previo no reciben licencia',()=>{
 assert.equal(assessPurchase(purchase('PURCHASED','otro_producto')).valid,false);
 assert.equal(assessPurchase(purchase('PURCHASED',PRODUCT_ID,undefined,'CONSUMPTION_STATE_CONSUMED')).valid,false);
});
test('no admite compra sin estado explícito de consumo de Play',()=>{
 const p=purchase('PURCHASED');delete p.productLineItem[0].productOfferDetails.consumptionState;
 assert.equal(assessPurchase(p).valid,false);
});
test('no admite indicio de reembolso completo',()=>{
 const p=purchase('PURCHASED');p.productLineItem[0].productOfferDetails.quantity=1;
 p.productLineItem[0].productOfferDetails.refundableQuantity=0;
 assert.equal(assessPurchase(p).reason,'possible_refund');
});
test('estado inesperado o respuesta incompleta fallan de forma segura',()=>{
 assert.equal(assessPurchase(purchase('PURCHASE_STATE_UNSPECIFIED')).valid,false);
 assert.equal(assessPurchase({}).valid,false);
});
test('token sólo se registra como hash irreversible al usar identificador',()=>{
 const value='purchasetoken01234567890123456789';
 assert.match(hashToken(value),/^[a-f0-9]{64}$/);
 assert.equal(hashToken(value),hashToken(value));
 assert.notEqual(hashToken(value),hashToken(value+'more'));
 assert.throws(()=>hashToken('invalid'));
});
test('cabecera de autorización estricta',()=>{
 assert.equal(parseAuthHeader('Basic abc'),null);
 assert.equal(parseAuthHeader('Bearer abc'),null);
 const jwt='a'.repeat(80);
 assert.equal(parseAuthHeader('Bearer '+jwt),jwt);
});
test('no se concede premium por un estado desconocido',()=>{
 assert.equal(secureResponse(false,'pendiente').premium,false);
 assert.equal(secureResponse('true','sin_verificar').premium,false);
});
