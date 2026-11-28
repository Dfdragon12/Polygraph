# Prompts para Cursor – Formato de Verificación académica con calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.2, 07/10/2026 (agrega el estado "Terminó materias, pendiente de grado")
**Requisito previo:** el servicio de Antecedentes legales ya implementado con sus prompts (`Prompts_Cursor_Antecedentes.md`). Este servicio sigue el mismo patrón y debe reutilizar lo que ya se construyó: catálogo configurable, servicio de calificación, campos calculados, avisos al guardar y entrega al motor del estudio.

**Archivos que acompañan estos prompts (ponerlos en el repositorio, por ejemplo en `docs/academica/`):**

| Archivo | Para qué sirve |
|---|---|
| `1_Formato_Verificacion_Academica.docx` | Plantilla Word con todos los marcadores |
| `2_Diccionario_marcadores_Academica.xlsx` | Campos, tablas, listas, catálogo, cálculos, umbrales y casos de prueba |
| `motor_aca.py` | Motor de referencia en Python (especificación ejecutable; el Java debe dar los mismos resultados) |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen.

---

## Prompt 0 – Revisar lo que ya existe de Antecedentes (no escribir nada todavía)

```
Vamos a construir el servicio "Verificación académica" del Estudio Avanzado. Sigue el mismo patrón que ya implementamos para "Antecedentes legales". No modifiques ningún archivo en este paso.

1. Muéstrame las clases y tablas que se crearon para Antecedentes: catálogo de códigos, servicio de calificación, cálculo de campos para el Word, avisos al guardar y entrega del resultado al motor del estudio.
2. Dime qué partes son genéricas y se pueden reutilizar tal cual para otro servicio (por ejemplo: cálculo del nivel con los umbrales, formato del puntaje, semáforo, entrega al motor) y qué partes son propias de Antecedentes.
3. Revisa cómo está configurado hoy el servicio "Validaciones" en el motor del estudio: ¿es un solo servicio con 20 % o ya existen servicios separados para académica, laboral y personal?
4. Revisa si la plataforma tiene un calendario de festivos de Colombia o una utilidad para sumar días hábiles.

Lee docs/academica/2_Diccionario_marcadores_Academica.xlsx (todas las hojas) y docs/academica/motor_aca.py.

Al final dame: la propuesta de clases nuevas y reutilizadas, y las dudas que encuentres entre el diccionario y lo que ya existe.
```

> Revisar la respuesta antes de seguir. Lo ideal es que lo genérico de Antecedentes se extraiga a una clase base o utilidad compartida, para que los servicios siguientes (laboral, personal, financiero, visita, poligrafía) no repitan código.

---

## Prompt 1 – Catálogo académico como configuración

```
Crea el catálogo del servicio Verificación académica usando el mismo mecanismo de configuración del catálogo de Antecedentes (no lo dejes quemado en el código).

Cada código tiene: codigo, descripcion, puntos (entero) o critico (booleano), activo. En este servicio no hay códigos "por proceso" ni "solo conducción".

| Código | Descripción | Puntos |
|---|---|---|
| ACA-01 | Documento falso o adulterado | Crítico |
| ACA-02 | La institución no confirma el título declarado | 70 |
| ACA-03 | Datos que no coinciden con lo declarado (diferencia de fondo) | 45 |
| ACA-04 | No verificable: el evaluado no aportó lo solicitado dentro del plazo | 45 |
| ACA-05 | Institución no reconocida o título sin validez en Colombia | 30 |

Las alertas ALR-ACA-01 (indicios de alteración sin confirmar) y ALR-ACA-02 (verificación pendiente por causa ajena al evaluado) son opciones de la lista de calificación, pero NO suman puntos y NO generan hallazgo.

Los umbrales son los mismos del modelo v1.8 que ya usa Antecedentes (Estándar 30/60, Sensible 25/50, Crítico 20/40). Reutilízalos; no los dupliques.

Prueba: el catálogo cargado tiene 5 códigos y los puntos coinciden con la tabla.
```

---

## Prompt 2 – Formulario del servicio

