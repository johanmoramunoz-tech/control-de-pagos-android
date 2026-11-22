const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const java=fs.readFileSync('app/src/main/java/cr/controles/pagos/LicenseAccountManager.java','utf8');
const main=fs.readFileSync('app/src/main/java/cr/controles/pagos/MainActivity.java','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const gradle=fs.readFileSync('app/build.gradle','utf8');
const manifest=fs.readFileSync('app/src/main/AndroidManifest.xml','utf8');
const server=fs.readFileSync('billing-server/server.cjs','utf8');
test('registro e inicio de sesión sólo desde Android nativo',()=>{
 assert.match(java,/createUserWithEmailAndPassword/);
 assert.match(java,/signInWithEmailAndPassword/);
 assert.match(java,/sendEmailVerification/);
 assert.match(main,/addJavascriptInterface\(new LicenseAccountBridge\(\),"AndroidLicenseAccount"\)/);
 assert.doesNotMatch(html,/getIdToken\(/);
 assert.doesNotMatch(html,/purchaseToken\(/);
});
test('sin Firebase la app abre gratis y cuenta Premium queda deshabilitada',()=>{
 assert.match(java,/if\(appId\.isEmpty\(\)\|\|apiKey\.isEmpty\(\)\|\|projectId\.isEmpty\(\)\)/);
 assert.match(html,/id="licenseRegister" type="button" disabled/);
 assert.match(html,/id="licenseRestore" type="button" disabled/);
 assert.match(java,/if\(auth==null\)/);
});
test('tokens Firebase permanecen en Android y consulta requiere HTTPS',()=>{
 assert.match(java,/new FirebaseOptions\.Builder\(\)/);
 assert.match(java,/user\.getIdToken\(true\)/);
 assert.match(java,/HttpsURLConnection/);
 assert.match(java,/address\.startsWith\("https:\/\/"\)/);
 assert.match(java,/conn\.setRequestProperty\("Authorization","Bearer "\+idToken\)/);
 assert.match(java,/getString\(R\.string\.license_pc_server_url\)/);
});
test('no autorizar ni activar IA basándose sólo en JSON del servidor o Firebase',()=>{
 assert.match(html,/jamás desbloquear IA sólo con este JSON/);
 assert.match(main,/private boolean premiumPreview\(\)\{return isDebugBuild\(\)/);
 assert.match(server,/identity\.email_verified!==true/);
 assert.match(server,/LICENSE_VERIFICATION_ENABLED!=='true'/);
});
test('las claves y direcciones se suministran en compilación externa',()=>{
 assert.match(gradle,/PC_FIREBASE_APP_ID/);
 assert.match(gradle,/PC_FIREBASE_API_KEY/);
 assert.match(gradle,/PC_FIREBASE_PROJECT_ID/);
 assert.match(gradle,/PC_LICENSE_SERVER_URL/);
 assert.match(gradle,/firebase-bom:35\.2\.0/);
 assert.match(manifest,/android\.permission\.INTERNET/);
});
test('sin transferencia de presupuestos y movimientos al servidor',()=>{
 assert.doesNotMatch(java,/db\.transactions|db\.budgets|exportData\(/);
 assert.match(html,/no se envían al servidor de licencias/);
});
