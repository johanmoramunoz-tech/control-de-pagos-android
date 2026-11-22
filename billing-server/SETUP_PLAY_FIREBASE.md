# Activación de pruebas de PagoCheck IA Premium — pendiente de acceso

**Estado: preparado, no desplegado.** Los cambios de infraestructura se habilitarán únicamente tras revisar Play Console, Firebase y el backend en el proyecto autorizado.

## 1. Google Play Console (realizar en la cuenta propietaria)
- Registrar o seleccionar la aplicación con **applicationId: `cr.controles.pagos`**.
- Comprobar la titularidad y la clave de firma/Play App Signing. El APK de prueba actual no es un sustituto del AAB distribuido por Google Play.
- Crear un producto de compra única, **no consumible**, con ID exacto **`pagocheck_premium_unico`**. Configurar opción de compra, disponibilidad y precio de forma explícita.
- Configurar la cuenta de prueba en **License testing**; incluirla en el canal de pruebas internas y confirmar que usa el mismo perfil de Google Play en el dispositivo.
- No utilizar tarjeta bancaria real para la primera prueba; verificar que Google Play muestre el diálogo de **test purchase**.

## 2. Firebase (con autorización)
- Elegir un proyecto específico para PagoCheck IA, separado de otras aplicaciones.
- Registrar la app Android `cr.controles.pagos`.
- Habilitar Firebase Authentication con Email/Password y verificar correo.
- Preparar Firestore en región seleccionada conscientemente y activar `firestore.rules` del repositorio.
- Mantener acceso a Firestore limitado a la cuenta de servicio. Nunca enviar tokens o secretos en el chat.
- Revisar requisitos de supresión de cuenta y retención de información de compra.

## 3. Google Cloud y Play Developer API
- Vincular Play Console con el proyecto Google Cloud correspondiente.
- Habilitar Android Publisher API.
- Otorgar permisos mínimos para validar/reconocer compras a una cuenta de servicio.
- Preparar un servicio HTTPS en Cloud Run con identidad administrada y sin credenciales JSON incorporadas a la imagen.
- Configurar variables no secretas y políticas IAM. El backend utiliza el permiso Android Publisher; **no habilitar `LICENSE_VERIFICATION_ENABLED=true`** hasta superar pruebas de integración, seguridad y anulaciones.
- Establecer gestión de mensajes RTDN y anulaciones en la Voided Purchases API.

## 4. Compilar aplicación de prueba
- En la compilación se necesitan `PC_FIREBASE_APP_ID`, `PC_FIREBASE_API_KEY`, `PC_FIREBASE_PROJECT_ID`, `PC_LICENSE_SERVER_URL`.
- Las claves de Firebase para cliente no son credenciales de cuenta de servicio. No incorporar claves privadas al APK.
- No activar cobros reales: la aplicación mantiene `LIVE_PURCHASES_ENABLED=false`.
- Para compras de prueba reales se requiere primero implementar y validar una vía segura de habilitación del flujo Billing, tras completar la verificación de servidor y permisos de publicación.

## 5. Prueba de extremo a extremo
1. Instalar desde Play (pruebas internas), iniciar sesión Premium con correo verificado.
2. Confirmar que el producto configurado y el precio aparecen.
3. Verificar que Google Play ofrece un método de **pago de prueba**.
4. Comprar como tester; observar PURCHASED sin conceder derechos antes de servidor.
5. Validar compra mediante Google Play Developer API; asociar token con UID correcto y confirmar reconocimiento.
6. Probar recuperar compra tras reinstalación y rechazo al usar otra cuenta.
7. Probar PENDING, cancelación/reembolso y revocación con RTDN + conciliación Voided Purchases.
8. Verificar que los datos financieros siguen exclusivamente en el teléfono.

**No hay compra real efectuada ni servicio remoto desplegado mientras la cuenta no esté conectada y autorizada.**
