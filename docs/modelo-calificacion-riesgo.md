# Modelo de calificación de riesgo del evaluado

**Versión del modelo:** 1.5 (adaptada a la plataforma) · **Fecha:** 24-sep-2026
**Origen:** `prompts-cursor-motor-riesgo.md` (especificación de negocio), ajustada con las
decisiones tomadas con Carlos el 24-sep-2026 para encajar en el catálogo real de la plataforma.

> Toda lógica de calificación de riesgo debe cumplir este documento. Si algo no está definido
> aquí, se pregunta antes de asumirlo — no se decide en el código.

---

## 1. Conceptos (cómo se mapean en la plataforma)

| Concepto del modelo | En la plataforma | Nota |
|---|---|---|
| Estudio | `Proceso` (tabla `procesos`) — AVANZADO, BASICO, BASICO VIRTUAL, QUICK, OEA, "Antecedentes", etc. | Todos los procesos se califican con el MISMO motor. |
| Servicio / subproceso calificable | `TipoProgreso` (tabla `tipos_progreso`) | Se pesa cada subproceso real — **no** se agrupan en 5 componentes. |
| Composición del estudio | `ProcesoTipoProgreso` (tabla `procesos_tipos_progreso`) | Aquí viven peso y obligatoriedad. |
| Estudio individual | Un `Proceso` con un solo subproceso (ya existen: "Antecedentes", "Análisis Financiero"…) | No se crean automáticamente. Su único subproceso pesa 100 % y es obligatorio. |
| Caso | `Servicio` (un proceso para un candidato) | |
| Subproceso dentro del caso | `ServicioSubproceso` | |
| Hallazgo | Una **opción de respuesta** de un campo de la plantilla con puntos (ver §3) | No hay pantalla de hallazgos aparte. |
| Criticidad del cargo | `ESTANDAR`, `SENSIBLE`, `CRITICO` | Por defecto en el proceso; el gestor la ajusta por caso (ver §2.3). |
| Resultado | Puntaje 0–100 (más puntos = más riesgo) y nivel `BAJO` / `MEDIO` / `ALTO` | |

**Decisión (24-sep-2026):** se descartó agrupar en 5 componentes (ANTECEDENTES, VALIDACIONES,
FINANCIERO, VISITA, POLIGRAFIA). El peso se define por cada subproceso real del proceso, en el
catálogo, al agregarlo al proceso. Esto aplica a **todos** los procesos.

## 2. Configuración (todo en base de datos, nada quemado en código)

### 2.1 Pesos por proceso
- Al agregar un subproceso a un proceso en el catálogo se define su **peso (%)** y si es **obligatorio**.
- La suma de pesos de un proceso **no puede pasar de 100 %**.
- Se puede guardar con menos de 100 mientras se configura, pero **el proceso solo califica riesgo
  cuando suma exactamente 100,00 %**. Mientras tanto se muestra "Pesos incompletos: X %" y el motor
  no calcula (el caso queda para revisión manual con ese motivo).
- Pesos de referencia del negocio para AVANZADO (a repartir entre sus subprocesos reales):
  Antecedentes 25 · Poligrafía 25 · Validaciones 20 · Financiero 15 · Visita 15.
  El reparto del 20 % de validaciones entre Validación Académica / Personal / Laboral lo define
  el negocio desde el catálogo. **No se inventan pesos en la migración.**

- **Peso 0 % está permitido y significa "no califica"**: el subproceso se hace normal, pero el
  motor lo ignora (ej. Pruebas Psicotécnicas, Verifeye). Un subproceso sin peso configurado (NULL)
  impide que el proceso califique.
- Solo cuentan los subprocesos **habilitados** en el proceso y **activos** en el catálogo.
- Cada cambio de pesos, obligatorios, cobertura o criticidad sube `procesos.version_calificacion`.

### 2.2 Cobertura mínima y obligatorios
- **"Obligatorio" es la misma marca que ya existe** en `procesos_tipos_progreso.obligatorio` (la que
  el cliente ve como Obligatorio/Opcional al solicitar) — decisión 24-sep-2026.
- La migración solo fija la cobertura de AVANZADO en 60 %; los obligatorios los ajusta el admin
  desde el catálogo (no se cambian en la migración porque el cliente los ve).