```
Configura el formulario del servicio "Verificación académica" usando EXACTAMENTE las claves del diccionario (hojas Campos, Tablas y Listas). Las claves deben coincidir letra por letra con los marcadores del Word.

Reglas:
1. Campos de la ficha (candidato) y del estudio (ev_cliente, ev_cargo, ev_lugar_exp, estudio_numero, fecha_emision): se precargan; si ya existen en el estudio por Antecedentes, se reutilizan.
2. ev_criticidad: es la misma criticidad del estudio. Si ya se escogió en Antecedentes, se toma de ahí; el analista no la escribe dos veces. No se imprime.
3. Listas desplegables con las opciones exactas de la hoja Listas: aca_criterio, aca_categoria, aca_nivel, aca_estado, aca_documento, aca_medio, aca_ref, aca_ac01_base.
   Los nombres de campos y opciones son los del diccionario. El módulo del candidato está en actualización: si sus nombres cambian, se mapean a estas claves; no se cambian las claves del formato.
3b. Tabla educacion_declarada (sección 1 del Word): precarga TODOS los estudios que registró el candidato (ed_institucion, ed_nivel, ed_titulo, ed_fecha_fin = "Fecha fin"). Solo lectura.
3c. Campo aca_estudio_sel (no se imprime): lista con los estudios declarados. Al escoger uno, precarga aca_institucion, aca_ciudad, aca_categoria, aca_nivel, aca_fecha_inicio, aca_fecha_fin, aca_titulo y aca_estado; el analista los corrige con lo que confirme la fuente.
4. aca_ref ("Calificación") es el ÚNICO campo que califica. Opciones: "—", ACA-01 a ACA-05, ALR-ACA-01, ALR-ACA-02. Valor por defecto: "—".
5. aca_criterio es obligatorio. Valor por defecto: "Último estudio finalizado".
6. Campos que se guardan pero NO se imprimen: ev_criticidad, aca_ac01_base, aca_fecha_sol_eval, aca_fecha_limite.
7. aca_ac01_base solo se muestra cuando aca_ref = ACA-01.
8. Tabla gestiones (g_fecha, g_medio, g_destino, g_resultado): una fila por llamada, correo, chat o consulta. Se guarda en la plataforma para control interno y NO se imprime en el informe.
9. Tabla informativos (sección 4): la diligencia el analista.
10. Campos calculados, de solo lectura: tabla hallazgos, srv_formula, srv_puntaje, sem_bajo, sem_medio, sem_alto, aca_fecha_limite (si la plataforma tiene festivos; ver Prompt 4).

No uses la función "Califica riesgo" del constructor para este servicio; la calificación la hace el servicio del Prompt 3, igual que en Antecedentes.

Prueba: compara las claves del formulario con los marcadores de docs/academica/1_Formato_Verificacion_Academica.docx. Las únicas claves que no aparecen en el Word son: ev_criticidad, aca_estudio_sel, aca_ac01_base, aca_fecha_sol_eval, aca_fecha_limite y las columnas de la tabla gestiones.
```

---

## Prompt 3 – Servicio de calificación académica

```
Crea el servicio de calificación de Verificación académica. Debe ser una traducción fiel a Java de calificar() y validar() de docs/academica/motor_aca.py. Reutiliza lo genérico de Antecedentes (nivel por umbrales, formato del puntaje, semáforo).

Entrada: campos y tablas del formulario. Salida: el mismo tipo de resultado que Antecedentes (hallazgos, puntaje, nivel, critico, formula, avisos).

Reglas (hoja Cálculos del diccionario):
1. Se toma aca_ref. Si es ACA-01 a ACA-05, genera UNA fila de hallazgo:
   - h_ref = código
   - h_descripcion = descripción del catálogo
   - h_fuente = aca_medio
   - h_detalle = aca_titulo + " – " + aca_institucion
   - h_puntos = número como texto, o "Crítico"
   "—", ALR-ACA-01 y ALR-ACA-02 no generan fila.
2. ACA-01: puntaje 100, nivel ALTO, critico = true, fórmula "Hallazgo crítico (ACA-01): nivel ALTO".
3. Otro código: puntaje = sus puntos; fórmula "ACA-03: 45 de 100 posibles"; nivel según los umbrales de ev_criticidad.
4. Sin código: puntaje 0, fórmula "Sin hallazgos que sumen puntos", nivel BAJO.
5. Si falta ev_criticidad: excepción de validación "Falta la criticidad del cargo." y no se calcula.

Pruebas unitarias obligatorias (hoja "Casos de prueba"; entradas en CASOS de motor_aca.py):

| Caso | Criticidad | aca_ref | Fórmula | Puntaje | Nivel |
|---|---|---|---|---|---|
| A1 Sin novedad | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| A2 Documento falso (inspección visual, sin visto bueno) | Estándar | ACA-01 | Hallazgo crítico (ACA-01): nivel ALTO | 100,0 | ALTO |
| A3 Institución no confirma el título | Estándar | ACA-02 | ACA-02: 70 de 100 posibles | 70,0 | ALTO |
| A4 Datos que no coinciden | Estándar | ACA-03 | ACA-03: 45 de 100 posibles | 45,0 | MEDIO |
| A5 No aportó lo solicitado | Crítico | ACA-04 | ACA-04: 45 de 100 posibles | 45,0 | ALTO |
| A6 Institución no reconocida | Estándar | ACA-05 | ACA-05: 30 de 100 posibles | 30,0 | BAJO |
| A7 Institución no responde | Sensible | ALR-ACA-02 | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| A8 Por solicitud del cliente, sin explicar | Sensible | ACA-03 | ACA-03: 45 de 100 posibles | 45,0 | MEDIO |
| A9 Terminó materias, marcado como último finalizado | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |

Avisos esperados: A2 → "ACA-01 por inspección visual: falta el visto bueno del segundo analista."; A7 → aviso de registrar la alerta en la sección 4; A8 → aviso de explicar el criterio en Observaciones; A9 → aviso de que un estudio sin grado no es el último finalizado. Los demás, sin avisos.

Pruebas de borde: ACA-03 en cargo Crítico → 45 > 40 → ALTO; ACA-05 en Sensible → 30 > 25 → MEDIO.
```

