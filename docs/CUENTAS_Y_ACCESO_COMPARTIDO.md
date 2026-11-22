# PagoCheck IA — Cuentas, colaboración y acceso autorizado (pendiente)

## Hallazgo en auditoría v13 / 2026-11-22
El APK almacena compromisos, movimientos, categorías y saldos **únicamente en localStorage del WebView**. No existe registro por correo, Firebase Authentication, base de datos compartida ni controles remotos de acceso.
**No mostrar como activas** pantallas de «Invitar usuario» que solo agreguen correos localmente; eso no concede permisos ni garantiza confidencialidad.

## Diseño funcional acordado
- Cuenta principal registrada y verificada por correo electrónico; la identidad del propietario se valida en servidor.
- Creación de un **espacio financiero**; identificador único por hogar/proyecto, sin compartir datos entre espacios.
- Invitaciones por correo mediante enlace/código temporal de un solo uso y aceptación del destinatario.
- Roles: **Propietario** (gestiona miembros/permisos), **Editor** (añade pagos y compromisos, con auditoría) y **Lector** (consulta/exporta sin modificación).
- Revocación inmediata de acceso, historial con actor y fecha, gestión de sesiones.
- Al iniciar sesión por primera vez, migración voluntaria del respaldo local a la cuenta. No borrar datos locales hasta verificar integridad y respaldos.
- La versión gratuita debe seguir funcionando offline y sin cuenta obligatoria.
- Durante desconexión la información consultada puede estar desactualizada; resolver conflictos y duplicados al sincronizar.

## Infraestructura necesaria
- Firebase Auth u otro proveedor seguro de autenticación.
- Firestore o base de datos con reglas de seguridad por espacio financiero/rol.
- Invitaciones verificadas (Cloud Functions o backend) y revocación.
- Sincronización Android y migración de la base local.
- Pruebas de aislamiento: dos usuarios sin acceso no deben leer ni modificar datos ajenos.
- Cumplimiento de privacidad, eliminación de cuenta, recuperación de contraseñas y control de acceso.

## Estado
No implementado en v14. Se muestra aviso explícito en Ajustes para evitar la falsa apariencia de autorización.
