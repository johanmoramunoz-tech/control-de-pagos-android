const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const start=html.indexOf('function targetForMonth(c,m){');
const end=html.indexOf('function monthlyCards(arr,kind){',start);
assert.ok(start>=0&&end>start);
const code=html.slice(start,end);
const context={
  db:{commitments:[],transactions:[],paymentChecks:{},carryovers:[]},
  month:'2026-12',
  previousMonthOf:m=>{const [y,n]=m.split('-').map(Number);const d=new Date(y,n-2,1);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')},
  activeBudgets:()=>[],budgetLimit:()=>0,
  crc:x=>'₡'+x,safe:x=>x,locked:()=>false,
  String,Number,Math,Date,Map,Object,
};
vm.createContext(context);
vm.runInContext(code,context);
const c={id:'electric',kind:'expense',name:'Electricidad',category:'Servicios',start:'2026-01',end:null,estimate:40000,overrides:{}};
test('pendiente de noviembre se propone sin alterar obligación de diciembre',()=>{
 context.db.commitments=[c];
 context.db.transactions=[];
 const p=context.sourcePendingFor('2026-12');
 assert.equal(p.length,1);assert.equal(p[0].amount,40000);
 assert.equal(context.targetForMonth(c,'2026-12'),40000);
});
test('pagos parciales de noviembre reducen el traslado',()=>{
 context.db.transactions=[{id:'nov-pay',kind:'expense',month:'2026-11',amount:15000,commitmentId:'electric',reversed:false}];
 assert.equal(context.outstandingFromMonth(c,'2026-11'),25000);
});
test('el arrastre no crea gasto y no puede proponerse otra vez',()=>{
 const item={id:'carry-electric',sourceMonth:'2026-11',targetMonth:'2026-12',commitmentId:'electric',name:'Electricidad',category:'Servicios',amount:25000};
 context.db.carryovers=[item];
 assert.equal(context.sourcePendingFor('2026-12').length,0);
 assert.equal(context.carryList('2026-12').length,1);
});
test('pago de deuda anterior no se cuenta como cuota de diciembre',()=>{
 context.db.transactions.push({id:'dec-carry',month:'2026-12',date:'2026-12-04',kind:'expense',amount:25000,commitmentId:'electric',carryoverId:'carry-electric',reversed:false});
 assert.equal(context.carryRemaining(context.db.carryovers[0]),0);
 assert.equal(context.carryList('2026-12').length,0);
 // related() debe excluir explícitamente los pagos arrastrados.
 assert.match(html,/function related\(c\)\{return ledger\(\)\.filter\(t=>!t\.carryoverId&&/);
 assert.equal(context.targetForMonth(c,'2026-12'),40000);
});
test('reversar un pago deja pendiente el arrastre',()=>{
 context.db.transactions.find(t=>t.id==='dec-carry').reversed=true;
 assert.equal(context.carryRemaining(context.db.carryovers[0]),25000);
});
test('historial de traslados se conserva en los respaldos',()=>{
 assert.match(html,/Array\.isArray\(db\.carryovers\)/);
 assert.match(html,/v\.carryovers!==undefined/);
 assert.match(html,/db=v;migrate\(\)/);
});
