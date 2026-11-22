const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(js);
const start=js.indexOf('function isoDay('),end=js.indexOf('function exportData()',start);
assert.ok(start>=0&&end>start);
const code=js.slice(start,end);
function setup(){
 const calls=[];
 const db={transactions:[
 {id:'1',date:'2026-11-01',month:'2026-11',kind:'income',amount:70000,description:'Sueldo',category:'Salario',source:'manual',reversed:false},
 {id:'2',date:'2026-11-12',month:'2026-11',kind:'expense',amount:25000,description:'Electricidad',category:'Servicios',source:'manual',reversed:false},
 {id:'3',date:'2026-11-21',month:'2026-11',kind:'expense',amount:10000,description:'Alimentación',category:'Alimentación',source:'manual',reversed:false},
 {id:'4',date:'2026-11-10',month:'2026-11',kind:'expense',amount:9000,description:'Anulado',category:'Servicios',reversed:true},
 {id:'5',date:'2026-12-01',month:'2026-12',kind:'expense',amount:50000,description:'Diciembre',category:'Servicios',reversed:false}
 ]};
 const context={db,month:'2026-11',safe:x=>String(x).replace(/&/g,'&amp;').replace(/</g,'&lt;'),crc:n=>'₡'+n,$:id=>calls.find(x=>x.id===id)||null,window:{},document:{},alert:()=>{},calls};
 vm.createContext(context);vm.runInContext(code,context);return context;
}
test('primera quincena excluye diciembre, segunda quincena y reversos',()=>{
 const c=setup(),r=c.reportDataset('2026-11-01','2026-11-15');
 assert.equal(r.tx.length,2);assert.equal(r.sums.income,70000);assert.equal(r.sums.expense,25000);
});
test('semana y quincena usan fechas inclusivas sin duplicar operaciones',()=>{
 const c=setup(),r=c.reportDataset('2026-11-16','2026-11-30');
 assert.equal(r.tx.length,1);assert.equal(r.sums.expense,10000);
});
test('exportaciones numérica y gráfica contienen los totales reales',()=>{
 const c=setup(),csv=c.generateCSV('2026-11-01','2026-11-30'),visual=c.generateHTMLReport('2026-11-01','2026-11-30');
 assert.ok(csv.includes('"Electricidad"'));assert.ok(csv.includes('"35000"'));
 assert.ok(visual.includes('Distribución por categoría'));assert.ok(visual.includes('Electricidad'));
 assert.ok(!visual.includes('Diciembre'));
});
