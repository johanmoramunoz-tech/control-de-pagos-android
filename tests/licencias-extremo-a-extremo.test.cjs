const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const billing=fs.readFileSync('app/src/main/java/cr/controles/pagos/PlayBillingManager.java','utf8');
const account=fs.readFileSync('app/src/main/java/cr/controles/pagos/LicenseAccountManager.java','utf8');
const main=fs.readFileSync('app/src/main/java/cr/controles/pagos/MainActivity.java','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
test('recupera sólo compras PURCHASED del producto esperado',()=>{
 assert.match(billing,/void verifyOwnedPurchases\(/);
 assert.match(billing,/purchase\.getProducts\(\)\.contains\(PREMIUM_ID\)/);
 assert.match(billing,/Purchase\.PurchaseState\.PURCHASED/);
 assert.match(billing,/verification\.onPurchaseToken\(purchase\.getPurchaseToken\(\)\)/);
});
test('el token de Google Play se maneja en Android sin llegar a WebView ni exportación',()=>{
 assert.match(main,/licenseAccount\.verifyPurchase\(token\)/);
 assert.match(account,/void verifyPurchase\(String purchaseToken\)/);
 assert.match(account,/request\.put\("purchaseToken",purchaseToken\)/);
 assert.match(account,/conn\.setRequestMethod\("POST"\)/);
 assert.match(account,/HttpsURLConnection/);
 assert.doesNotMatch(main,/sendAiCallback\("receivePlayBillingStatus",token\)/);
 assert.doesNotMatch(html,/purchaseToken/);
});
test('licencia válida requiere respuesta del servidor HTTPS y cuenta autenticada',()=>{
 assert.match(account,/if\(!readyForPurchaseVerification\(\)\)/);
 assert.match(account,/user\.getIdToken\(true\)/);
 assert.match(account,/status==200/);
 assert.match(account,/"verificada"\.equals\(response\.optString\("reason"\)\)/);
 assert.match(account,/user\.getUid\(\)\.equals\(auth\.getCurrentUser\(\)\.getUid\(\)\)/);
 assert.match(account,/revision!=authRevision/);
});
test('la app puede activar reconocimiento sólo tras licencia validada o prueba depurable',()=>{
 assert.match(main,/private boolean hasPremiumAccess\(\)\{return premiumPreview\(\)\|\|\(licenseAccount!=null&&licenseAccount\.hasVerifiedLicense\(\)\);\}/);
 assert.match(main,/if\(!hasPremiumAccess\(\)\)/);
 assert.match(main,/if\(!hasPremiumAccess\(\)\|\|uri==null\)return/);
 assert.match(main,/o\.put\("available",hasPremiumAccess\(\)\)/);
});
test('el modo comercial permanece sin cobros incluso con licencia nativa preparada',()=>{
 assert.match(billing,/LIVE_PURCHASES_ENABLED=false/);
 assert.match(billing,/if\(!LIVE_PURCHASES_ENABLED\)/);
 assert.match(html,/Comprar Premium · No disponible/);
 assert.match(main,/private boolean premiumPreview\(\)\{return isDebugBuild\(\)/);
});
test('recuperación en UI nunca solicita copiar un token en texto',()=>{
 assert.match(html,/Recuperar compra Premium/);
 assert.match(html,/AndroidPlayBilling\?\.restorePurchase/);
 assert.match(main,/void restorePurchase\(\)/);
});
