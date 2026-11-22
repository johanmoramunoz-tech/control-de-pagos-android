# PagoCheck IA — Verificación de licencias Google Play (v21, PREPARACIÓN)

**Estado:** código de backend creado; **no desplegado**, sin credenciales, sin vinculación de cuentas Android y sin compras habilitadas.

## Componentes incluidos
- `verify-purchase.cjs`: valida formato de token, producto, estado PURCHASED/PENDING/CANCELLED y consumo.
- `server.cjs`: endpoints de verificación y consulta con Firebase ID token, Google Play Developer API y Firestore.
- `POST /v1/license/verify`: { "purchaseToken": "..." }; nunca confiar en el ID de usuario enviado por el cliente. Requiere `Authorization: Bearer <Firebase ID token>`.
- `GET /v1/license/status`: vuelve a consultar Google Play para negar acceso tras cancelación o pérdida de verificación.
- `GET /health`: informa que la facturación no está habilitada, sin datos de usuario.

La licencia **no se concede** sólo porque la app informe `PURCHASED`, ni por la presencia de un token, ni por un resultado PENDING. El servidor comprueba con `purchases.productsv2.getproductpurchasev2`. El token se vincula transaccionalmente a un único UID y se confirma el producto esperado `pagocheck_premium_unico` del paquete `cr.controles.pagos`.

## Requisitos para comenzar pruebas reales
1. Cuenta Play Console, app `cr.controles.pagos` registrada y producto **no consumible** con ID exacto `pagocheck_premium_unico`; definir precio y países **en Play Console**, no codificados en la app.
2. Proyecto Google Cloud vinculado a Play Console, habilitar Android Publisher API, cuenta de servicio con privilegios mínimos para validar y reconocer compras. No guardar claves en GitHub ni dentro del APK; preferir identidad administrada en Cloud Run.
3. Proyecto Firebase para autenticación, base Firestore y políticas de retención de tokens. Registrar usuario real (no anónimo), habilitar revocación y recuperación de cuenta; esta parte **NO está implementada en Android**.
4. Desplegar backend con acceso HTTPS, políticas IAM, límites de solicitudes, observabilidad sin registrar tokens, y control de coste; configurar Firestore.
5. Implementar en el cliente el envío autenticado del token (HTTPS), recuperación de compra y respuesta de licencia verificada. En producción NO incluir el interruptor de evaluación Premium; hasta completar la conexión debe mantenerse el flujo de compra **deshabilitado**.
6. Verificar con pruebas de licencia en Play Console: compra nueva, pendiente, cancelada, reembolso, compra repetida, otro usuario intentando usar mismo token, reconexión, reinstalación y compra desde cuenta distinta.
7. Activar notificaciones en tiempo real de compras (RTDN) y reconciliación de compras anuladas para revocaciones oportunas; el endpoint de estado sirve de verificación por demanda, no sustituye un procesamiento de cambios remoto.

## Seguridad y límites actuales
- El backend **almacena temporalmente el purchaseToken** para reconsultar Google Play. Tratarlo como secreto en Firestore; proteger con IAM y reglas que nieguen acceso directo de usuarios, definir retención y eliminación por solicitud legal.
- El servicio necesita desplegarse y probarse con credenciales reales antes de usarse. No puede considerarse una licencia comercial completa todavía.
- **No se debe activar** `LIVE_PURCHASES_ENABLED` ni usar este servidor para recibir pagos comerciales antes de finalizar autenticación móvil, devoluciones, cuentas, recuperación, pruebas y revisión de seguridad.
- Se requiere revisión de manejo de errores, idempotencia, políticas de eliminación y notificaciones antes de producción.
- Respaldo financiero JSON no cifra datos. Es independiente de la compra Premium.

## API de Google utilizada
- `GET /androidpublisher/v3/applications/{packageName}/purchases/productsv2/tokens/{token}`
- `POST /androidpublisher/v3/applications/{packageName}/purchases/products/{productId}/tokens/{token}:acknowledge`

Nota: Google Play exige reconocer las compras confirmadas oportunamente; las transacciones PENDING no se reconocen como completadas.
