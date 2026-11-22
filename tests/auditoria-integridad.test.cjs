const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const js=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(js);
const start=js.indexOf('function validateBackup('),end=js.indexOf('function close(){',start);
const datesStart=js.indexOf('function parseDateInput('),datesEnd=js.indexOf('function displayDate(',datesStart);
assert.ok(start>=0&&end>start&&datesStart>0);
function harness(){
 const saved={schema:17,commitments:[{id:'c1',kind:'expense',name:'Electricidad',estimate:20000,category:'Servicios',start:'2026-11'}],transactions:[{id:'t1',kind:'expense',amount:20000,month:'2026-11',date:'2026-11-22',category:'Servicios',reversed:false}],monthClosed:{},audit:[],categories:[{id:'cat',name:'Servicios',kind:'expense',icon:'💡'}],budgets:[]};
 const ctx={db:{...saved,transactions:[]},migrate:()=>{ctx.db.schema=19},confirm:()=>true,persist:()=>true,
  alert:x=>ctx.messages.push(x),render:()=>{ctx.renders++},messages:[],renders:0};
 vm.createContext(ctx);
 vm.runInContext(js.slice(datesStart,datesEnd)+js.slice(start,end),ctx);
 return {saved,ctx};
}
test('restaura copia v17 y conserva una entrada financiera sin duplicación',()=>{
 const {saved,ctx}=harness();
 assert.equal(ctx.receiveBackup(JSON.stringify(saved)),undefined);
 assert.equal(ctx.db.schema,19);
 assert.equal(ctx.db.transactions.length,1);
 assert.equal(ctx.db.transactions[0].amount,20000);
 assert.equal(ctx.renders,1);
});
test('rechaza respaldo incompatible y conserva datos originales',()=>{
 const {saved,ctx}=harness();
 const previous=ctx.db;
 const broken={...saved,transactions:[{...saved.transactions[0],amount:-500}]};
 ctx.receiveBackup(JSON.stringify(broken));
 assert.equal(ctx.db,previous);
 assert.equal(ctx.renders,0);
 assert.match(ctx.messages[0],/Importación cancelada/);
});
test('rechaza respaldo malicioso con movimiento de mes incoherente',()=>{
 const {saved,ctx}=harness();
 const wrong={...saved,transactions:[{...saved.transactions[0],month:'2026-10'}]};
 ctx.receiveBackup(JSON.stringify(wrong));
 assert.equal(ctx.db.transactions.length,0);
 assert.equal(ctx.renders,0);
});
test('cancelación o error de almacenamiento no sustituye datos anteriores',()=>{
 const {saved,ctx}=harness();const previous=ctx.db;
 ctx.persist=()=>false;
 ctx.receiveBackup(JSON.stringify(saved));
 assert.equal(ctx.db,previous);
 assert.equal(ctx.renders,0);
 assert.match(ctx.messages[0],/Se conservaron los datos anteriores/);
});
test('rechaza copia con arrays inválidos',()=>{
 const {saved,ctx}=harness();const v={...saved,monthClosed:[]};
 assert.throws(()=>ctx.validateBackup(v),/incompatible/);
});
test('al renombrar categoría también se actualizan presupuestos asociados',()=>{
 const section=js.slice(js.indexOf('function editCategory('),js.indexOf('function deleteCategory('));
 assert.match(section,/db\.budgets\.forEach\(b=>\{if\(b\.category===old\)b\.category=name\}\)/);
});
test('avisa que los respaldos carecen de cifrado',()=>{
 assert.match(html,/respaldos JSON no están cifrados/i);
});
