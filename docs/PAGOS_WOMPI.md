# Módulo de Pagos — Wompi

Cobros directos vía Wompi (Web Checkout) para Polygraph Service. Backend en
`backend/src/main/java/com/polygraph/erp/modules/pagos/`, frontend en
`frontend/src/pages/pagos/` y `frontend/src/services/pagoWompiService.js`.

> **Hay dos integraciones de Wompi en este repo.** Este documento cubre el módulo
> de **pagos directos** (`PagoWompi`, `PagoController`, `/api/v1/pagos/...`,
> variables `WOMPI_*` con prefijo `wompi.` en `application.yml`). El flujo de
> **compra de créditos prepago / carrito** (`OrdenCompra`, `WompiPasarelaPago`,
> `/api/v1/client/ordenes/...`, `/api/v1/pagos/wompi/webhook`, variables bajo
> `app.wompi.*`) es un módulo distinto y anterior — no lo confundas con este.
> Si estás depurando un pago, primero confirma cuál de los dos flujos lo generó.

## 1. Flujo completo

```
Usuario (ADMIN_POLYGRAPH / GESTOR / ADMIN_CLIENTE)
   │
   ▼
Frontend: CheckoutWompi.jsx
   │  POST /api/v1/pagos/iniciar { clienteId, facturaOConceptoId, montoEnCentavos, descripcion }
   ▼
Backend: PagoController → PagoService.iniciarPago
   1. Verifica rol (ADMIN_CLIENTE solo puede pagar por su propio cliente)
   2. RECALCULA el monto desde la OrdenCompra referenciada — el monto que
      manda el frontend NUNCA se usa para cobrar, solo se compara para loguear
      si no coincide (posible manipulación)
   3. Genera referencia única "PS-<random>-<timestamp>"
   4. Persiste PagoWompi en estado PENDIENTE
   5. Firma de integridad = WompiSignatureService.generarFirmaIntegridad(...)
   6. Escribe un registro CREACION en auditoria_pagos
   │
   ▼
Responde { publicKey, referencia, montoEnCentavos, moneda, firmaIntegridad, redirectUrl }
   │  (nunca privateKey / integritySecret / eventsSecret)
   ▼
Frontend: inyecta el widget.js de Wompi (Web Checkout) con esos data-attributes
   │
   ▼
Usuario paga en el dominio de Wompi (checkout.wompi.co)
   │
   ├──────────────────────────────┐
   ▼                              ▼
Wompi redirige el navegador a   Wompi envía un webhook
redirectUrl?referencia=...&id=  POST /api/v1/webhooks/wompi (async, servidor a servidor)
   │  (SOLO INFORMATIVO —              │
   │   nunca decide el resultado)      ▼
   ▼                              WompiWebhookController → PagoWompiWebhookService.procesarEvento
Frontend: ResultadoPago.jsx        1. Valida el checksum (SIEMPRE primero) — si falla,
   Poll GET /api/v1/pagos/{referencia}   log WARN + auditoría WEBHOOK_CHECKSUM_INVALIDO, responde 401
   cada 3s, máx. 10 intentos          2. Idempotencia: si el transaccionWompiId ya está en un
   El veredicto SIEMPRE sale de          estado final, no reprocesa
   la respuesta del backend           3. Compara el monto del evento contra el de la BD — si no
                                          coincide, pago → ERROR, NUNCA se aprueba
                                       4. Mapea estado (ver tabla abajo), guarda, audita
                                       5. Si quedó APROBADO, publica PagoAprobadoEvent
                                          │
                                          ▼
                                   PagoAplicacionService (@Async, después del commit)
                                   levanta la restricción de acceso por deuda del cliente
                                   (limpia EstadoMora en ClientePospago)
```

### Red de seguridad: `ConciliacionPagosJob`

Un webhook puede perderse (Wompi caído, red, timeout). Cada 15 minutos
(`@Scheduled(cron = "0 */15 * * * *")`):

