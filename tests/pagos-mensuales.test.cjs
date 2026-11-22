const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
if(!js)throw Error('Sin JavaScript');
function fn(name,next){
 const start=js.indexOf('function '+name+'(');
 if(start<0)throw Error('No encontrada función: '+name);
 const end=js.indexOf('\nfunction '+next+'(',start);
 if(end<0)throw Error('No encontrada siguiente función '+next);
 return js.slice(start,end);
}
function harness(existing=0){
 const db={paymentChecks:{},transactions:existing?[{id:'prev',month:'2026-11',commitmentId:'c1',kind:'expense',amount:existing,date:'2026-11-02',description:'Internet',category:'Servicios',reversed:false}]:[],audit:[],monthClosed:{}};
 const c={id:'c1',kind:'expense',name:'Internet',category:'Servicios',estimate:10000,overrides:{},start:'2026-01',due:15};
 const context={db,month:'2026-11',today:()=> '2026-11-22',monthNow:()=> '2026-11',confirm:()=>true,alert:()=>{},locked:()=>false,currentCommitments:()=>[c],target:x=>x.overrides['2026-11']??x.estimate,related:x=>db.transactions.filter(t=>t.commitmentId===x.id&&!t.reversed),executed:x=>db.transactions.filter(t=>t.commitmentId===x.id&&!t.reversed).reduce((a,t)=>a+t.amount,0),uuid:()=> 'new'+db.transactions.length,logging:()=>{},persist:()=>true,render:()=>{},safe:x=>String(x),crc:n=>'₡'+n,openPaymentAdjustment:()=>{context.adjusted=true}};
 vm.createContext(context);
 const helpersStart=js.indexOf('function moneyDisplay('),helpersEnd=js.indexOf('const field=',helpersStart);
 vm.runInContext(js.slice(helpersStart,helpersEnd),context);
 vm.runInContext(fn('checked','status')+fn('setPaidCheck','monthlyCards')+fn('finishPayment','openPaymentAdjustment'),context);
 return {context,db,c};
}
test('confirmación rápida crea exactamente la diferencia sin duplicar abonos',()=>{
 const {context,db,c}=harness(3000);
 context.finishPayment('c1');
 assert.equal(db.transactions.length,2);
 assert.equal(db.transactions[1].amount,7000);
 assert.equal(db.transactions.reduce((a,t)=>a+t.amount,0),10000);
 assert.equal(context.checked(c),true);
 context.finishPayment('c1');
 assert.equal(db.transactions.length,2);
});
test('si monto ya está cubierto, el check no agrega un segundo movimiento',()=>{
 const {context,db,c}=harness(10000);
 context.finishPayment('c1');
 assert.equal(db.transactions.length,1);
 assert.equal(context.checked(c),true);
});
test('si pagos superan proyección se debe ajustar, no confirmar automáticamente',()=>{
 const {context,db,c}=harness(15000);
 context.finishPayment('c1');
 assert.equal(context.adjusted,true);
 assert.equal(db.transactions.length,1);
 assert.equal(context.checked(c),false);
});
test('un compromiso sin abonos genera pago proyectado una sola vez',()=>{
 const {context,db,c}=harness();
 context.finishPayment('c1');
 assert.equal(db.transactions[0].amount,10000);
 assert.equal(context.checked(c),true);
});

test('ajustar importe real y marcar completo registra solo diferencia',()=>{
 const {context,db,c}=harness(3000);
 const inputs={
  f_total:{value:'12500'},
  f_date:{value:'2026-11-22'},
  f_full:{checked:true},
  f_partial:{value:'0',disabled:false},
  f_note:{value:'Pago de noviembre'}
 };
 context.$=id=>inputs[id];
 context.field=()=>'';
 context.popup=(title,html,callback)=>{context.saveAdjustment=callback};
 vm.runInContext(fn('openPaymentAdjustment','openPayment'),context);
 context.openPaymentAdjustment('c1');
 assert.equal(context.saveAdjustment(),true);
 assert.equal(db.transactions.length,2);
 assert.equal(db.transactions[1].amount,9500);
 assert.equal(c.overrides['2026-11'],12500);
 assert.equal(context.checked(c),true);
});
test('seguimiento registra abono pero no activa estado pagado',()=>{
 const {context,db,c}=harness(3000);
 const inputs={
  f_total:{value:'10000'},
  f_date:{value:'2026-11-22'},
  f_full:{checked:false},
  f_partial:{value:'2000',disabled:false},
  f_note:{value:''}
 };
 context.$=id=>inputs[id];
 context.field=()=>'';
 context.popup=(title,html,callback)=>{context.saveAdjustment=callback};
 vm.runInContext(fn('openPaymentAdjustment','openPayment'),context);
 context.openPaymentAdjustment('c1');
 assert.equal(context.saveAdjustment(),true);
 assert.equal(db.transactions.length,2);
 assert.equal(db.transactions[1].amount,2000);
 assert.equal(context.checked(c),false);
});