- Cada proceso define su **cobertura mínima (%)**. Referencia: AVANZADO 60 %; procesos de un solo
  subproceso 100 %.
- Obligatorios de referencia en AVANZADO: Antecedentes y Poligrafía.

### 2.3 Criticidad
- Cada proceso tiene una **criticidad por defecto**.
- En cada caso (`Servicio`) el gestor puede **cambiarla** según el cargo del evaluado. Queda
  registrado quién y cuándo.

### 2.4 Umbrales por criticidad (editables por el admin en "Calificación de riesgo → Parámetros")
| Criticidad | Límite BAJO | Límite MEDIO | Mínimo MEDIO |
|---|---|---|---|
| ESTANDAR | 30 | 60 | 31 |
| SENSIBLE | 25 | 50 | 26 |
| CRITICO | 20 | 40 | 21 |

Nivel: `BAJO` si puntaje ≤ límite BAJO; `MEDIO` si ≤ límite MEDIO; `ALTO` si es mayor.

### 2.5 Parámetros de piso (editables por el admin)
`umbral_servicio_alto = 70` · `umbral_servicio_moderado = 50` · `cantidad_servicios_moderados = 2`.

### 2.6 Versionado
Toda la configuración se versiona. Cada resultado guarda una copia de la configuración con la que
se calculó. Cambiar pesos, puntos, umbrales o parámetros **no recalcula resultados bloqueados**.

## 3. Hallazgos = puntos por opción en la plantilla

- En el constructor de plantillas (admin/catálogo → Plantilla), los campos tipo **Lista** y
  **Sí/No** pueden configurarse como **calificables**: a cada opción de respuesta se le asignan
  **puntos (≥ 0)** y una marca **crítica** (sí/no).
- Quien ejecuta el subproceso (analista, poligrafista o visitador) solo responde; **no ve ni
  digita puntos**.
- En secciones tipo **tabla**, cada fila suma sus puntos (ej. 3 referencias no verificables
  suman 3 veces).
- Ejemplo:
  - "¿Aparece en listas restrictivas?" [Sí/No] → Sí: 0 pts, crítico · No: 0 pts.
  - "Resultado verificación laboral" [Lista] → Confirmada 0 · Inconsistencias leves 15 ·
    No se pudo verificar 10 · Certificado falso 0 (crítico).
- **Respuesta crítica → justificación obligatoria** (decisión 24-sep-2026): al elegirla, quien
  diligencia ve "Esta respuesta es determinante: explica en qué te basas" (sin puntos) y no puede
  marcar el subproceso como completado hasta explicarla (`AsignacionService.completar` →
  `FormularioService.respuestasCriticasSinJustificar`). Si cambia a una respuesta normal, la
  explicación se descarta.
- Solo califican campos **Lista** y **Sí/No** llenados a mano (no datos automáticos del
  candidato/cliente). En Sí/No las opciones son `true` / `false`.
- Si el admin renombra o quita una opción, sus puntos se descartan al guardar (el constructor avisa).
- Críticos iniciales que el negocio debe configurar en las plantillas: coincidencia en listas
  restrictivas (OFAC, ONU); orden de captura vigente; suplantación de identidad; documentos
  académicos o laborales falsos; indicios de actividades ilícitas en la visita; admisión de
  delitos o vínculos con grupos ilegales en poligrafía.

## 4. Estado de cada subproceso dentro del caso (para el motor)

| Estado para el motor | Estado en la plataforma |
|---|---|
| `REALIZADO` | `ServicioSubproceso` COMPLETADO o PUBLICADO |
| `NO_REALIZADO` | Marcado explícitamente por quien coordina (gestor/programador) al resolver una novedad (ej. "El candidato se negó"), con motivo. **No penaliza.** |
| `PENDIENTE` | Cualquier otro estado (PENDIENTE, ASIGNADO) |

**Decisiones 25-sep-2026 (reemplazan la nota anterior):** cuenta como REALIZADO **desde que se
completa** (COMPLETADO), no solo al aprobarse; **todo CANCELADO cuenta como NO_REALIZADO** — el
"No realizado" que marca quien coordina al resolver una novedad es un CANCELADO con
`servicio_subprocesos.no_realizado = true` y su motivo. Subprocesos del proceso que el caso no tiene
entran como NO_REALIZADO; subprocesos del caso que ya no están en el proceso, con peso 0.