- Busca `PagoWompi` en `PENDIENTE` con más de 20 minutos de antigüedad.
- Si tiene `transaccionWompiId`, consulta `GET {baseUrl}/transactions/{id}` a
  Wompi directamente (con la llave privada, nunca la pública) y aplica el
  resultado con el **mismo** método que usa el webhook
  (`PagoWompiWebhookService.aplicarEstadoTransaccion`) — no hay dos copias de
  la regla de negocio.
- Si no tiene `transaccionWompiId` y lleva más de 24 horas en `PENDIENTE`, lo
  marca `ERROR` (probablemente el usuario nunca completó el checkout).
- Cada pago se procesa en su propia transacción: si uno falla, no afecta a los demás.

## 2. Estados

| Estado interno | Origen                                              |
| --------------- | --------------------------------------------------- |
| `PENDIENTE`      | Creado, esperando confirmación                      |
| `APROBADO`       | Wompi `APPROVED`                                     |
| `RECHAZADO`      | Wompi `DECLINED`                                     |
| `ANULADO`        | Wompi `VOIDED`                                       |
| `ERROR`          | Wompi `ERROR`, **o** monto inconsistente, **o** más de 24h sin transacción |

## 3. Variables de entorno

Todas se leen bajo el prefijo `wompi.` en `application.yml`
(`backend/src/main/resources/application.yml`), mapeadas a
`WompiProperties` (`modules/pagos/config/WompiProperties.java`). Ejemplo en
`.env.example` (raíz del repo) — **nunca** pongas valores reales ahí.

| Variable | Uso | Dónde se usa |
| --- | --- | --- |
| `WOMPI_PUBLIC_KEY` | Llave pública, se envía al frontend/Wompi Web Checkout. No es secreta. | `PagoService` (respuesta de `/iniciar`) |
| `WOMPI_PRIVATE_KEY` | Llave privada. **Nunca sale del backend, nunca se loguea.** | `ConciliacionPagosJob` (header `Authorization: Bearer`) |
| `WOMPI_INTEGRITY_SECRET` | Firma el checkout (`referencia + montoEnCentavos + moneda + secreto`, SHA-256). **Nunca sale del backend.** | `WompiSignatureService.generarFirmaIntegridad` |
| `WOMPI_EVENTS_SECRET` | Valida el checksum del webhook. **Nunca sale del backend.** | `WompiSignatureService.validarChecksumEvento` |
| `WOMPI_BASE_URL` | API de Wompi (sandbox o producción) para conciliación. | `WompiRestClientConfig` (`RestClient` con 5s connect / 10s read) |
| `WOMPI_REDIRECT_URL` | URL fija a la que Wompi redirige tras el pago. El frontend le agrega `?referencia=...` antes de mandarla al widget. | `PagoService` (respuesta de `/iniciar`) |

