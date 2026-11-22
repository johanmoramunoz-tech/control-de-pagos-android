# Preparación de publicación en Google Play

## Ya configurado
- Nombre comercial y nombre del lanzador: PagoCheck IA.
- Identificador Android preservado: `cr.controles.pagos`.
- Recursos de ícono Android y pantalla de inicio preliminar.
- Vista numérica/gráfica y recordatorios del prototipo existente.

## Requisitos pendientes antes de Play Store
- [ ] Verificar la marca comercial y el nombre para evitar conflictos.
- [ ] Validar icono adaptativo sin recortes en diferentes lanzadores.
- [ ] Generar AAB de release y habilitar Play App Signing.
- [ ] Mantener clave privada de firma protegida y estable; probar instalación y actualización.
- [ ] Crear ficha comercial con capturas reales (sin interfaces ficticias).
- [ ] Publicar política de privacidad real con URL pública.
- [ ] Completar formulario de seguridad de datos y clasificación de contenido.
- [ ] Ejecutar pruebas en dispositivos Android compatibles, incluidos recordatorios y recuperación de respaldos.
- [ ] Integrar y probar Firebase/Drive para colaboración familiar antes de anunciarlo.
- [ ] Integrar y probar API/MCP, transcripción y análisis de facturas antes de anunciar IA.
- [ ] Configurar Google Play Billing y verificación del servidor antes de ofrecer planes pagados.
- [ ] Definir soporte, contacto de desarrollador y proceso de eliminación de datos si hay cuentas.

**Importante:** las versiones v5-v7 debug tenían firma no persistente. La versión v8 solo puede generar un APK estable si están correctamente configurados los secretos `CDP_KEYSTORE_B64` y `CDP_STORE_PASSWORD`. Mantener el mismo `applicationId` evita que Android la trate como otra aplicación; la firma debe coincidir para actualizar.