## 5. Motor (sin cambios frente a la especificación de negocio)

### Paso 1 – Calificación por subproceso
- `puntaje_bruto_servicio` = suma de puntos de las respuestas calificables, con tope en 100.
- Con algún hallazgo crítico: `puntaje_final_servicio = 100`; si no, `= puntaje_bruto_servicio`.
- `nivel_servicio` = umbrales de la criticidad aplicados a `puntaje_final_servicio`.

### Paso 2 – Hallazgos críticos (knock-out)
Si cualquier subproceso REALIZADO tiene un hallazgo crítico → `tipo_ajuste = CRITICO`.

### Paso 3 – Promedio ponderado
- Solo participan los REALIZADOS.
- Los NO_REALIZADOS reparten su peso proporcionalmente: `peso_efectivo = peso / Σ pesos realizados × 100`.
- `puntaje_ponderado = Σ (puntaje_bruto_servicio × peso_efectivo / 100)` — usa el puntaje
  **bruto** (sin el 100 por crítico).
- `cobertura = Σ pesos de los realizados` (relativa a la configuración del propio proceso).

### Paso 4 – Pisos, puntaje final y nivel
1. `nivel_por_puntaje` = umbrales aplicados a `puntaje_ponderado`.
2. `tipo_ajuste = CRITICO` → `puntaje_final = 100`, `nivel_final = ALTO`.
3. Si no: Regla A = algún subproceso con `puntaje_bruto ≥ 70`; Regla B = 2 o más con
   `puntaje_bruto > 50`. Si A o B y `nivel_por_puntaje = BAJO` →
   `puntaje_final = max(puntaje_ponderado, mínimo_MEDIO)`, `nivel_final = MEDIO`,
   `tipo_ajuste = PISO`. El piso nunca sube a ALTO; si ya es MEDIO o ALTO no cambia nada.
4. Sin ajuste → `puntaje_final = puntaje_ponderado`, `nivel_final = nivel_por_puntaje`, `NINGUNO`.
5. `requiere_revision_manual = true` si: nivel final MEDIO o ALTO; o falta (NO_REALIZADO) un
   obligatorio; o cobertura < cobertura mínima; o los pesos del proceso no suman 100.

Si algún subproceso está PENDIENTE, **no hay resultado final del estudio** (estado "En proceso");
sí se calcula el resultado de cada subproceso ya realizado.

**Estados sin nivel final** (decisión 25-sep-2026): `EN_PROCESO` (algo pendiente), `SIN_INFORMACION`
(ningún subproceso con peso quedó realizado — revisión manual), `NO_CALIFICA` (algún subproceso sin
peso o pesos que no suman 100 — revisión manual con el motivo).

**Peso 0** (decisión 25-sep-2026): el subproceso no participa en el promedio ni en las reglas de
piso, pero **sus respuestas críticas sí llevan el estudio a ALTO** (lo crítico siempre cuenta).

**Cálculo del ponderado:** `Σ(bruto × peso) / Σ pesos realizados` — igual a
`Σ bruto × peso_efectivo / 100`, sin arrastrar el redondeo del peso efectivo.

### Redondeo
`BigDecimal` escala 4. `puntaje_ponderado` y `puntaje_final` se redondean a 1 decimal (HALF_UP)
**antes** de asignar el nivel.

## 6. Nivel final

