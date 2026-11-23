# PagoCheck IA — Ruta de publicación en Google Play
Actualización: 23 de noviembre de 2026.

## Estado confirmado
- Cuenta Play Console: **el desarrollador informa que su inscripción fue aprobada**. No confundir inscripción con autorización de producción para la app.
- Identificador de app: `cr.controles.pagos`.
- Configuración Android: compileSdk/targetSdk 36; Gradle 8.13 y AGP 8.13.2.
- Última compilación revisada: GitHub Actions #194, **success** (APK de prueba).
- El usuario ha probado en el teléfono el registro/importación/exportación y funciones principales. El traslado entre meses **no se ha probado con datos reales**.
- Los compromisos recurrentes ya aparecen por cada mes; el traslado de pendientes requiere selección manual y no crea automáticamente egresos.
- Firebase Authentication funciona para crear sesión y recuperar contraseña; eliminación de identidad incorporada, eliminación completa de datos asociados y recurso web **pendientes**.
- Cobros reales desactivados y backend de licencias aún no desplegado.

## Bloqueadores antes de abrir prueba cerrada
- [ ] Diseñar y verificar la política de privacidad pública con responsable y correo de contacto reales; revisar qué datos recopila Firebase y qué pasa con datos locales/respaldos.
- [ ] Habilitar una página web pública para solicitud de eliminación de cuenta y datos asociados; implementar proceso real de supresión (no basta borrar usuario Firebase).
- [ ] Revisar formulario Seguridad de los datos, contenido y clasificación, audiencia, anuncios, acceso a app y declaración de funciones financieras.
- [ ] Preparar firma de **carga de Google Play** para AAB Release. El workflow actual solo firma APK debug, y `:app:bundleRelease` no publica ni firma un paquete de distribución. No reutilizar automáticamente una llave de debug para publicación.
- [ ] Aumentar versionCode desde 24 para la primera carga de Play y verificar que cada nueva versión lo incrementa.
- [ ] Verificar ajustes de Release, Firebase Auth y certificado SHA correspondiente; diferenciar pruebas debug de distribución Play.
- [ ] Ocultar opciones de simulación Premium y mensajes internos de prueba en la variante pública; no anunciar compras sin backend seguro operativo.
- [ ] Revisar icono, capturas reales, banner, textos y accesibilidad.
- [ ] Probar recuperación/importación JSON, permisos, notificaciones, cierres y reversos en la variante Release real.

## Matriz de pruebas sin registros personales
Usar un respaldo ficticio aislado; nunca sobrescribir datos reales sin exportar antes.

| Caso | Preparación | Resultado esperado |
| --- | --- | --- |
| A. Recurrencia | Crear electricidad ₡40.000 desde noviembre, marcar pagada en noviembre | Diciembre muestra su compromiso recurrente como pendiente, no abonado |
| B. Deuda anterior | Dejar electricidad noviembre ₡35.000 sin pagar y seleccionarla en la revisión de diciembre | Aparece bloque «Pendientes de meses anteriores», con origen noviembre, separado de electricidad diciembre |
| C. No duplicación | Confirmar traslado, sin efectuar pago | Saldo ejecutado y movimientos reales no cambian |
| D. Pago de arrastre | Pagar ₡35.000 en diciembre de la deuda de noviembre | Solo se registra egreso real de diciembre; no marca pagado el compromiso recurrente diciembre |
| E. Abono de deuda | Trasladar ₡35.000, abonar ₡10.000 | Quedan ₡25.000 pendientes; el egreso real es ₡10.000 |
| F. Reverso | Reversar el abono anterior | Vuelven a quedar ₡35.000 pendientes; el saldo disponible refleja el reverso |
| G. Sin pendientes | Confirmar todas las obligaciones de noviembre | Bloque anterior con mensaje informativo; sin afirmar pagos si falta comprobación |
| H. Respaldo | Exportar, importar y volver a abrir diciembre | Se conservan origen, saldos pendientes, confirmaciones e historial |
| I. Cierre de mes | Cerrar noviembre y consultar diciembre | No se borran movimientos ni estado histórico |
| J. IA y presupuesto | Gastos presupuestados mayores que ingresos | Alerta informativa sin impedir operaciones ni cambiar montos |

## Distribución
- Cuenta personal creada tras 13/11/2023: Google pide **al menos 12 testers inscritos continuamente durante 14 días** en prueba cerrada para solicitar acceso a producción.
- Crear primero ficha interna/Play Console, completar política, formularios y AAB, ejecutar prueba cerrada real, reunir observaciones y solicitar acceso a producción.
- El estado de aprobación y las exigencias finales se verifican en Play Console, no se presumen a partir de la inscripción del desarrollador.

Referencias oficiales:
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://developer.android.com/studio/publish/app-signing
