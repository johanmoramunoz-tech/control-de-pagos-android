const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];assert.ok(js);
function codeOf(start,end){
 const a=js.indexOf(start),b=js.indexOf(end,a+start.length);
 assert.ok(a>=0&&b>a,'fragment missing '+start);
 return js.slice(a,b);
}
function context(available=false){
 const modal=[],native=[],elements={};
 const ctx={localStatus:{available,preview:true,billingActive:false},safe:x=>String(x),tab:'home',month:'2026-11',
  popup:(title,body)=>modal.push({title,body}),close:()=>{},render:()=>{},alert:msg=>modal.push({title:'Alert',body:msg}),
  $:id=>elements[id]||null,document:{querySelectorAll:()=>[]},
  window:{AndroidLocal:{camera:()=>native.push('camera'),speech:()=>native.push('speech'),gallery:()=>native.push('gallery')}},
  modal,native,elements};
 vm.createContext(ctx);
 vm.runInContext('const PREMIUM_CONTEXT='+js.split('const PREMIUM_CONTEXT=')[1].split(';\nfunction premiumRibbon')[0]+';',ctx);
 vm.runInContext(codeOf('function premiumRibbon(','function bindPremiumRibbon('),ctx);
 return ctx;
}
test('todos los apartados gratuitos tienen acceso contextual para conocer Premium',()=>{
 const c=context(false);
 for(const section of ['home','obligations','budgets','movements','closing','categories','settings']){
   const html=c.premiumRibbon(section);
   assert.match(html,/IA Premium/);assert.match(html,/Ver Premium/);assert.match(html,/data-premium-action/);
 }
});
test('en Gratis los accesos Premium no ejecutan cámara ni voz',()=>{
 const c=context(false);c.handlePremiumAction('camera');c.handlePremiumAction('speech');
 assert.deepEqual(c.native,[]);
 assert.equal(c.modal.length,2);assert.match(c.modal[0].body,/No se requieren claves API/);
});
test('Premium de evaluación abre cámara y reconocimiento de voz',()=>{
 const c=context(true);c.handlePremiumAction('camera');c.handlePremiumAction('speech');
 assert.deepEqual(c.native,['camera','speech']);
 assert.match(c.premiumRibbon('budgets'),/Usar IA/);
});
test('la compra no se presenta como activa ni como plan de pago funcional',()=>{
 assert.match(js,/Google Play · compra única Premium/);
 assert.match(js,/No se realizarán cargos en esta compilación/i);
 assert.match(js,/setTestPremium/);
});
