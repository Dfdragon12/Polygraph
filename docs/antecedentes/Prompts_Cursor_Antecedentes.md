# Prompts para Cursor – Formato de Antecedentes legales con calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.0, 02/10/2026
**Archivos que acompañan estos prompts (ponerlos en el repositorio, por ejemplo en `docs/antecedentes/`):**

| Archivo | Para qué sirve |
|---|---|
| `VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx` | Plantilla Word con todos los marcadores |
| `Diccionario_marcadores_Antecedentes_FINAL.xlsx` | Campos, tablas, listas, catálogo, reglas de cálculo, umbrales y casos de prueba |
| `motor_ant.py` | Motor de referencia en Python (es la especificación ejecutable; el código Java debe dar los mismos resultados) |
| `casos_prueba.py` | Casos C1 a C5 con entradas; ejecutar `python3 casos_prueba.py` muestra los resultados esperados |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen. Cada prompt le pide a Cursor que primero lea el código existente: no debe inventar clases ni tablas que ya existan.

---

## Prompt 0 – Reconocer el código existente (no escribir nada todavía)

```
Antes de escribir código, necesito que entiendas cómo está construida la plataforma. No modifiques ningún archivo en este paso.

Busca en el repositorio y descríbeme, con rutas de archivos y nombres de clases:

1. El constructor de formularios de servicios: cómo se define un formulario, sus campos ("campos") y sus tablas ("tablas"), cómo se guardan los valores y cómo se configuran las listas desplegables.
2. La función "Califica riesgo" del constructor: cómo se asignan puntos a cada opción de lista, cómo se suman por fila, el tope de 100, las "veces máximas" por opción y la respuesta crítica.
3. El motor de calificación del estudio completo (modelo v1.8): dónde se calculan las tres capas (críticos → ALTO; pesos por servicio; pisos), dónde están los pesos del Estudio Avanzado y los umbrales por criticidad (pantalla "Parámetros de riesgo").
4. El generador de Word: cómo resuelve ${campos['x']}, ${candidato['X']}, los comentarios repeatTableRow(tablas['x']) y repeatDocPart(tablas['x']), y los marcadores ${riesgo[...]} y ${calificacion[...]}.
5. Cómo se precargan datos de la etapa 1 (ficha del candidato, cliente, cargo).
6. Si existe algún "hook" o punto de extensión para ejecutar lógica propia de un servicio al guardar el formulario o antes de generar el Word.
7. El framework de pruebas que usa el proyecto.

Lee también docs/antecedentes/Diccionario_marcadores_Antecedentes_FINAL.xlsx (hojas Campos, Tablas, Listas, Catálogo, Cálculos, Umbrales, Casos de prueba) y docs/antecedentes/motor_ant.py.

Al final dame:
- Un resumen de la arquitectura encontrada.
- Una propuesta de dónde encaja la lógica de Antecedentes (qué clases nuevas, qué clases existentes se tocan).
- Las dudas o diferencias que encuentres entre el diccionario y lo que ya existe.
```

> Revisar la respuesta antes de seguir. Si Cursor encuentra que algo ya existe (por ejemplo, un catálogo de códigos), los siguientes prompts deben reutilizarlo.

---

## Prompt 1 – Catálogo de códigos LEG como configuración