Además, el webhook necesita que la ruta esté sin JWT en `SecurityConfig`:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/wompi").permitAll()
```

(Todo lo demás sigue detrás de `anyRequest().authenticated()`.)

## 4. Auditoría

Tabla `auditoria_pagos` (`AuditoriaPago` / `AuditoriaPagoService`), de solo
inserción, con un registro por evento relevante:

- **Quién generó el cobro** y cuándo (`CREACION`, con `id_generado_por`).
- Cada cambio de estado, con estado anterior/nuevo y el monto.
- La IP de origen de cada webhook y si su checksum fue válido
  (`WEBHOOK_CHECKSUM_INVALIDO`, `WEBHOOK_ESTADO_ACTUALIZADO`).
- Los cambios que dispara la conciliación automática (`CONCILIACION_ESTADO_ACTUALIZADO`,
  sin IP ni checksum — no aplican a una consulta autenticada con llave privada).

Consulta: `GET /api/v1/pagos/auditoria?referencia=...&page=&size=` — **solo
`ADMIN_POLYGRAPH`** (`AuditoriaPagoController`, `@PreAuthorize("hasRole('ADMIN_POLYGRAPH')")`).

## 5. Reglas de seguridad (no negociables)

- El monto que llega del frontend a `/pagos/iniciar` **nunca** se usa para
  cobrar — siempre se recalcula server-side desde la orden de compra.
- El checksum del webhook se valida **antes** de cualquier otra cosa; si falla,
  no se toca la BD.
- El resultado que ve el usuario en `ResultadoPago.jsx` sale **siempre** de
  `GET /api/v1/pagos/{referencia}` (nuestra BD) — el query param `id` que
  agrega Wompi al redirect es solo informativo, nunca decide nada.
- `privateKey`, `integritySecret` y `eventsSecret` no salen nunca del backend:
  no aparecen en respuestas HTTP, no se logean, no llegan al frontend.
- Los correos que se logean se enmascaran con `EnmascaradorUtil.enmascararEmail`
  (`j***@empresa.com`) — nunca se imprime un correo completo en un log.
- No se guarda número de tarjeta, CVV ni ningún dato sensible del medio de
  pago — solo el nombre del método (`CARD`, `NEQUI`, `PSE`, `BANCOLOMBIA_TRANSFER`).

## 6. Probar en sandbox con ngrok

Wompi necesita alcanzar tu webhook desde internet, así que en desarrollo local
se expone el backend con [ngrok](https://ngrok.com/).

1. **Credenciales de sandbox**: crea/entra a tu comercio de prueba en el
   [dashboard de Wompi](https://comercios.wompi.co/) y copia las llaves de
   **sandbox** (`pub_test_...`, `prv_test_...`) y los secretos de eventos e
   integridad de sandbox. Confirma ahí mismo la URL base de la API de sandbox
   vigente (suele ser un dominio `sandbox.wompi.co`).

2. **Variables de entorno locales** (`.env` del backend, nunca versionado):
   ```
   WOMPI_PUBLIC_KEY=pub_test_xxxxxxxx
   WOMPI_PRIVATE_KEY=prv_test_xxxxxxxx
   WOMPI_INTEGRITY_SECRET=test_integrity_xxxxxxxx
   WOMPI_EVENTS_SECRET=test_events_xxxxxxxx
   WOMPI_BASE_URL=https://sandbox.wompi.co/v1
   WOMPI_REDIRECT_URL=http://localhost:5173/cliente/pagos/resultado
   ```

3. **Levanta el backend** normalmente (`./mvnw spring-boot:run` o tu forma
   habitual) — por defecto en `localhost:8080`.

4. **Expón el backend con ngrok**:
   ```
   ngrok http 8080
   ```
   Copia la URL pública que te da (`https://xxxx.ngrok-free.app`).

5. **Configura el webhook en el dashboard de Wompi** (sandbox → eventos) para
   que apunte a:
   ```
   https://xxxx.ngrok-free.app/api/v1/webhooks/wompi
   ```
   Verifica que el "secreto de eventos" que te muestra el dashboard para esa
   URL sea el mismo que pusiste en `WOMPI_EVENTS_SECRET`.

6. **Levanta el frontend** (`npm run dev` en `frontend/`) y haz un pago de
   prueba desde `/cliente/pagos/checkout` (o `/admin`, `/gestor`) con una
   `facturaOConceptoId` de una `OrdenCompra` real en tu BD local.

7. **Usa las tarjetas de prueba de Wompi** (documentadas en su sandbox) para
   simular `APPROVED`, `DECLINED`, etc.

8. **Verifica**:
   - En la terminal del backend, el log `Webhook de Wompi con checksum inválido`
     **no** debería aparecer — si aparece, el `WOMPI_EVENTS_SECRET` no coincide
     con el configurado en el dashboard para esa URL de ngrok.
   - `GET /api/v1/pagos/{referencia}` (o la pantalla de `ResultadoPago`) debe
     reflejar el estado final en un par de segundos.
   - `GET /api/v1/pagos/auditoria?referencia=...` (como `ADMIN_POLYGRAPH`) debe
     mostrar `CREACION` y luego `WEBHOOK_ESTADO_ACTUALIZADO` con `checksumValido=true`.
   - La interfaz de ngrok (`http://127.0.0.1:4040`) te deja inspeccionar el
     body exacto que Wompi mandó, útil para depurar sin adivinar.

> Nota: cada vez que reinicias ngrok sin plan pago la URL cambia — hay que
> volver a actualizarla en el dashboard de Wompi.
