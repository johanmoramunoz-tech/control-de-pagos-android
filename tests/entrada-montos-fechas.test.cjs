const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(js,'script no encontrado');
const start=js.indexOf('function moneyDisplay(');
const end=js.indexOf('const field=',start);
assert.ok(start>0&&end>start,'funciones de dinero y fecha');
const snippet=js.slice(start,end);
function context(fields={}){
 const ctx={
  $:id=>fields[id]||null,
  today:()=>'2026-11-22',
  document:{},window:{},safe:x=>String(x??''),
  confirm:()=>true,
  alert:msg=>{ctx.alerts.push(msg)},
  alerts:[]
 };
 vm.createContext(ctx);
 vm.runInContext(snippet,ctx);
 return ctx;
}
test('montos enteros aparecen separados por millares',()=>{
 const c=context();
 assert.equal(c.moneyDisplay('4000'),'4.000');
 assert.equal(c.moneyDisplay('40000'),'40.000');
 assert.equal(c.moneyDisplay('200000'),'200.000');
 assert.equal(c.moneyDisplay('4000.000'),'4.000.000');
 assert.equal(c.moneyRead('4.000'),4000);
 assert.equal(c.moneyRead('40.000'),40000);
 assert.equal(c.moneyRead('200000'),200000);
 assert.equal(c.moneyRead('4,000'),4000);
 assert.ok(Number.isNaN(c.moneyRead('4.00')));
});
test('admite fechas de usuario y del calendario',()=>{
 const c=context();
 assert.equal(c.parseDateInput('22/11/2026'),'2026-11-22');
 assert.equal(c.parseDateInput('22-11-2026'),'2026-11-22');
 assert.equal(c.parseDateInput('22.11.2026'),'2026-11-22');
 assert.equal(c.parseDateInput('2026-11-22'),'2026-11-22');
 assert.equal(c.displayDate('2017-11-22'),'22/11/2017');
 assert.equal(c.parseDateInput('31/02/2026'),'');
 assert.equal(c.parseDateInput('29/02/2024'),'2024-02-29');
 assert.equal(c.parseDateInput('29/02/2026'),'');
});
test('compra de carnes de ₡4.000 el 22/11/2026 se registra en presupuesto seleccionado',()=>{
 const fields={f_description:{value:'Carnes'},f_amount:{value:'4.000'},f_date:{value:'22/11/2026'},f_subcategory:{value:'Carnes'},f_note:{value:'Carnes'}};
 const c=context(fields);
 const budget={id:'food',name:'Alimentación',category:'Alimentación',estimate:200000,start:'2026-11'};
 const records=[];
 Object.assign(c,{
  locked:()=>false,activeBudgets:()=>[budget],budgetLimit:()=>200000,budgetUsed:()=>0,
  month:'2026-11',monthNow:()=> '2026-11',crc:n=>'₡'+n,
  field:()=>'',budgetSubcategorySelect:()=>'',popup:(title,form,cb)=>{c.save=cb},
  addTx:tx=>{records.push(tx);return true}
 });
 const a=js.indexOf('function recordBudgetExpense('),b=js.indexOf('function detailBudget(',a);
 vm.runInContext(js.slice(a,b),c);
 c.recordBudgetExpense('food');
 assert.equal(c.save(),true,c.alerts.join(';'));
 assert.equal(records.length,1);
 assert.equal(records[0].date,'2026-11-22');
 assert.equal(records[0].amount,4000);
 assert.equal(records[0].subcategory,'Carnes');
 assert.equal(records[0].budgetId,'food');
});
