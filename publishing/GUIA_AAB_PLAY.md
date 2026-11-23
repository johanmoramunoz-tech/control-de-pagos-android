# PagoCheck IA — AAB firmado para prueba interna de Google Play

Estado: preparación técnica realizada. **Todavía no existe un AAB Release firmado comprobado**, hasta que el desarrollador configure su clave de carga en GitHub y ejecute el flujo.

## Configuración incorporada
- Paquete: `cr.controles.pagos`
- Versión de carga: `versionCode 25`, `versionName 25.0-prueba-interna`
- `targetSdk 36`, `compileSdk 36`
- Tipo `release`: no depurable; el modo de simulación Premium solo funciona en debug.
- Cobros Premium: desactivados; no presentar esta versión como servicio comercial.
- Flujo manual: **Actions → Preparar AAB firmado para Google Play → Run workflow**.
- Artefacto si todo finaliza correctamente: `PagoCheck-IA-v25-AAB-Play-Interna` con `app-release.aab`.
- Se verifica integridad ZIP y firma JAR del archivo resultante.

## 1. Crear clave de CARGA en Linux Mint
En la computadora local, desde Terminal (no en el chat), generar una clave nueva e independiente de las APK debug:

```bash
mkdir -p ~/Documentos/PagoCheck-IA-firma
chmod 700 ~/Documentos/PagoCheck-IA-firma
keytool -genkeypair -v \
  -keystore ~/Documentos/PagoCheck-IA-firma/pagocheck-play-upload.jks \
  -alias pagocheckplay -keyalg RSA -keysize 3072 \
  -validity 10000 -storetype PKCS12
```

El programa solicitará una contraseña del almacén. En PKCS12, usar la misma contraseña para la clave cuando se solicite. Guardarla en un gestor de contraseñas o lugar protegido y conservar una copia privada del archivo JKS, separada del ordenador. No compartir el archivo ni la contraseña.

## 2. Registrar cuatro GitHub Actions secrets
En GitHub → repositorio `johanmoramunoz-tech/control-de-pagos-android` → Settings → Secrets and variables → Actions → New repository secret.

| Secreto | Contenido |
| --- | --- |
| `PC_UPLOAD_KEYSTORE_B64` | Base64 de `pagocheck-play-upload.jks` en una sola línea |
| `PC_UPLOAD_STORE_PASSWORD` | Contraseña del almacén |
| `PC_UPLOAD_KEY_ALIAS` | `pagocheckplay` |
| `PC_UPLOAD_KEY_PASSWORD` | Contraseña de la clave, normalmente la misma que el almacén PKCS12 |

Para obtener Base64 en Terminal local:
```bash
base64 -w 0 ~/Documentos/PagoCheck-IA-firma/pagocheck-play-upload.jks
```
Copiar la salida completa directamente al campo secreto de GitHub, sin incluirla en chats, incidencias ni commits. Luego limpiar el historial visible de la Terminal si se necesita. Nunca guardar el Base64 dentro del repositorio.

## 3. Ejecutar el flujo
GitHub → Actions → **Preparar AAB firmado para Google Play** → **Run workflow** en `main`.
Cuando termine verde: abrir la ejecución → Artifacts → descargar `PagoCheck-IA-v25-AAB-Play-Interna` → descomprimir → conservar `app-release.aab`. Verificar el nombre del archivo antes de cargarlo en la consola.

## 4. Prueba interna
En Play Console → PagoCheck IA → Prueba interna → Crear nueva versión:
- Inscribirse en Play App Signing y permitir que Google administre la firma de distribución.
- Subir `app-release.aab`, no `app-debug.apk`.
- Revisar avisos, firma de carga y `versionCode 25`.
- Mantener una lista de testers autorizados y lanzar al canal interno sólo después de que Play Console complete sus verificaciones.

## 5. Restricciones
- **No reutilizar automáticamente** la llave debug `CDP_KEYSTORE_B64` para la publicación.
- Al instalar desde Google Play, la firma de distribución puede diferir de la APK debug; una actualización directa podría fallar. Exportar **JSON** antes de desinstalar la APK de prueba; reinstalar desde Play e importar el respaldo con cautela.
- Registrar en Firebase las huellas del **certificado de firma de app de Play**, si lo requieren servicios de autenticación/Google.
- Antes de distribuir externamente, validar política de privacidad, eliminación completa de cuenta, declaraciones y uso de datos, permisos y pantallas de evaluación.
- No activar el cobro ni poner `LIVE_PURCHASES_ENABLED=true` sin servidor de licencias completo.

## Referencias
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/build/building-cmdline
- https://support.google.com/googleplay/android-developer/answer/9845334
