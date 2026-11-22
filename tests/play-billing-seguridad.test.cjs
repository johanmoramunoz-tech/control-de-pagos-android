const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const billing=fs.readFileSync('app/src/main/java/cr/controles/pagos/PlayBillingManager.java','utf8');
const java=fs.readFileSync('app/src/main/java/cr/controles/pagos/MainActivity.java','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const gradle=fs.readFileSync('app/build.gradle','utf8');
test('compra bloqueada sin servidor de licencias',()=>{
 assert.match(billing,/LIVE_PURCHASES_ENABLED=false/);
 assert.match(billing,/if\(!LIVE_PURCHASES_ENABLED\)/);
 assert.match(billing,/licenseVerified",false/);
 assert.match(html,/buyPremium" disabled/);
});
test('compra única y recuperación desde Google Play',()=>{
 assert.match(billing,/PREMIUM_ID="pagocheck_premium_unico"/);
 assert.match(billing,/ProductType\.INAPP/);
 assert.match(billing,/queryPurchasesAsync/);
 assert.match(billing,/PurchaseState\.PURCHASED/);
 assert.match(billing,/PurchaseState\.PENDING/);
 assert.doesNotMatch(billing,/acknowledgePurchase\(/);
});
test('no desbloquea OCR con informe local de compra',()=>{
 assert.match(java,/private boolean premiumPreview\(\)\{return isDebugBuild\(\)/);
 assert.doesNotMatch(java,/ownedReportedByPlay.*premiumPreview/);
 assert.match(html,/Cobros desactivados hasta verificar las licencias/);
});
test('la biblioteca existe y el puente Android está conectado',()=>{
 assert.match(gradle,/com\.android\.billingclient:billing:8\.2\.1/);
 assert.match(java,/AndroidPlayBilling/);
 assert.match(java,/playBilling\.close\(\)/);
});
