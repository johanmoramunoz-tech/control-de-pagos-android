const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const src=fs.readFileSync('app/src/main/java/cr/controles/pagos/MainActivity.java','utf8');
test('avisos del WebView usan encabezado propio y botón Entendido',()=>{
 assert.match(src,/@Override public boolean onJsAlert\(/);
 assert.match(src,/setTitle\("PagoCheck IA · Atención"\)/);
 assert.match(src,/setPositiveButton\("Entendido",\(dialog,which\)->result\.confirm\(\)\)/);
});
test('confirmaciones conservan las opciones aceptar, cancelar y cierre',()=>{
 assert.match(src,/@Override public boolean onJsConfirm\(/);
 assert.match(src,/setTitle\("PagoCheck IA · Confirmación"\)/);
 assert.match(src,/setPositiveButton\("Confirmar",\(dialog,which\)->result\.confirm\(\)\)/);
 assert.match(src,/setNegativeButton\("Cancelar",\(dialog,which\)->result\.cancel\(\)\)/);
 assert.equal((src.match(/setOnCancelListener\(dialog->result\.cancel\(\)\)/g)||[]).length,2);
});
test('no se modifica el comportamiento de la interfaz financiera',()=>{
 const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
 assert.match(html,/function recordBudgetExpense\(/);
 assert.match(html,/function finishPayment\(/);
 assert.match(html,/function receiveLocalProposal\(/);
});