**Automático, con opción de cambiarlo** (decisión 25-sep-2026, reemplaza a "obligatoria para
publicar"): el nivel final es, por defecto, el que recomienda el motor — nadie tiene que
confirmarlo. El **gestor del cliente** puede cambiarlo (justificación obligatoria) y puede
**volver al automático** en cualquier momento antes de publicar. La recomendación del motor nunca
se borra; `nivel_decidido` solo se guarda cuando difiere de ella.

Publicar (`CalificacionRiesgoService.prepararPublicacion`, desde `SolicitudService.cambiarEstado`)
solo exige que el cálculo esté `CALCULADO` si el proceso califica riesgo; al publicar, el cálculo
vigente queda bloqueado. El Word muestra el nivel **que vale** (el cambiado, o el automático).

## 7. Qué se muestra y qué se guarda

### Marcadores Word (sintaxis real de la plataforma)
| Marcador | Contenido |
|---|---|
| `${riesgo['nivel']}` | Nivel que vale (automático o cambiado por el gestor); "En proceso" si falta algo, "Sin calificar" si no se puede calificar |
| `${riesgo['puntaje']}` | Puntaje final con 1 decimal y coma (ej. `21,2`); vacío si no hay cálculo |
| `${calificacion['nivel']}` | Nivel del subproceso de ese documento |
| `${calificacion['puntaje']}` | `puntaje_final_servicio` del subproceso |

Disponibles en todas las plantillas; los calcula la plataforma y el ejecutor no los edita.
**Nunca** se exponen en el Word: ponderado sin ajustar, tipo de ajuste, motivos, pesos.

### Encabezado del formulario (decisión 25-sep-2026)
Quien diligencia (analista/poligrafista/visitador) ve arriba el **nivel y puntaje de su
subproceso** — "Parcial" mientras lo llena, calculado con lo guardado — y si tiene una respuesta
crítica. En las tablas, cada fila muestra cuántos puntos suma ("+5 pts" o "Crítica") y abajo el
total de la tabla; para eso el formulario trae los puntos por opción (`/estructura` no). El gestor del cliente y el admin ven además el
**nivel final del estudio** (automático o cambiado). Endpoint
`GET /api/v1/riesgo/subprocesos/{id}/resumen` (`CalificacionRiesgoService.resumenSubproceso`).

### Registro interno de auditoría (no se imprime)
Puntaje bruto por subproceso, pesos efectivos, puntaje ponderado, nivel por puntaje, tipo de
ajuste (NINGUNO | PISO | CRITICO), motivos de ajuste (subproceso, regla o hallazgo, valor),
cobertura, motivos de revisión manual, copia de la configuración usada, usuario y fecha.
Historial de todos los cálculos; ninguno se sobrescribe.

### Bloqueo
Mientras el caso está abierto, se recalcula al guardar el formulario de un subproceso o al
cambiar su estado. Cuando el servicio se publica, el resultado queda **bloqueado**.
Si la solicitud se devuelve de PUBLICADO a otro estado, el resultado se **desbloquea y se recalcula**
(decisión 25-sep-2026); si el cálculo cambió, el nivel vuelve a ser el automático.

## 8. Plan de implementación (fases)

| Fase | Contenido |
|---|---|
| A ✅ | Catálogo: peso y obligatorio por subproceso en cada proceso, cobertura mínima y criticidad por defecto; umbrales y parámetros de piso. **Hecha 24-sep-2026** (V190, `CalificacionProcesoService`, `ParametrosRiesgoService`). |
| B ✅ | Constructor de plantillas: puntos y marca crítica por opción de campos Lista y Sí/No; justificación obligatoria de respuestas críticas. **Hecha 24-sep-2026** (V191, `formulario_columna_puntajes`, `FormularioColumna.calificaRiesgo`, `FormularioDato.justificacion`). |
| C ✅ | Motor de dominio puro + pruebas de aceptación (las 11 de la especificación, mismos números). **Hecha 25-sep-2026** (`modules/riesgo/motor`: `MotorCalificacionRiesgo` y un componente por paso; 34 pruebas). |
| D ✅ | "No realizado", cálculo en vivo, auditoría/historial, criticidad por caso, nivel final automático con opción de cambiarlo y bloqueo al publicar. **Hecha 25-sep-2026** (V192, `CalificacionRiesgoService`, pestaña "Riesgo" del detalle de la solicitud). |
| E ✅ | Marcadores Word `${riesgo[...]}` y `${calificacion[...]}` (vista previa, análisis de la plantilla y manual PDF los reconocen). **Hecha 25-sep-2026** (`CalificacionRiesgoService.marcadoresWord`, `ContextoPlantillaWord`). |

## 9. Pendientes del negocio
- Reparto de pesos de cada proceso entre sus subprocesos reales (ej. el 20 % de validaciones en
  AVANZADO) y pesos de BASICO, BASICO VIRTUAL, QUICK, OEA y combinados.
- Puntos de cada opción de las plantillas (vienen de los formatos que se están ajustando).
- Cómo cuentan en la calificación: Pruebas Psicotécnicas, Verifeye, Revisión Patrimonial,
  Visita domiciliaria financiera.
- Confirmar que la diferencia entre Regla A (≥ 70) y Regla B (> 50) es intencional.