```
Crea el catálogo de códigos del servicio Antecedentes legales como datos de configuración (tabla en base de datos o archivo de configuración, según lo que ya use el proyecto; no lo dejes quemado en el código de cálculo).

Cada código tiene: codigo, descripcion, puntos (entero) o critico (booleano), por_proceso (booleano), solo_conduccion (booleano), activo.

Datos (fuente: hoja "Catálogo" del diccionario y CATALOGO en motor_ant.py):

| Código | Descripción | Puntos | Por proceso | Solo conducción |
|---|---|---|---|---|
| LEG-01 | Documento cancelado o no encontrado en la Registraduría | Crítico | No | No |
| LEG-02 | Coincidencia confirmada en lista vinculante o restrictiva (ONU, OFAC, HM Treasury) | Crítico | No | No |
| LEG-03 | Orden de captura o requerimiento judicial vigente | Crítico | No | No |
| LEG-04 | Notificación o búsqueda vigente de Interpol o Europol | Crítico | No | No |
| LEG-05 | Proceso penal activo o pena vigente como investigado, relacionado con el cargo | Crítico | Sí | No |
| LEG-06 | Registrado como proveedor ficticio en la DIAN | 50 | No | No |
| LEG-07 | Inhabilitado por el Banco Mundial o el BID | 50 | No | No |
| LEG-08 | Responsabilidad fiscal vigente (Contraloría) | 45 | No | No |
| LEG-09 | Sanción o inhabilidad disciplinaria vigente (Procuraduría, Personería) | Crítico | No | No |
| LEG-10 | Licencia de conducción suspendida o cancelada (solo cargos de conducción) | Crítico | No | Sí |
| LEG-11 | Proceso penal activo o pena vigente como investigado, no relacionado con el cargo | 70 | Sí | No |
| LEG-12 | Omitió declarar un antecedente vigente | 25 | No | No |
| LEG-13 | Información adversa en noticias criminales o medios | 40 | No | No |
| LEG-14 | Licencia vencida o categoría que no corresponde al cargo (solo cargos de conducción) | 40 | No | Sí |
| LEG-15 | Multa o sanción como contratista del Estado (SECOP) | 20 | No | No |
| LEG-16 | Reportado como deudor moroso del Estado (BDME) | 15 | No | No |
| LEG-17 | Multas de tránsito pendientes sin acuerdo de pago (solo cargos de conducción) | 20 | No | Sí |
| LEG-18 | Medida correctiva registrada (RNMC), en cualquier estado | 30 | No | No |
| LEG-19 | Proceso penal terminado sin condena (archivo, prescripción, desistimiento, conciliación o principio de oportunidad) | 15 | Sí | No |
| LEG-20 | Condena cumplida por delito relacionado con el cargo | 30 | Sí | No |
| LEG-21 | Condena cumplida por delito no relacionado con el cargo | 20 | Sí | No |
| LEG-22 | Demanda no penal activa en contra (civil, laboral o de familia) | 10 | Sí | No |
| LEG-23 | Demanda no penal terminada en contra (no aplica si el fallo fue a favor del evaluado) | 5 | Sí | No |
| LEG-24 | Suplantación de identidad confirmada (el evaluado usa una identidad ajena) | Crítico | No | No |

Las alertas ALR-01, ALR-02, ALR-03, ALR-04 y ALR-06 NO suman puntos y NO van al catálogo de cálculo; solo son opciones informativas de las listas.

Crea también la configuración de umbrales por criticidad del cargo SOLO si no existe ya en "Parámetros de riesgo" (si existe, reutilízala):
- Estándar: BAJO 0–30, MEDIO 31–60, ALTO >60
- Sensible: BAJO 0–25, MEDIO 26–50, ALTO >50
- Crítico: BAJO 0–20, MEDIO 21–40, ALTO >40

Escribe una prueba que verifique que el catálogo cargado tiene 24 códigos y que los puntos coinciden con la tabla.
```

---

## Prompt 2 – Formulario del servicio: campos, tablas y listas

