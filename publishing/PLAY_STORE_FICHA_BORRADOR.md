# PagoCheck IA — Ficha preliminar para Google Play
**Estado:** borrador interno; no publicar hasta que se contraste con el AAB real.

## Nombre (máximo 30 caracteres)
PagoCheck IA

## Descripción breve (máximo 80 caracteres)
Organiza tus pagos, ingresos, gastos y recordatorios mensuales.

## Descripción detallada preliminar
PagoCheck IA permite organizar compromisos financieros y presupuestos personales desde Android.

Registra ingresos estimados, gastos recurrentes, vencimientos, pagos efectuados y abonos parciales. Clasifica tus movimientos, consulta informes por período y revisa resúmenes de tus registros.

Puedes programar recordatorios de obligaciones y exportar o importar respaldos manuales. La información financiera se administra principalmente en el dispositivo; no se conecta a cuentas bancarias ni ejecuta transferencias.

La aplicación incluye registro e inicio de sesión mediante Firebase para la futura recuperación de licencias. El registro de una cuenta no desbloquea Premium ni implica una compra.

**No declarar en Play Store como funciones disponibles mientras no estén implementadas y comprobadas:** análisis de comprobantes por IA en la nube, sincronización automática, servidor de licencias, suscripciones, compras comerciales y recuperación de compras.

## Estado comercial comprobado en código
- Modelo de distribución actual: versión de prueba con herramientas gratuitas y simulación de funciones Premium, sin pagos reales.
- Google Play Billing tiene `LIVE_PURCHASES_ENABLED=false`.
- Producto previsto: `pagocheck_premium_unico` (compra única, no consumible); aún sin producto configurado en Play Console ni servidor operativo.
- La simulación Premium de debug debe eliminarse o permanecer inaccesible en Release.
- No anunciar suscripción ni niveles Plus/Premium IA hasta que su modelo, disponibilidad y facturación estén establecidos y probados.

## Lista de preparación antes de crear ficha pública
- [ ] Cuenta Play Console con identidad/teléfonos verificados.
- [ ] Registro de la app `cr.controles.pagos`, ficha, categoría, correo de soporte y audiencia.
- [ ] Política de privacidad pública exacta y declaración Seguridad de los datos contrastada con el AAB.
- [ ] Eliminación de cuenta desde la app y recurso web público para solicitudes.
- [ ] Actualizar `targetSdk` de 35 a 36 o superior, actualizando Gradle/SDK si corresponde, y ejecutar pruebas de regresión.
- [ ] Generar AAB **Release** firmado con la configuración de carga de Play; la construcción de `bundleRelease` en CI actual no es todavía un paquete de distribución firmado.
- [ ] Preparar icono, banner, capturas reales de la variante de distribución y descripción sin funciones ficticias.
- [ ] Comprobar avisos y requisitos de aplicaciones que gestionan datos financieros y políticas aplicables.
- [ ] Preparar al menos 12 testers continuos durante 14 días en prueba cerrada si aplica a la cuenta personal nueva; documentar pruebas y correcciones.
- [ ] Antes de habilitar compras: producto Play, servidor HTTPS seguro, permisos mínimos, verificación de compra, reconocimiento y cancelaciones/reembolsos probados.
- [ ] Revisar advertencias de versiones obsoletas de acciones en GitHub antes de publicar.

Referencias oficiales:
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/9859152
- https://support.google.com/googleplay/android-developer/answer/10144311
