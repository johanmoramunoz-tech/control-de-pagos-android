const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];assert.ok(js,'script unavailable');
const a=js.indexOf('function activeBudgets('),b=js.indexOf('function summary()',a);
assert.ok(a>=0&&b>a,'budget functions not found');
function harness(){
 const ctx={
  month:'2026-11',
  db:{budgets:[{id:'b1',name:'Alimentación',category:'Alimentación',estimate:200000,start:'2026-10',end:null,subcategories:['Carnes','Abarrotes'],overrides:{}}],
    transactions:[
      {id:'t1',month:'2026-11',kind:'expense',category:'Alimentación',subcategory:'Carnes',amount:4000,reversed:false,budgetId:'b1'},
      {id:'t2',month:'2026-11',kind:'expense',category:'Alimentación',subcategory:'Abarrotes',amount:20000,reversed:false,budgetId:null},
      {id:'t3',month:'2026-11',kind:'expense',category:'Transporte',amount:3000,reversed:false},
      {id:'t4',month:'2026-11',kind:'expense',category:'Alimentación',amount:9000,reversed:true},
      {id:'t5',month:'2026-10',kind:'expense',category:'Alimentación',amount:8000,reversed:false},
      {id:'t6',month:'2026-11',kind:'transfer',category:'Alimentación',amount:15000,reversed:false}
    ]},
  safe:x=>String(x),crc:n=>'₡'+n,iconFor:()=>'',categoryList:()=>[],popup:()=>{},uuid:()=>'',logging:()=>{},persist:()=>{},alert:()=>{},confirm:()=>true
 };
 vm.createContext(ctx);vm.runInContext(js.slice(a,b),ctx);return ctx;
}
test('facturas acumuladas y compras manuales descuentan solo una vez',()=>{
 const c=harness(),b=c.db.budgets[0];
 assert.equal(c.budgetUsed(b),24000);
 assert.equal(c.budgetLimit(b)-c.budgetUsed(b),176000);
 assert.equal(c.budgetExpenses(b).length,2);
});
test('reversos, transferencias y otros meses se excluyen del consumo',()=>{
 const c=harness(),b=c.db.budgets[0];assert.equal(c.budgetExpenses(b).length,2);
 assert.equal(c.budgetUsed(b,'2026-10'),8000);
});
test('la categoría puede asignar movimiento heredado sin budgetId',()=>{
 const c=harness();assert.equal(c.budgetOfTransaction(c.db.transactions[1])?.id,'b1');
});
test('límite mensual y proyección recurrente se distinguen',()=>{
 const c=harness(),b=c.db.budgets[0];b.overrides['2026-11']=150000;
 assert.equal(c.budgetLimit(b),150000);assert.equal(c.budgetLimit(b,'2026-12'),200000);
});
