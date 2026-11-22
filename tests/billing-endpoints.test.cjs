const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync('billing-server/server.cjs','utf8');
const verify=require('../billing-server/verify-purchase.cjs');

const TOKEN='valid_purchase_token_01234567890123456';

function setup({enabled=false,playState='PURCHASED',product=verify.PRODUCT_ID,uid='user-1'}={}){
 const docs=new Map();let apiCalls=0,claims=0,authCalls=0;
 let authorizedUid=uid;
 const play={purchaseStateContext:{purchaseState:playState},
  productLineItem:[{productId:product,productOfferDetails:{consumptionState:'CONSUMPTION_STATE_YET_TO_BE_CONSUMED'}}],
  acknowledgementState:'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'};
 function docRef(collection,id){
  const key=collection+'/'+id;
  return {
   get:async()=>({data:()=>docs.get(key)}),
   set:async(data,opt)=>{docs.set(key,opt?.merge?{...docs.get(key),...data}:data)},
   id
  };
 }
 function collection(name){
  return {
   doc:id=>docRef(name,id),
   where:(field,op,value)=>({get:async()=>({docs:[...docs.entries()].filter(([k,v])=>k.startsWith(name+'/')&&v[field]===value)
     .map(([k,v])=>({data:()=>v,ref:docRef(name,k.split('/').slice(1).join('/'))}))})})
  };
 }
 const firestore=()=>({
  collection,
  runTransaction:async callback=>callback({
   get:ref=>ref.get(),
   set:(ref,value,opt)=>{claims++;return ref.set(value,opt)}
  })
 });
 const fakeAdmin={
  apps:[{}],
  firestore:Object.assign(firestore,{FieldValue:{serverTimestamp:()=>'<serverTime>'}}),
  auth:()=>({verifyIdToken:async()=>{authCalls++;return {uid:authorizedUid,firebase:{sign_in_provider:'password'}}}})
 };
 class GoogleAuth {
  async getClient(){return {request:async ({url,method})=>{
   apiCalls++;
   return {data:method==='POST'?{}:play};
  }}}
 }
 const modules={http:require('node:http'),'google-auth-library':{GoogleAuth},
  'firebase-admin':fakeAdmin,'./verify-purchase.cjs':verify};
 const module={exports:{}};
 const context={module,exports:module.exports,process:{env:{LICENSE_VERIFICATION_ENABLED:enabled?'true':'false'}},
  require:name=>{if(!(name in modules))throw Error('unknown module '+name);return modules[name]},
  console};
 vm.createContext(context);vm.runInContext(source,context,{filename:'server.cjs'});
 async function call(route,method='GET',body={},bearer='a'.repeat(80)){
  const req={url:route,method,headers:{authorization:'Bearer '+bearer},
   async *[Symbol.asyncIterator](){if(method==='POST')yield JSON.stringify(body)}};
  const res={status:null,headers:null,body:'',
   writeHead(code,headers){this.status=code;this.headers=headers},
   end(data){this.body=data}
  };
  await module.exports.handle(req,res);
  return {status:res.status,body:JSON.parse(res.body)};
 }
 return {call,docs,play,stats:()=>({apiCalls,claims,authCalls}),setUid:u=>{authorizedUid=u}};
}
test('backend deshabilitado por defecto no permite verificar ni consultar licencias',async()=>{
 const h=setup();const result=await h.call('/v1/license/verify','POST',{purchaseToken:TOKEN});
 assert.equal(result.status,503);
 assert.equal(h.stats().apiCalls,0);
 assert.equal(h.stats().authCalls,0);
 const health=await h.call('/health');assert.equal(health.status,200);
 assert.equal(health.body.billingEnabled,false);
});
test('sin credenciales de cuenta no acepta solicitudes de licencia',async()=>{
 const h=setup({enabled:true});
 const r=await h.call('/v1/license/status','GET',{},'too_short');
 assert.equal(r.status,401);assert.equal(h.stats().apiCalls,0);
});
test('un producto diferente no se asocia ni concede derechos',async()=>{
 const h=setup({enabled:true,product:'unrelated_product'});
 const r=await h.call('/v1/license/verify','POST',{purchaseToken:TOKEN});
 assert.equal(r.status,200);assert.equal(r.body.premium,false);
 assert.equal(h.stats().claims,0);
 assert.equal(h.docs.size,0);
});
test('un pago PENDING no ocupa ni vincula un token',async()=>{
 const h=setup({enabled:true,playState:'PENDING'});
 const r=await h.call('/v1/license/verify','POST',{purchaseToken:TOKEN});
 assert.equal(r.body.premium,false);
 assert.equal(r.body.reason,'pending');
 assert.equal(h.stats().claims,0);
});
test('una compra válida se asocia a un único propietario y otro UID no puede reclamarla',async()=>{
 const h=setup({enabled:true});
 const first=await h.call('/v1/license/verify','POST',{purchaseToken:TOKEN});
 assert.equal(first.body.premium,true);
 assert.equal(h.stats().claims,1);
 h.setUid('user-2');
 const second=await h.call('/v1/license/verify','POST',{purchaseToken:TOKEN});
 assert.equal(second.body.premium,false);
 assert.equal(second.body.reason,'compra_vinculada_a_otra_cuenta');
});
test('si Play revoca compra, consulta posterior no devuelve Premium',async()=>{
 const h=setup({enabled:true});
 await h.call('/v1/license/verify','POST',{purchaseToken:TOKEN});
 h.play.purchaseStateContext.purchaseState='CANCELLED';
 const result=await h.call('/v1/license/status');
 assert.equal(result.body.premium,false);
});
test('el formato inválido del token no se entrega a Google Play',async()=>{
 const h=setup({enabled:true});
 const r=await h.call('/v1/license/verify','POST',{purchaseToken:'xyz'});
 assert.equal(r.status,400);
 assert.equal(h.stats().apiCalls,0);
});
