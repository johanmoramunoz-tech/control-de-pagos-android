# PagoCheck IA — Integración desde ChatGPT (arquitectura objetivo)

**Decisión de producto (22 de noviembre de 2026):** priorizar la vinculación de una cuenta PagoCheck IA desde ChatGPT. La clave API propia (BYOK) seguirá siendo una vía avanzada/experimental para desarrolladores; no será el camino principal de incorporación.

## Experiencia
1. Usuario crea/inicia sesión en PagoCheck IA.
2. En ChatGPT busca/instala/conecta PagoCheck IA (sujeto a aprobación/publicación/disponibilidad en ChatGPT).
3. ChatGPT solicita autorización OAuth para operar sobre su cuenta PagoCheck IA.
4. En una conversación: «Registra ₡25.000 por combustible del 22/11». ChatGPT llama la herramienta MCP correspondiente.
5. Backend responde con una vista previa; usuario revisa y confirma antes de crear un movimiento.
6. El backend persiste el movimiento y Android lo muestra mediante sincronización, con auditoría.

## Arquitectura
ChatGPT (aplicación/plugin MCP)
    ↕ OAuth 2.1 / PKCE
Servidor MCP HTTPS (herramientas de lectura y escritura, autenticadas)
    ↕ API de negocio (validación, autorización, auditoría)
Firebase Auth + Firestore (datos por usuario/hogar)
    ↕ SDK Android (sincronización y caché local)
PagoCheck IA Android

**No existe conexión directa segura de ChatGPT al WebView local de cada dispositivo.** Es necesaria una API remota controlada por PagoCheck IA. La API para modelos de OpenAI y una suscripción ChatGPT son productos distintos. La autorización a un servidor MCP no equivale a regalar llamadas de API en Android.

## Herramientas MCP propuestas (versionado estricto)
- `get_account_summary(month)`: lectura agregada con permisos de propietario/miembro.
- `list_categories(kind)`: categorías existentes de la cuenta.
- `list_commitments(month)`: obligaciones previstas y ejecutadas.
- `prepare_transaction(kind, amount_crc, date, description, category_id, note)`: validación y propuesta, **sin escritura**.
- `confirm_transaction(proposal_id, idempotency_key)`: registra tras confirmación explícita y autorización por usuario.
- `reverse_transaction(transaction_id, reason)`: reverso auditado con confirmación explícita.
- `get_sync_status()`: estado de actualización del cliente Android.

## Seguridad requerida
- La identidad de la cuenta de ChatGPT no sustituye la cuenta de PagoCheck IA: vinculación OAuth verificada.
- El servidor comprueba identidad, audiencia, caducidad, scopes y recursos autorizados; acceso por hogar y roles.
- Cada escritura exige consentimiento y confirmación clara, identificador idempotente y registro de auditoría.
- No exponer claves API, tokens ni datos financieros en archivos fuente, logs o respuestas no autorizadas.
- Cuotas, protección contra abuso y controles de coste; los datos sensibles no deben utilizarse innecesariamente como contexto.
- No eliminar categorías/compromisos ni datos sin acción explícita y autorización.
- Cumplimiento de política de privacidad, eliminación de cuenta y normativas aplicables.

## Lo que **NO** está implementado (22/11/2026)
- Servidor MCP desplegado y accesible desde ChatGPT.
- OAuth de conexión PagoCheck IA↔ChatGPT.
- Firebase Auth, colecciones protegidas, backend de negocio y sincronización Android.
- Publicación de PagoCheck IA en el catálogo de aplicaciones de ChatGPT.
- Ejecución real de herramientas MCP desde ChatGPT.

## Hitos
1. Modelo Firestore y reglas multiusuario por hogar.
2. Firebase Auth Android y sincronización bidireccional con migración local verificable.
3. Backend de autorización y transacciones con idempotencia.
4. Servidor MCP con herramientas de solo lectura y pruebas, luego escritura con confirmación.
5. OAuth 2.1, prueba de vinculación desde ChatGPT y revisión de permisos.
6. Publicación y onboarding sencillo en ChatGPT, condicionado a revisión/disponibilidad.

## Criterios de aceptación
- Usuario conecta PagoCheck IA desde ChatGPT sin pegar claves de API.
- La sesión se puede revocar desde cualquiera de ambos sistemas.
- ChatGPT solo ve movimientos permitidos de esa cuenta.
- No es posible registrar dos veces accidentalmente la misma orden.
- El usuario confirma el gasto y lo observa sincronizado en el teléfono.
- Funciona el plan manual aun cuando ChatGPT esté desconectado.
