# PagoCheck IA — Verificación de licencias Google Play (v24, PREPARACIÓN)

**Estado:** backend con protecciones reforzadas, **no desplegado**, sin credenciales, sin vinculación de cuentas Android y sin compras habilitadas.

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

## Verificaciones incorporadas en v22
- El backend devuelve HTTP 503 en todos los endpoints de licencia mientras `LICENSE_VERIFICATION_ENABLED` no sea exactamente `true`. Activar esa variable NO basta para poder publicar: requiere los elementos de la lista anterior y revisión de seguridad externa.
- Tokens no válidos, compras pendientes, canceladas, consumidas o con estado de consumo no determinado se rechazan ANTES de asociar el token a una cuenta.
- `firestore.rules` niega lectura/escritura directa a clientes sobre `play_purchases` y `premium_users`. Deben aplicarse efectivamente al proyecto Firebase cuando se configure; el archivo en GitHub por sí solo no las activa.
- El estado de compra se consulta a la API oficial de Google; `purchases.productsv2` no sustituye la Voided Purchases API para revocaciones y devoluciones.
- Las compras y las cuentas en Android aún NO están conectadas a este backend. No activar el cobro ni tratar la integración como producción.
- Por seguridad, nunca pegar claves de servicio ni tokens de compra en mensajes o archivos públicos.

## Datos que deben configurarse fuera del repositorio
- ID de proyecto Firebase y configuración Android correspondiente, mediante el proceso oficial de Firebase.
- Enlace a la cuenta Play Console y producto no consumible `pagocheck_premium_unico`.
- Servicio privado HTTPS con identidad administrada y acceso exclusivo a Android Publisher API y Firestore.
- Políticas de privacidad, tratamiento de reembolsos, eliminación de cuenta, retención de datos y canal de soporte.
- Aprobación de los mecanismos de prueba de compras y licencias de Google Play.

## v23: cuenta de compra en Android (sin servicio habilitado)
El módulo nativo `LicenseAccountManager.java` admite registro, inicio de sesión, confirmación del correo y consulta HTTPS de derechos mediante `GET /v1/license/status`. **No** sincroniza los registros ni envía importes, documentos, facturas o contraseñas al WebView. La cuenta sólo sirve para recuperar Premium.

Se deben suministrar **en la compilación Android**, desde variables de entorno del proceso de construcción (no en mensajes ni en el repositorio):
- `PC_FIREBASE_APP_ID`: identificador de app Android del proyecto Firebase, que corresponde al paquete `cr.controles.pagos`.
- `PC_FIREBASE_API_KEY`: clave pública de configuración Android Firebase (no es una cuenta de servicio ni un secreto de backend).
- `PC_FIREBASE_PROJECT_ID`: proyecto de autenticación.
- `PC_LICENSE_SERVER_URL`: URL base HTTPS del servidor (sin rutas adicionales, consultas, credenciales o puertos alternativos).

Por defecto, las variables son cadenas vacías: la aplicación es **Gratis y local**, los controles de cuenta aparecen deshabilitados, el servidor de licencia no se consulta y las compras siguen bloqueadas.

Antes de implementar cobros:
1. Crear proyecto Firebase propio y registrar la app Android con paquete correcto.
2. Habilitar inicio de sesión Email/Password y verificar emails; revisar las cuotas, privacidad y manejo de recuperación de cuentas.
3. Configurar el backend con identidad de servicio y TLS y comprobar `GET /v1/license/status` con Firebase ID token y correo validado.
4. Implementar vinculación del token de compra real al usuario autenticado, notificaciones de revocación (RTDN y Voided Purchases) y renovación de licencias. **La v23 sólo prepara la consulta de licencias, no el envío del token de compra.**
5. Revisar y probar qué ocurre al cambiar de cuenta, cerrar sesión, reinstalar y usar sin Internet. El servidor sigue deshabilitado por defecto y la interfaz no usa la respuesta para abrir funciones de pago.
6. Antes de comenzar distribución comercial, eliminar el interruptor de Premium de prueba de compilaciones Release y habilitar permisos sólo con una licencia verificada.

## v24: integración Android - Play Billing - servidor
Se preparó la recuperación de compras no consumibles confirmadas por Google Play mediante `PlayBillingManager.verifyOwnedPurchases()`. El `purchaseToken` se entrega directamente al componente Android `LicenseAccountManager.verifyPurchase(...)`: nunca se muestra en el WebView, los respaldos financieros ni las notificaciones.

El módulo Android utiliza la sesión Firebase con correo verificado y solicita un ID token renovado. Envía `POST /v1/license/verify` por HTTPS, autentica al usuario, recibe la verificación del backend y actualiza el acceso Premium. Se bloquea cualquier operación si falta configuración o credenciales. La misma operación puede servir para recuperar una compra previa.

El acceso Premium en modo **Release** queda condicionado a la licencia verificada del servidor. El desbloqueo de prueba permanece limitado a variantes Android `debug`. En el código actual `LIVE_PURCHASES_ENABLED=false`; no se inicia ni cobra ninguna compra.

**Sigue pendiente para producción:** configurar Google Play Console, Firebase, servidor HTTPS, producto no consumible y pruebas con licencia real; activar el cobro solamente después de validar los eventos de compra, anulaciones y recibos; controlar caducidad de la sesión y comprobar funcionamiento offline. Los registros financieros continúan en el almacenamiento del teléfono.