```
Configura (o crea por código, según cómo funcione el constructor que encontraste en el Prompt 0) el formulario del servicio "Antecedentes legales" del Estudio Avanzado, usando EXACTAMENTE las claves del diccionario (hojas Campos, Tablas y Listas). Las claves deben coincidir letra por letra con los marcadores del Word.

Reglas:
1. Campos que vienen de la ficha (candidato): NOMBRE_COMPLETO, TIPO_DOCUMENTO, CEDULA, FECHA_EXPEDICION, FECHA_NACIMIENTO, LUGAR_NACIMIENTO, RUT. Se precargan de la etapa 1, no se piden al analista.
2. Campo ev_criticidad (Estándar | Sensible | Crítico): es obligatorio, lo diligencia el analista, NO tiene marcador en el Word (no se imprime) y se usa para escoger los umbrales.
3. Listas desplegables con las opciones exactas de la hoja Listas: ev_tipo_cargo (16 tipos del Anexo 2 + Otro), ev_estado_doc, ev_conduccion, decN_respuesta, decN_coincide, ln_ref, li_ref, simit_ref, runt_ref, a_ref, p_calificacion, pp_calificacion, rec_dir_coincide, rec_residencial.
4. Las columnas de "Ref." / "Calificación" (ln_ref, li_ref, simit_ref, runt_ref, a_ref, p_calificacion, pp_calificacion) tienen como opciones "—" más los códigos indicados en la hoja Listas. El valor por defecto es "—".
5. a_ref existe en el formulario pero no se imprime.
6. Tablas: informativos, listas_nacionales, listas_internacionales, simur, runt, alertas_id, direcciones, telefonos, correos, procesos, procesos_privados (columnas en la hoja Tablas). La tabla "hallazgos" NO la llena el analista: la genera el código (Prompt 4).
7. Campos calculados que el analista NO edita (solo lectura en pantalla): srv_formula, srv_puntaje, sem_bajo, sem_medio, sem_alto, transito_nota, alertas_id_nota, procesos_nota, procesos_priv_nota, p_numero, pp_numero y la tabla hallazgos.
8. Campos de texto del analista: srv_alertas_nota (instructivo), concepto, analista_nombre, analista_fecha, y todos los demás de texto del diccionario.

IMPORTANTE: no uses la función "Califica riesgo" del constructor para este servicio. La calificación la hace un servicio propio (Prompt 3), porque necesita reglas que el constructor no tiene (por proceso vs. una sola vez, conducción, LEG-12 calculado). Si el constructor obliga a usarla, déjame las opciones con 0 puntos y avísame.

Escribe una prueba que compare la lista de claves del formulario con la lista de marcadores encontrados en docs/antecedentes/VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx (recorre word/document.xml y word/comments.xml). Deben coincidir; ev_criticidad y a_ref son las únicas claves que no aparecen en el Word.
```

---

## Prompt 3 – Servicio de calificación de Antecedentes

