const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const source=html.slice(html.indexOf('function previousMonthOf(m){'),html.indexOf('function tile(label,n){'));
const context={
 db:{transactions:[],commitments:[],budgets:[]},
 activeBudgets:()=>[],
 budgetLimit:()=>0,
 Object,Number,Date,Map,Math,String
};
vm.createContext(context);vm.runInContext(source,context);
test('ingresos y gastos se calculan sin contar transferencias o reversos',()=>{
 context.db.transactions=[
 {month:'2026-11',kind:'income',amount:300000},
 {month:'2026-11',kind:'expense',amount:90000,category:'Vivienda'},
 {month:'2026-11',kind:'expense',amount:10000,category:'Otros',reversed:true},
 {month:'2026-11',kind:'transfer',amount:100000},
 ];
 const a=context.financialInsights('2026-11');
 assert.equal(a.income,300000);assert.equal(a.spending,90000);
});
test('los posibles gastos hormiga requieren repetición y umbral',()=>{
 context.db.transactions=[
 {month:'2026-11',kind:'expense',amount:4000,category:'Comida',subcategory:'Café'},
 {month:'2026-11',kind:'expense',amount:4000,category:'Comida',subcategory:'Café'},
 {month:'2026-11',kind:'expense',amount:4000,category:'Comida',subcategory:'Café'},
 {month:'2026-11',kind:'expense',amount:3000,category:'Transporte',subcategory:'Bus'}
 ];
 const a=context.financialInsights('2026-11');
 assert.equal(a.recurring.length,1);assert.equal(a.recurring[0].total,12000);
});
test('se avisa cuando gastos previstos superan ingresos',()=>{
 context.db.transactions=[];
 context.db.commitments=[
 {kind:'income',start:'2026-01',estimate:200000},
 {kind:'expense',start:'2026-01',estimate:250000}
 ];
 const a=context.financialInsights('2026-11');
 assert.equal(a.projectedIncome,200000);assert.equal(a.projectedExpenses,250000);
});
test('el informe Premium no sustituye la validación de licencia',()=>{
 assert.match(html,/if\(!localStatus\.available\)\{showPremiumInfo\(\);return;\}/);
 assert.match(html,/id="analyzeFinancialMonth"/);
 assert.match(html,/monthlyAdvisorHtml\(month\)/);
});
