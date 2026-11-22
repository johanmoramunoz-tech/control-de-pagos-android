# PagoCheck IA v12 — Premium local, sin claves API

## Diseño aprobado
**Gratis:** el usuario registra manualmente movimientos, categorías y obligaciones, usa gráficos, recordatorios y respaldos.
**Premium:** reconocimiento OCR de facturas en el dispositivo, dictado de movimientos y propuesta editable que requiere confirmación antes de registrar.
**Cobro:** licencia Premium de pago único prevista. **No se ha integrado Google Play Billing ni validación de compra**, y no hay precio fijado. En el APK debug firmado v12, Premium se habilita solo para pruebas mediante el indicador Android FLAG_DEBUGGABLE. Las compilaciones no depurables no habilitan Premium sin una futura implementación real de licencias.

## Implementación actual
- Modelo ML Kit Latin Text Recognition V2 **empaquetado** en APK: `com.google.mlkit:text-recognition:16.0.1`.
- Foto: Intent de cámara con URI de FileProvider; también selector de imágenes del sistema.
- OCR en el dispositivo: `LocalOcr.java`.
- Extracción financiera local determinista: `LocalReceiptParser.java`, reglas de fecha, posibles importes TOTAL, categorías y advertencias.
- Dictado: RecognizerIntent, con preferencia por reconocimiento offline. **El servicio de voz instalado puede requerir red** según modelo y teléfono; no es un modelo propio incorporado en el APK.
- Revisión antes de guardar: importe, fecha, categoría, descripción, confirmación obligatoria, prevención básica de duplicados.
- La app Android **no solicita permiso INTERNET**; se retiró el antiguo cliente BYOK/IA remota.
- Los datos siguen locales en almacenamiento del WebView; no hay cuenta ni sincronización bancaria.

## Limitaciones honestas
- El OCR encuentra texto pero NO garantiza distinguir total, subtotal, cambio, IVA o comprobante de pago en todos los documentos.
- Cuando hay varios importes comparables o no aparece una línea TOTAL confiable, el motor entrega candidatos y solicita corrección.
- La presencia de factura NO equivale a pago realizado; el usuario debe declararlo.
- Las monedas extranjeras no se convierten automáticamente; la versión actual registra colones.
- No hay clasificación mediante LLM ni comprensión semántica compleja; la categorización son reglas.
- No existen pruebas instrumentadas sobre dispositivos reales todavía. Debe probarse con facturas reales variadas, incluyendo escaneo borroso.
- No reconocer PDFs ni recibos multipágina en esta primera etapa.
- No hay firma comercial ni compra Premium habilitada.

## Verificación recomendada en Honor/Android
1. Exportar respaldo JSON antes de instalar.
2. Actualizar v11→v12 sin desinstalar (mismo applicationId y clave firmante).
3. Confirmar funcionamiento de Gratis en modo avión.
4. Abrir «Planes e IA», observar Premium de evaluación.
5. Hacer foto de factura ficticia: confirmar que aparecen campos editables, corregir si necesario, exigir confirmación antes de guardar.
6. Verificar que al cancelar no se genere ningún movimiento.
7. Dictar «Pagué veinticinco mil colones por combustible hoy» y luego «Pagué 25000 colones por combustible»; registrar solo si importe correcto y usuario confirma.
8. Comprobar que no se duplican pagos accidentalmente y se preservan categorías eliminadas.
9. Probar sin acceso a internet la lectura OCR de varias facturas.

## Próxima etapa
- Mejoras robustas de reconocimiento de importes, múltiples líneas y fechas; pruebas por casos automatizadas.
- Motor de voz con capacidad offline controlada o advertencia visible según el reconocedor instalado.
- Validación de compras de pago único mediante Google Play Billing y protección de entitlement antes de abrir Premium en producción.
- Pruebas de privacidad y seguridad; declaración exacta de datos manejados en Play Store.