```
Crea el servicio de calificación del servicio Antecedentes legales (por ejemplo, CalificacionAntecedentesService). Debe ser una traducción fiel a Java de la función calificar() de docs/antecedentes/motor_ant.py. Léela completa antes de empezar.

Entrada: los campos y las tablas del formulario del servicio.
Salida: un objeto ResultadoAntecedentes con:
- hallazgos: lista de filas {h_ref, h_descripcion, h_fuente, h_detalle, h_puntos}
- puntaje (double, 0 a 100)
- nivel (BAJO | MEDIO | ALTO)
- critico (boolean) y lista de códigos críticos
- formula (texto)
- avisos (lista de textos internos, no se imprimen)

Reglas (hoja Cálculos del diccionario):

1. Recolección de códigos, en este orden:
   a. ev_estado_doc ∈ {Cancelada por muerte, Cancelada por doble cedulación, No encontrada} → LEG-01 (fuente "Registraduría", detalle "Estado del documento: <valor>"). "Vigente" y "En trámite" no generan código.
   b. Si dec1_coincide, dec2_coincide o dec3_coincide = "No" → LEG-12 UNA sola vez (fuente "Declaración del evaluado", detalle "La respuesta a la(s) pregunta(s) 1, 3 no coincide con lo verificado").
   c. Cada fila de listas_nacionales con ln_ref que sea un código del catálogo (fuente = ln_fuente, detalle = ln_observacion).
   d. Cada fila de listas_internacionales con li_ref del catálogo (fuente = li_lista, detalle = li_observacion).
   e. simit_ref del catálogo (fuente "SIMIT", detalle "Multas: <simit_multas>").
   f. Cada fila de runt con runt_ref del catálogo (fuente "RUNT", detalle "Categoría <runt_categoria>: <runt_estado>").
   g. Cada fila de alertas_id con a_ref del catálogo (fuente = a_fuente, detalle = a_descripcion).
   h. Cada fila de procesos con p_calificacion del catálogo (fuente "Rama Judicial", detalle "Proceso N° <p_numero>: <p_delito> (<p_estado>)").
   i. Cada fila de procesos_privados con pp_calificacion del catálogo (fuente "Bases privadas", detalle igual con pp_).
   Se ignoran "—", vacíos y los códigos ALR.

2. Conducción: si ev_conduccion ≠ "Sí", se descartan los códigos con solo_conduccion = true (LEG-10, LEG-14, LEG-17) y se agrega el aviso "<código> ignorado: el cargo no implica conducción".

3. Acumulación: los códigos con por_proceso = true suman una vez por cada fila en que aparecen. Los demás cuentan una sola vez (se conserva la primera aparición, en el orden del punto 1). No hay más límite que el tope de 100.

4. Puntaje y nivel:
   - Si hay al menos un código crítico: puntaje = 100, nivel = ALTO, critico = true.
   - Si no: puntaje = min(100, suma de puntos); nivel según los umbrales de ev_criticidad (BAJO si puntaje ≤ límite BAJO; MEDIO si ≤ límite MEDIO; si no, ALTO).
   - Si ev_criticidad está vacío: lanzar una excepción de validación clara ("Falta la criticidad del cargo") y no calcular.

5. Fórmula (texto exacto):
   - Con crítico: "Hallazgo crítico (LEG-09): nivel ALTO" (si hay varios: "Hallazgo crítico (LEG-02, LEG-09): nivel ALTO", sin repetir).
   - Sin hallazgos que sumen: "Sin hallazgos que sumen puntos".
   - Suma ≤ 100: "30 + 15 + 15 = 60 de 100 posibles".
   - Suma > 100: "25 + 40 + 70 = 135 → 100 (tope máximo)".
   Los sumandos van en el orden de la tabla de hallazgos.

6. h_puntos: el número como texto ("30") o la palabra "Crítico".

Usa el catálogo y los umbrales de la configuración del Prompt 1 (no los quemes).

Pruebas unitarias obligatorias: los 5 casos de la hoja "Casos de prueba" (entradas en docs/antecedentes/casos_prueba.py). Resultados esperados:

| Caso | Hallazgos (en orden) | Fórmula | Puntaje | Nivel | Avisos |
|---|---|---|---|---|---|
| C1 Limpio (Estándar) | — | Sin hallazgos que sumen puntos | 0,0 | BAJO | — |
| C2 RNMC repetida + 2 archivados + SIMIT sin conducción (Estándar) | LEG-18, LEG-19, LEG-19 | 30 + 15 + 15 = 60 de 100 posibles | 60,0 | MEDIO | LEG-17 ignorado: el cargo no implica conducción |
| C3 Sanción disciplinaria vigente (Sensible) | LEG-09, LEG-21 | Hallazgo crítico (LEG-09): nivel ALTO | 100,0 | ALTO | — |
| C4 Tope (Crítico) | LEG-12, LEG-13, LEG-11 | 25 + 40 + 70 = 135 → 100 (tope máximo) | 100,0 | ALTO | — |
| C5 Cargo de conducción (Estándar) | LEG-17, LEG-14 | 20 + 40 = 60 de 100 posibles | 60,0 | MEDIO | — |

Agrega además estas pruebas de borde:
- Puntaje exactamente en el límite: Estándar con 30 puntos → BAJO; con 60 → MEDIO; con 61 → ALTO.
- LEG-18 en dos filas → suma 30 una sola vez.
- LEG-11 en dos procesos → suma 140 → tope 100.
- ev_estado_doc = "En trámite" → no genera LEG-01.
- li_ref = "ALR-02" → no genera hallazgo.
```

