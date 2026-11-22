'use strict';
/**
 * Backend de preparación. No desplegar sin configuración y pruebas de Play Console.
 * API: POST /v1/license/verify {purchaseToken} con Authorization: Bearer <Firebase ID token>
 *     GET /v1/license/status con el mismo encabezado.
 * No acepta ID de usuario de la petición, nunca entrega datos financieros.
 */
const http=require('node:http');
const {GoogleAuth}=require('google-auth-library');
const admin=require('firebase-admin');
const {PRODUCT_ID,PACKAGE_NAME,hashToken,assessPurchase,secureResponse,parseAuthHeader}=require('./verify-purchase.cjs');

if(!admin.apps.length)admin.initializeApp();
const store=admin.firestore();
const googleAuth=new GoogleAuth({scopes:['https://www.googleapis.com/auth/androidpublisher']});
const packageName=process.env.PLAY_PACKAGE_NAME||PACKAGE_NAME;
if(packageName!==PACKAGE_NAME)throw new Error('El identificador de paquete difiere del APK publicado');

function respond(res,code,payload){
 res.writeHead(code,{'content-type':'application/json; charset=utf-8','cache-control':'no-store',
  'x-content-type-options':'nosniff'});
 res.end(JSON.stringify(payload));
}
async function authorize(req){
 const token=parseAuthHeader(req.headers.authorization);
 if(!token)throw Object.assign(new Error('Se requiere sesión de cuenta verificada'),{status:401});
 const identity=await admin.auth().verifyIdToken(token,true);
 if(!identity?.uid||identity.firebase?.sign_in_provider==='anonymous')
  throw Object.assign(new Error('Se requiere usuario autenticado, no anónimo'),{status:401});
 return identity.uid;
}
async function googleRequest(url,method='GET'){
 const client=await googleAuth.getClient();
 const response=await client.request({url,method,headers:{'content-type':'application/json'},data:method==='POST'?{}:undefined});
 return response.data;
}
function playUrl(token){
 return 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+
 encodeURIComponent(packageName)+'/purchases/productsv2/tokens/'+encodeURIComponent(token);
}
function ackUrl(token){
 return 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+
 encodeURIComponent(packageName)+'/purchases/products/'+encodeURIComponent(PRODUCT_ID)+
 '/tokens/'+encodeURIComponent(token)+':acknowledge';
}
const purchases=store.collection('play_purchases');
const users=store.collection('premium_users');
async function refreshClaim(uid,token){
 const hash=hashToken(token),ref=purchases.doc(hash);
 const play=await googleRequest(playUrl(token));
 const decision=assessPurchase(play);
 // Un token inválido o una compra PENDING jamás se asocian a una cuenta.
 // De otro modo, un tercero podría bloquear una futura compra legítima.
 if(!decision.valid)return secureResponse(false,decision.reason);
 const now=admin.firestore.FieldValue.serverTimestamp();
 const claim=await store.runTransaction(async trx=>{
  const doc=await trx.get(ref);
  const data=doc.data();
  if(data?.uid&&data.uid!==uid)return 'owned_by_other_user';
  trx.set(ref,{uid,productId:PRODUCT_ID,packageName,tokenHash:hash,purchaseToken:token,
   state:'processing',lastVerified:now},{merge:true});
  return 'accepted';
 });
 if(claim==='owned_by_other_user')return secureResponse(false,'compra_vinculada_a_otra_cuenta');
 // Confirmación sólo después de verificación Play y reclamo durable.
 // Si falla acknowledgment, la llamada se puede repetir sin dar acceso.
 if(!decision.acknowledged){
  await googleRequest(ackUrl(token),'POST');
 }
 await ref.set({state:'active',lastVerified:admin.firestore.FieldValue.serverTimestamp()},{merge:true});
 await recalcUser(uid);
 return secureResponse(true,'verificada');
}
async function recalcUser(uid){
 const all=await purchases.where('uid','==',uid).get();
 const active=all.docs.some(d=>d.data().state==='active'&&d.data().productId===PRODUCT_ID);
 await users.doc(uid).set({active,updatedAt:admin.firestore.FieldValue.serverTimestamp()},{merge:true});
 return active;
}
async function verifiedStatus(uid){
 // Cada consulta al estado comprueba con Google Play para detectar cancelaciones
 // incluso si faltan notificaciones RTDN. Nunca confiar sólo en Firestore.
 const mine=await purchases.where('uid','==',uid).get();
 let any=false;
 for(const doc of mine.docs){
  const claim=doc.data();
  if(!claim.purchaseToken||claim.productId!==PRODUCT_ID)continue;
  try{
   const purchase=await googleRequest(playUrl(claim.purchaseToken));
   const decision=assessPurchase(purchase);
   const active=decision.valid&&decision.acknowledged;
   await doc.ref.set({state:active?'active':decision.reason,lastVerified:admin.firestore.FieldValue.serverTimestamp()},{merge:true});
   if(active)any=true;
  }catch(error){
   // Sin respuesta Google, falla cerrada: no anunciar licencia nueva.
   await doc.ref.set({state:'unverified'},{merge:true});
  }
 }
 await recalcUser(uid);
 return secureResponse(any,any?'verificada':'sin_licencia_verificada');
}
async function readJSON(req){
 let data='';
 for await(const chunk of req){
  data+=chunk;
  if(data.length>8192)throw Object.assign(new Error('Solicitud demasiado grande'),{status:413});
 }
 try{return JSON.parse(data)}catch{throw Object.assign(new Error('JSON inválido'),{status:400})}
}
async function handle(req,res){
 if(req.url==='/health'&&req.method==='GET')
  return respond(res,200,{status:'ok',billingEnabled:false,verificationConfigured:process.env.LICENSE_VERIFICATION_ENABLED==='true'});
 // Este prototipo no puede publicarse sin credenciales, Firebase, políticas de acceso
 // y conciliación de anulaciones. Deshabilitado por defecto incluso si se despliega accidentalmente.
 if(process.env.LICENSE_VERIFICATION_ENABLED!=='true')
  return respond(res,503,{error:'Servicio de licencias aún no habilitado'});
 if(!['POST','GET'].includes(req.method))return respond(res,405,{error:'Método no admitido'});
 if(!['/v1/license/verify','/v1/license/status'].includes(req.url))
  return respond(res,404,{error:'Ruta desconocida'});
 if((req.url==='/v1/license/verify'&&req.method!=='POST')||
    (req.url==='/v1/license/status'&&req.method!=='GET'))
  return respond(res,405,{error:'Método no admitido'});
 try{
  const uid=await authorize(req);
  if(req.url==='/v1/license/status')return respond(res,200,await verifiedStatus(uid));
  const {purchaseToken}=await readJSON(req);
  try{hashToken(purchaseToken)}catch(e){throw Object.assign(new Error('Token de compra inválido'),{status:400})}
  return respond(res,200,await refreshClaim(uid,purchaseToken));
 }catch(error){
  // No filtrar tokens, credenciales ni datos sensibles en respuestas.
  const status=error.status||503;
  return respond(res,status,{error:status<500?error.message:'No fue posible verificar la licencia; inténtalo nuevamente'});
 }
}
const port=Number(process.env.PORT||8080);
if(require.main===module){
 http.createServer((req,res)=>{handle(req,res).catch(()=>respond(res,503,{error:'Verificación no disponible'}));})
 .listen(port,'0.0.0.0');
}
module.exports={handle,refreshClaim,verifiedStatus};