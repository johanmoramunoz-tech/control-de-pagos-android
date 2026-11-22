# Control de Pagos Android v8

Aplicación Android de prueba para presupuesto familiar local.

## Versión 8
- Resumen con vista **Números** o **Gráficos**; selección persistente.
- Barras comparativas de ingresos y gastos previstos vs. ejecutados.
- Distribución de gastos por categoría basada en movimientos del mes.
- Recordatorios de compromisos locales (Android) heredados de v7.
- Firma persistente para actualizaciones posteriores mediante secretos de GitHub Actions.

## Firma para actualizar sin desinstalar
El workflow requiere dos secretos del repositorio:
- `CDP_KEYSTORE_B64`: contenido de la clave privada de prueba, codificada en base64.
- `CDP_STORE_PASSWORD`: contraseña de la clave.
El alias es `controlpagos`. **Nunca guardar la clave o los secretos en el repositorio.**
En `Settings → Secrets and variables → Actions`, crear los dos secretos; después ejecutar el workflow manualmente en Actions.
El sistema no publicará un APK si faltan los secretos de firma. Guardar una copia segura de la clave privada.
Debido a que la versión v7 fue firmada con una clave anterior no persistente, será necesaria una reinstalación una sola vez para migrar a v8. Exportar un respaldo JSON desde v7 antes de desinstalar.
El paquete de firma es solo para pruebas; la publicación comercial deberá seguir una estrategia de firma y Google Play App Signing independiente.

## Estado de inteligencia artificial
No hay conexión operativa con ChatGPT, Gemini, un servicio MCP propio ni servicios de análisis de facturas. Las funciones de dictado de Android no equivalen a IA externa. Firebase, sincronización familiar, Google Drive y Play Billing siguen pendientes.

## Seguridad
- El repositorio debería configurarse como privado antes de añadir servicios o credenciales.
- Los respaldos de prueba JSON no están cifrados.
- No utilizar información financiera real sensible hasta completar cifrado, autenticación y pruebas.