---

## Prompt 4 – Campos calculados para el Word

```
Con el ResultadoAntecedentes del Prompt 3, llena los campos calculados que lee la plantilla Word. Ejecuta el cálculo cada vez que el analista guarde el formulario y otra vez justo antes de generar el Word (para que nunca quede desactualizado).

1. tablas['hallazgos'] = la lista de hallazgos (si está vacía, la tabla del Word queda sin filas de datos; la nota de la sección 3 la escribe el analista si quiere).
2. campos['srv_puntaje'] = puntaje con un decimal y coma decimal: 0,0 / 60,0 / 100,0 (mismo formato que ${calificacion['puntaje']}).
3. campos['srv_formula'] = fórmula.
4. Semáforo: solo el campo del nivel obtenido lleva texto, los otros dos quedan vacíos ("").
   - campos['sem_bajo'] | campos['sem_medio'] | campos['sem_alto'] = "▲ 60,0 puntos"
   - Si hay crítico: "▲ Hallazgo crítico" en sem_alto.
5. campos['transito_nota'] = si ev_conduccion = "No": "Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos."; si "Sí": "".
   La sección de tránsito SIEMPRE se imprime, aunque el cargo no sea de conducción.
6. campos['alertas_id_nota'] = "Sin alertas reportadas." si la tabla alertas_id está vacía; si no, "".
7. campos['procesos_nota'] = "No se encontraron procesos judiciales en Rama Judicial." si procesos está vacía; si no, "".
8. campos['procesos_priv_nota'] = "No se encontraron procesos en bases privadas." si procesos_privados está vacía; si no, "".
9. p_numero y pp_numero: consecutivo 1, 2, 3… en el orden de las filas de cada tabla (se recalcula si el analista borra o reordena filas).

Prueba: genera el Word del caso C2 y verifica que no quede ningún "${" sin resolver, que la sección 3 tenga 3 filas y que sem_medio diga "▲ 60,0 puntos".
```

---

## Prompt 5 – Integración con el modelo de calificación del estudio (v1.8)

```
Conecta el resultado de Antecedentes con el motor del estudio que encontraste en el Prompt 0. NO dupliques el motor del estudio: el servicio de Antecedentes solo le entrega su resultado.

Lo que entrega Antecedentes al motor del estudio:
- puntaje del servicio (0–100)
- critico (true/false) y los códigos críticos
- nivel del servicio (para mostrarlo; el estudio recalcula el suyo)

Cómo lo usa el motor del estudio (verifica que ya funcione así; si algo no coincide, avísame antes de cambiarlo):
- Capa 1 – Críticos: si cualquier servicio tiene critico = true → el estudio queda ALTO y puntaje_final = 100.
- Capa 2 – Ponderado: Estudio Avanzado = Antecedentes 25 % + Poligrafía 25 % + Validaciones 20 % + Financiero 15 % + Visita 15 %. Nivel según los umbrales de la criticidad del cargo.
- Capa 3 – Pisos: (A) si un servicio tiene puntaje ≥ 70 → el estudio queda mínimo MEDIO; (B) si dos servicios tienen puntaje > 50 → mínimo MEDIO. Cuando un piso sube el nivel, puntaje_final = el mínimo del rango MEDIO de esa criticidad (Estándar 31, Sensible 26, Crítico 21).
- Los marcadores ${calificacion['nivel']} y ${calificacion['puntaje']} del informe final del estudio salen de ese motor.

Corrección pendiente detectada en la pantalla "Parámetros de riesgo": el "Mínimo MEDIO" de Estándar aparece como 87; debe ser 31 (Sensible 26, Crítico 21). Revisa de dónde sale ese valor y corrígelo, o dime si es un dato de configuración que debemos cambiar manualmente.

Mientras los otros servicios no estén programados, el motor debe funcionar con los servicios que tengan resultado (no fallar si faltan). Pregúntame cómo quieres tratar los pesos de los servicios faltantes antes de implementarlo: no asumas una regla.

Pruebas:
- Antecedentes C3 (crítico) → estudio ALTO, puntaje_final 100, aunque los demás servicios estén en 0.
- Antecedentes 72 puntos, demás en 0, Estándar → ponderado 18, pero el piso A lo sube a MEDIO con puntaje_final 31.
```