---

## Prompt 4 – Campos calculados y fecha límite

```
Con el resultado del Prompt 3, llena los campos que lee la plantilla Word. Ejecuta el cálculo al guardar el formulario y otra vez justo antes de generar el Word. Reutiliza lo que ya hace Antecedentes.

1. tablas['hallazgos'] = la lista de hallazgos (0 o 1 fila).
2. campos['srv_puntaje'] = puntaje con un decimal y coma decimal (0,0 / 45,0 / 100,0).
3. campos['srv_formula'] = fórmula.
4. Semáforo: solo el campo del nivel obtenido lleva texto, los otros dos quedan vacíos.
   - Normal: "▲ 45,0 puntos".
   - Con ACA-01: "▲ Hallazgo crítico" en sem_alto.
5. aca_fecha_limite:
   - Si la plataforma tiene calendario de festivos de Colombia (lo revisaste en el Prompt 0): aca_fecha_limite = aca_fecha_sol_eval + 4 días hábiles (lunes a viernes, sin festivos). Campo de solo lectura.
   - Si no lo tiene: déjalo como campo que escribe el analista y avísame, para decidir si se agrega el calendario.
6. Alerta de plazo: cuando la fecha actual pase aca_fecha_limite y aca_ref siga en "—" o en ALR-ACA-02, muestra al analista un aviso en la bandeja del estudio: "Venció el plazo del evaluado: cierre como ACA-04 si no aportó lo solicitado." Usa el mecanismo de avisos o notificaciones que ya exista; si no existe ninguno, avísame antes de crearlo.

Prueba: genera el Word del caso A4 y verifica que no quede ningún "${" sin resolver, que la sección 3 tenga una fila ACA-03 con 45 puntos y que sem_medio diga "▲ 45,0 puntos".
```

---

## Prompt 5 – Integración con el motor del estudio

```
Conecta el resultado académico con el motor del estudio, igual que Antecedentes. NO modifiques las reglas del modelo v1.8 (capas, umbrales, pisos); solo la configuración de pesos.

Decisión de negocio: Validaciones (20 % en el Estudio Avanzado) se divide en tres servicios SEPARADOS, cada uno con su propio puntaje, semáforo y peso:
- Validación laboral: 10 %
- Validación académica: 6 %
- Validación personal: 4 %

Reglas:
1. Cada validación entra al motor como un servicio independiente: capa 1 (crítico), capa 2 (ponderado con su peso) y capa 3 (pisos A y B) se evalúan por servicio.
2. NUNCA se promedian las tres en un solo puntaje de "Validaciones" antes de entrar al motor, porque así un buen resultado en una taparía un mal resultado en otra.
3. Si hoy existe un solo servicio "Validaciones" con 20 %, propón cómo dividirlo en tres sin romper los estudios ya calificados, y muéstrame la propuesta antes de cambiarlo.
4. Mientras laboral y personal no estén programados, el motor debe funcionar con los servicios que tengan resultado. Usa la misma regla que acordamos para Antecedentes sobre servicios faltantes.

Pruebas (cargo Estándar, demás servicios en 0):
- Académica ACA-01 → estudio ALTO, puntaje_final 100.
- Académica ACA-02 (70) → ponderado 70 × 6 % = 4,2 → BAJO por promedio → piso A (servicio ≥ 70) → MEDIO, puntaje_final 31.
- Académica ACA-03 (45) → ponderado 2,7 → BAJO; no activa pisos (A exige 70; B exige dos servicios con más de 50).
- Académica ACA-02 (70) y Visita 55 → piso A y piso B → MEDIO.
```

