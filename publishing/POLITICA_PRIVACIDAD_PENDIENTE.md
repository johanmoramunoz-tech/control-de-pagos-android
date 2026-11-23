# PagoCheck IA — Auditoría preliminar de privacidad y protección de datos

**Estado: documento de trabajo, NO política pública final ni declaración definitiva de Seguridad de los datos.**
Fecha de revisión: 22 de noviembre de 2026.

## Evidencia verificada en el código
- La app Android utiliza `cr.controles.pagos` y solicita permiso de Internet y notificaciones. El manifiesto tiene `allowBackup=false` y `usesCleartextTraffic=false`.
- Las operaciones de presupuestos, ingresos, pagos, movimientos e informes se mantienen localmente según el diseño actual; el proyecto ofrece exportación/importación manual.
- Firebase Authentication permite registrar cuentas por correo y contraseña, enviar verificación, iniciar sesión y restablecer contraseña. El correo/identidad se procesa a través de Firebase.
- El módulo de licencias consulta de manera opcional un servidor HTTPS con un ID token de Firebase; el servidor todavía NO está desplegado.
- Google Play Billing está incluido, pero `LIVE_PURCHASES_ENABLED=false`; no hay compras comerciales activas.
- El reporte de soporte guarda localmente hasta 15 incidentes con código, fecha, versión y operación, y sólo se comparte por acción del usuario. No debe incorporar credenciales, UID, correo ni movimientos financieros.
- Existen funciones previstas de cámara, galería y reconocimiento local; hay que verificar permisos y cualquier transferencia antes de responder el formulario de Seguridad de los datos.

## Información pendiente de confirmar antes de publicar
1. Nombre público y dirección de contacto del responsable de datos; correo de soporte independiente del correo personal si es posible.
2. Flujos de datos reales de Firebase y de cualquier biblioteca de terceros; alcance de identificadores e información de diagnóstico.
3. Conservación y eliminación de cuentas de Firebase: implementar solicitud accesible dentro de la app **y** mediante una página web pública; eliminar los datos asociados, respetando obligaciones legales de conservación cuando sean aplicables.
4. Política de conservación/eliminación para datos locales, archivos exportados y eventuales tokens de compra.
5. Permisos reales de la APK de distribución, pruebas de tráfico, cifrado y acceso al almacenamiento.
6. Textos visibles al usuario, mecanismo de solicitudes de privacidad y atención de incidentes.
7. Validar si se utiliza algún servicio de IA en la versión efectivamente publicada y qué datos procesa; no atribuir sincronización en nube a funciones locales.
8. Definir y publicar URL accesible sin inicio de sesión, no PDF, para política de privacidad y URL de eliminación de cuenta.

## Reglas antes de completar Play Console
- No declarar «No se recopilan datos» mientras Firebase Authentication procese correo e identificadores asociados.
- No afirmar que la aplicación está libre de terceros: Firebase es un servicio externo.
- No confundir datos almacenados localmente con recopilación remota; describir cada flujo y las excepciones reales.
- No publicar esta política como final sin verificar implementación, contacto y retenciones.
- Vincular las prácticas declaradas en Play Console con la versión AAB finalmente subida.

Referencias oficiales:
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/10787469