---

## Prompt 6 – Validaciones al guardar (avisos para el analista)

```
Agrega validaciones al guardar el formulario de Antecedentes. Son AVISOS que se muestran al analista; no bloquean el guardado, salvo la primera.

1. BLOQUEA el cálculo: ev_criticidad vacío → "Falta la criticidad del cargo."
2. Fila de procesos/procesos_privados con calidad Víctima, Denunciante o Demandante y calificación distinta de "—" → "La calidad del evaluado no suma puntos; revise la calificación."
3. LEG-05 o LEG-20 con relación con el cargo = "No" → "Marcó un código 'relacionado con el cargo' pero la relación dice No."
4. LEG-11 o LEG-21 con relación = "Sí" → "Marcó un código 'no relacionado' pero la relación dice Sí."
5. Proceso de tipo Ejecutivo con calificación distinta de "—" → "Los procesos ejecutivos se analizan en el servicio Financiero."
6. li_coincidencia = "Solo nombre" con LEG-02 o LEG-04 → "Una coincidencia solo por nombre es ALR-02, no un hallazgo confirmado."
7. Código de conducción (LEG-10, LEG-14, LEG-17) con ev_conduccion = "No" → "Este código no suma porque el cargo no implica conducción."
8. Tabla informativos vacía y existe algún ALR seleccionado en ln_ref, li_ref o a_ref → "Registre la alerta en la sección 4 (hallazgos sin impacto)."

Prueba cada aviso con un caso mínimo.
```

---

## Prompt 7 – Prueba de punta a punta y revisión final

```
Haz una prueba de punta a punta del servicio Antecedentes:

1. Crea un estudio de prueba Estándar con los datos del caso C2 (inventados, no uses datos reales).
2. Guarda el formulario, genera el Word con la plantilla docs/antecedentes/VERIFICACION_ANTECEDENTES_LEGALES_FINAL.docx y verifica:
   - no queda ningún "${" sin resolver;
   - la sección 3 tiene LEG-18, LEG-19, LEG-19 con 30, 15, 15;
   - la fórmula dice "30 + 15 + 15 = 60 de 100 posibles" y el puntaje "60,0";
   - el semáforo marca MEDIO;
   - la sección de tránsito aparece con la nota informativa;
   - las secciones 11 y 12 repiten un bloque por proceso con su número consecutivo.
3. Verifica que el resultado llegue al motor del estudio (Prompt 5).
4. Corre todas las pruebas del proyecto y dime si algo que ya funcionaba se rompió.

Al final, dame un resumen: archivos creados, archivos modificados, pruebas agregadas y cualquier punto que haya quedado pendiente o que hayas resuelto de forma distinta a estos prompts.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si hay diferencia entre estos prompts y `motor_ant.py`, manda `motor_ant.py`; si hay diferencia entre el diccionario y la plantilla Word, manda la plantilla (y avisar para corregir el diccionario).
- **El Word no calcula nada:** solo muestra lo que el código deja en los campos y tablas.
- **Datos reales:** las pruebas deben usar datos inventados; no subir a Cursor informes ni soportes de candidatos reales.
- **Siguientes servicios:** Validación académica, laboral y personal, Financiero, Visita y Poligrafía seguirán el mismo patrón (catálogo propio, servicio de calificación propio, campos calculados, entrega al motor del estudio).