---

## Prompt 6 – Avisos al guardar

```
Agrega los avisos al guardar el formulario académico (función validar() de motor_aca.py). Son avisos para el analista y no bloquean, salvo el primero.

1. BLOQUEA el cálculo: falta ev_criticidad → "Falta la criticidad del cargo."
2. Falta aca_criterio → "Indique el criterio de verificación."
3. aca_criterio distinto de "Último estudio finalizado" y aca_observaciones con menos de 20 caracteres → "Criterio distinto al último estudio finalizado: explique en Observaciones qué estudio se verificó y por qué."
4. ACA-01 sin aca_ac01_base → "ACA-01: indique si lo confirmó la institución o si es una alteración evidente en la inspección visual."
5. ACA-01 con aca_ac01_base = "Alteración evidente en inspección visual" y sin aca_vobo → "ACA-01 por inspección visual: falta el visto bueno del segundo analista."
6. ACA-04 sin aca_fecha_sol_eval o sin aca_fecha_limite → "ACA-04: registre la fecha de solicitud al evaluado y la fecha límite (4 días hábiles)."
7. aca_ref = ALR-ACA-01 o ALR-ACA-02 → "<código> no suma puntos: regístrela también en la sección 4 (hallazgos sin impacto)."
8. aca_ref = "—", aca_estado sin grado ("Terminó materias, pendiente de grado", "En curso", "Retirado sin graduarse" o "Aplazado") y aca_documento = "Diploma" o "Acta de grado" → "El estudio no tiene grado pero el evaluado aportó diploma o acta: revise si corresponde ACA-01 o ALR-ACA-01."
9. aca_criterio = "Último estudio finalizado" y aca_estado = "Terminó materias, pendiente de grado" → "Un estudio con materias terminadas y sin grado no es el \"Último estudio finalizado\": verifique el último estudio con grado, o use el criterio \"Por solicitud del cliente\" y explíquelo en Observaciones." (caso A9 de motor_aca.py)

Prueba cada aviso con un caso mínimo.
```

---

## Prompt 7 – Prueba de punta a punta

```
Haz una prueba de punta a punta de Verificación académica:

1. Crea un estudio Avanzado de prueba, cargo Estándar, con datos inventados (no uses datos reales).
2. Diligencia el formulario como el caso A4 (ACA-03), con dos filas en gestiones.
3. Genera el Word con docs/academica/1_Formato_Verificacion_Academica.docx y verifica:
   - no queda ningún "${" sin resolver;
   - la sección 3 tiene ACA-03 con 45 puntos y la fórmula "ACA-03: 45 de 100 posibles";
   - el semáforo marca MEDIO con "▲ 45,0 puntos";
   - las gestiones NO aparecen en el Word;
   - el criterio de verificación aparece en la primera fila de la sección 5;
   - la sección 1 muestra la tabla de educación declarada con todos los estudios del candidato.
4. Verifica que el resultado llegue al motor del estudio como servicio "Validación académica" con peso 6 %.
5. Corre todas las pruebas del proyecto, incluidas las de Antecedentes, y dime si algo que ya funcionaba se rompió.

Al final dame un resumen: archivos creados, archivos modificados, pruebas agregadas y cualquier punto pendiente o resuelto de forma distinta a estos prompts.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si estos prompts y `motor_aca.py` no coinciden, manda `motor_aca.py`. Si el diccionario y la plantilla Word no coinciden, manda la plantilla (y avisar para corregir el diccionario).
- **El Word no calcula nada:** solo muestra lo que el código deja en los campos y tablas.
- **Un solo estudio:** el servicio verifica un único estudio, el que indica el criterio de verificación. Por eso hay un solo campo de calificación, no una tabla.
- **Datos reales:** las pruebas usan datos inventados. No subir a Cursor informes ni soportes de candidatos reales.
