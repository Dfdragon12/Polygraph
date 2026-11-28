# Prompts para Cursor – Formato de Verificación laboral con calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.0, 05/10/2026
**Requisito previo:** Antecedentes legales y Verificación académica ya implementados con sus prompts. Este servicio sigue el mismo patrón y debe reutilizar lo construido (catálogo configurable, servicio de calificación, campos calculados, avisos, entrega al motor del estudio y la división de Validaciones en tres servicios).

**Archivos que acompañan estos prompts (ponerlos en `docs/laboral/`):**

| Archivo | Para qué sirve |
|---|---|
| `1_Formato_Verificacion_Laboral.docx` | Plantilla Word con todos los marcadores (4 funciones de repetición) |
| `2_Diccionario_marcadores_Laboral.xlsx` | Campos, tablas, listas, catálogo, cálculos, umbrales y casos de prueba |
| `motor_lab.py` | Motor de referencia en Python (especificación ejecutable; el Java debe dar los mismos resultados) |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen.

**Diferencias principales con Antecedentes y Académica:**
- Hay una "pasta" por cada vinculación laboral (bloque `repeatDocPart`) y cada una puede llevar hasta dos códigos.
- Los códigos suman por vinculación. LAB-13 (inactividad no corroborada) y LAB-14 (inactividad sustentada solo con la declaración del evaluado) cuentan una sola vez por estudio cada uno.
- Se precargan las experiencias que relacionó el candidato (resumen en la sección 1 y una pasta por experiencia) y los "tiempos muertos" que ya calcula la plataforma.

---

## Prompt 0 – Revisar lo que ya existe (no escribir nada todavía)

```
Vamos a construir el servicio "Verificación laboral" del Estudio Avanzado, con el mismo patrón de "Antecedentes legales" y "Verificación académica". No modifiques ningún archivo en este paso.

1. Muéstrame lo que ya existe y es reutilizable: catálogo configurable, servicio de calificación base, cálculo de nivel y semáforo, avisos al guardar, entrega al motor del estudio, servicio "Validación académica" separado con 6 %.
2. Ubica dónde guarda la plataforma la "Experiencia laboral" que relaciona el candidato (empresa, cargo, inicio, fin, motivo de retiro) y dónde calcula el "tiempo muerto" entre experiencias (pantalla de coordinación: "Tiempo muerto de N días (desde → hasta) – Sin justificación registrada").
3. Revisa si el generador de Word ya soporta repeatDocPart (lo usa Antecedentes en procesos judiciales) y si permite tablas cuyas filas tengan columnas que no se imprimen.
4. Revisa si ya existe la utilidad de días hábiles con festivos de Colombia (se pidió en Académica).

Lee docs/laboral/2_Diccionario_marcadores_Laboral.xlsx (todas las hojas) y docs/laboral/motor_lab.py.

Al final dame la propuesta de clases nuevas y reutilizadas, y las dudas que encuentres.
```

---

## Prompt 1 – Catálogo laboral como configuración

```
Crea el catálogo de Verificación laboral con el mismo mecanismo de configuración de los otros servicios.

| Código | Descripción | Puntos | Acumula |
|---|---|---|---|
| LAB-01 | Certificado laboral falso o adulterado | Crítico | Por vinculación |
| LAB-02 | La empresa no reconoce la vinculación declarada | 70 | Por vinculación |
| LAB-03 | Datos que no coinciden de fondo (fechas, cargo o tipo de contrato) | 60 | Por vinculación |
| LAB-04 | El motivo de retiro declarado no coincide con el confirmado | 45 | Por vinculación |
| LAB-05 | Despido con justa causa | 70 | Por vinculación |
| LAB-06 | Abandono del cargo | 60 | Por vinculación |
| LAB-07 | Llamados de atención por faltas de cumplimiento | 50 | Por vinculación |
| LAB-08 | Llamados o sanciones por faltas de conducta o confianza | 70 | Por vinculación |
| LAB-09 | Persona natural o informal: concepto "malo" o no lo volvería a contratar | 50 | Por vinculación |
| LAB-10 | Experiencia omitida en la hoja de vida (más de 30 días), sin explicación razonable | 40 | Por vinculación |
| LAB-11 | No verificable: el evaluado no aportó el certificado en 4 días hábiles | 50 | Por vinculación |
| LAB-12 | Persona natural o informal: concepto "regular" del jefe | 30 | Por vinculación |
| LAB-13 | Periodo de inactividad no corroborado | 30 | Una vez por estudio |
| LAB-14 | Periodo de inactividad sustentado solo con la declaración del evaluado | 10 | Una vez por estudio |

Atributos adicionales por código: solo_informal (true para LAB-09 y LAB-12), seleccionable_en_vinculacion (false para LAB-13 y LAB-14, que los genera la tabla de inactividad).
ALR-LAB-01 (pendiente por causa ajena al evaluado) es opción de lista pero no suma ni genera hallazgo.
Reutiliza los umbrales del modelo v1.8.

Prueba: 14 códigos cargados con sus puntos; LAB-13 y LAB-14 no aparecen en las listas de código de la vinculación.
```

---

## Prompt 2 – Formulario: vinculaciones, inactividad y precarga

```
Configura el formulario de "Verificación laboral" con EXACTAMENTE las claves del diccionario (hojas Campos, Tablas y Listas).

1. Campos del estudio y de la ficha: se reutilizan los que ya existen (incluida ev_criticidad, que no se imprime).
2. lab_periodo_desde y lab_periodo_hasta: los escribe el analista. NO los calcules: el candidato siempre relaciona 4 años y el analista decide cuántos valida según el tipo de estudio.
3. Tabla vinculaciones (una fila = una "pasta"): columnas impresas y no impresas según la hoja Tablas. En pantalla, cada vinculación se edita como una ficha, no como una fila de tabla, porque tiene muchos campos.
4. Precarga al crear el servicio:
   - Tabla experiencia_declarada (sección 1 del Word): TODAS las experiencias que registró el candidato (ex_empresa, ex_cargo, ex_inicio, ex_fin). Solo lectura; el analista no la edita.
   - Una vinculación por cada experiencia que relacionó el candidato: v_decl_empresa, v_decl_cargo, v_decl_inicio, v_decl_fin, v_decl_motivo (no se imprimen) y v_empresa con el mismo nombre.
   - Una fila de inactividad por cada "tiempo muerto" mayor a 30 días que ya calcula la plataforma: in_desde, in_hasta, in_dias.
   - El analista puede agregar vinculaciones (por ejemplo, las halladas en la sábana pensional) y borrar las que quedan fuera del periodo validado.
5. En la ficha de la vinculación muestra lado a lado lo declarado (v_decl_*) y lo confirmado, para que el analista decida LAB-03 o LAB-04.
6. Listas con las opciones exactas de la hoja Listas: v_validado_por, v_tipo, v_motivo_clase, v_ref1, v_ref2, v_lab01_base, in_corroboracion.
7. v_lab01_base y v_vobo solo se muestran cuando v_ref1 o v_ref2 = LAB-01. v_fecha_sol_eval y v_fecha_limite, cuando se marca LAB-11 o cuando el analista solicita certificado.
8. v_seguimiento: texto interno para la nota de seguimiento ("nota verde"). No se imprime.
9. Tabla gestiones (g_vinculacion, g_fecha, g_medio, g_destino, g_resultado): solo en la plataforma, no se imprime.
10. Calculados de solo lectura: v_numero, v_calificacion, v_nota, tabla hallazgos, srv_formula, srv_puntaje, sem_*, vinc_nota, inactividad_nota, v_fecha_limite (si hay festivos).

No uses "Califica riesgo" del constructor; califica el servicio del Prompt 3.

Prueba: las claves impresas del formulario coinciden con los marcadores de docs/laboral/1_Formato_Verificacion_Laboral.docx.
```

---

## Prompt 3 – Servicio de calificación laboral

```
Crea el servicio de calificación de Verificación laboral, traducción fiel de calificar() y validar() de docs/laboral/motor_lab.py. Reutiliza lo genérico de los otros servicios.

Reglas:
1. Numera las vinculaciones 1, 2, 3… ANTES de calificar.
2. Por cada vinculación: toma v_ref1 y v_ref2; si son iguales cuentan una vez; ignora "—" y ALR-LAB-01. Cada código genera una fila de hallazgo:
   - h_ref = código; h_descripcion = catálogo; h_fuente = v_validado_por
   - h_detalle = "Vinculación N° {n}: {v_empresa} ({v_cargo})"; h_puntos = número o "Crítico".
3. Inactividad: si al menos una fila tiene in_corroboracion = "No se pudo corroborar", agrega UNA fila LAB-13 (30); si al menos una tiene "Solo declaración del evaluado", agrega UNA fila LAB-14 (10). Van al final, en ese orden:
   h_fuente = "Periodos de inactividad", h_detalle = "{k} periodo(s) (cuenta una vez por estudio)".
4. Puntaje: con LAB-01 → 100 y ALTO. Si no, suma con tope 100 y nivel según los umbrales de ev_criticidad.
5. Fórmula: "Sin hallazgos que sumen puntos" / "LAB-07: 50 de 100 posibles" / "50 + 45 = 95 de 100 posibles" / "70 + 60 = 130 → 100 (tope máximo)" / "Hallazgo crítico (LAB-01): nivel ALTO".

Pruebas obligatorias (CASOS de motor_lab.py; resultados en la hoja "Casos de prueba"):

| Caso | Criticidad | Hallazgos | Fórmula | Puntaje | Nivel |
|---|---|---|---|---|---|
| L1 Sin novedad | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| L2 Validada por sábana | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| L3 Llamado de cumplimiento + motivo no coincide | Sensible | LAB-07, LAB-04 | 50 + 45 = 95 de 100 posibles | 95,0 | ALTO |
| L4 Certificado falso | Crítico | LAB-01 | Hallazgo crítico (LAB-01): nivel ALTO | 100,0 | ALTO |
| L5 Datos no coinciden + 2 inactividades sin corroborar | Estándar | LAB-03, LAB-13 | 60 + 30 = 90 de 100 posibles | 90,0 | ALTO |
| L6 Llamados en dos empresas, uno repetido en la misma | Estándar | LAB-07, LAB-07 | 50 + 50 = 100 de 100 posibles | 100,0 | ALTO |
| L7 Despido con justa causa + abandono | Estándar | LAB-05, LAB-06 | 70 + 60 = 130 → 100 (tope máximo) | 100,0 | ALTO |
| L8 Concepto regular en empresa formal | Sensible | LAB-12 | LAB-12: 30 de 100 posibles | 30,0 | MEDIO |
| L9 Nunca trabajó formalmente, inactividad corroborada | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| L10 Inactividad: una sin corroborar y dos solo declaradas | Sensible | LAB-13, LAB-14 | 30 + 10 = 40 de 100 posibles | 40,0 | MEDIO |

Avisos esperados: L6 → "el mismo código está marcado dos veces"; L8 → "LAB-12 solo aplica a vinculaciones con persona natural o informales". Los demás, sin avisos.
```

---

## Prompt 4 – Campos calculados para el Word

```
Con el resultado del Prompt 3 llena los campos que lee la plantilla. Calcula al guardar y otra vez antes de generar el Word.

1. tablas['vinculaciones']: cada fila con v_numero, v_calificacion (v_ref1 y v_ref2 sin "—" ni repetidos, separados por coma; "—" si no hay) y v_nota:
   si v_validado_por = "Sábana pensional o bases de datos" →
   "Se confirma la vinculación por medio de bases de datos públicas y privadas. No fue posible validar motivo de retiro, llamados de atención ni la veracidad del certificado."
   si no → vacío.
2. tablas['hallazgos'], srv_puntaje (un decimal con coma), srv_formula y semáforo (solo el nivel obtenido lleva "▲ 50,0 puntos" o "▲ Hallazgo crítico"), igual que los otros servicios.
3. vinc_nota: sin vinculaciones → "El evaluado no registra vinculaciones laborales en el periodo validado."
4. inactividad_nota: sin filas de inactividad → "No se identificaron periodos de inactividad superiores a 30 días en el periodo validado."
5. v_fecha_limite = v_fecha_sol_eval + 4 días hábiles (utilidad de festivos de Académica). Aviso en la bandeja cuando venza sin respuesta: "Venció el plazo del certificado de la vinculación N° {n}: cierre como LAB-11 si no lo aportó."
6. La sección 7 (inactividad) se imprime siempre, aunque la tabla esté vacía.

Prueba: genera el Word de L5 y verifica que no quede "${" sin resolver, que la sección 1 muestre la experiencia declarada, que haya un bloque por vinculación con su número, que la sección 3 tenga LAB-03 y LAB-13, y que sem_alto diga "▲ 90,0 puntos".
```

---

## Prompt 5 – Integración con el motor del estudio y cierre

```
1. Entrega el resultado al motor del estudio como servicio "Validación laboral", peso 10 % en el Avanzado, SEPARADO de "Validación académica" (6 %) y "Validación personal" (4 %). Cada uno entra por separado a las capas 1, 2 y 3 del modelo v1.8. No modifiques las reglas del modelo.
2. Bloqueo del informe final: si alguna vinculación tiene v_validado_por = "Pendiente", no permitas generar el informe final y muestra: "Vinculación N° {n}: sigue pendiente. Ciérrela por sábana pensional o como LAB-11 antes del informe final." Los borradores o vistas previas sí se pueden generar.
3. Respuestas tardías (después de entregado el informe):
   - El informe entregado NO se modifica ni se regenera.
   - Permite registrar la novedad en el historial del estudio: fecha, analista, vinculación, código y soporte adjunto.
   - Deja preparado el registro; el documento de adenda se definirá después (aún no se decide si muestra el nivel recalculado). No lo construyas todavía.

Pruebas (cargo Estándar, demás servicios en 0):
- Laboral LAB-01 → estudio ALTO, puntaje_final 100.
- Laboral LAB-02 (70) → ponderado 7,0 → piso A → MEDIO, puntaje_final 31.
- Laboral LAB-03 (60) y Visita 55 → piso B → MEDIO.
- Laboral LAB-07 en dos vinculaciones (100) → piso A → MEDIO.
- Laboral LAB-04 (45) → ponderado 4,5 → BAJO.
```

---

## Prompt 6 – Avisos al guardar

```
Agrega los avisos de validar() de motor_lab.py. No bloquean, salvo los indicados.

1. BLOQUEA el cálculo: falta ev_criticidad.
2. Falta lab_periodo_desde o lab_periodo_hasta → "Indique el periodo validado (desde y hasta) según el tipo de estudio."
3. v_ref1 = v_ref2 (distinto de "—") → "Vinculación {n}: el mismo código está marcado dos veces; cuenta una sola vez."
4. LAB-09 o LAB-12 con v_tipo ≠ "Persona natural o informal" → "Vinculación {n}: {código} solo aplica a vinculaciones con persona natural o informales."
5. ALR-LAB-01 → "Vinculación {n}: ALR-LAB-01 no suma puntos; regístrela también en la sección 4."
6. LAB-11 sin v_fecha_sol_eval o v_fecha_limite → "Vinculación {n}: LAB-11 requiere la fecha de solicitud al evaluado y la fecha límite."
7. LAB-01 sin v_lab01_base → aviso; con "Alteración evidente en inspección visual" y sin v_vobo → "requiere visto bueno del segundo analista."
8. v_motivo_clase = "Despido con justa causa" sin LAB-05 → "revise si corresponde LAB-05". "Abandono del cargo" sin LAB-06 → "revise si corresponde LAB-06".
9. v_validado_por = "Pendiente" → aviso al guardar; BLOQUEA solo el informe final (Prompt 5).

Prueba cada aviso con un caso mínimo.
```

---

## Prompt 7 – Prueba de punta a punta

```
1. Crea un estudio Avanzado de prueba, cargo Estándar, con datos inventados: tres experiencias declaradas y un tiempo muerto de 120 días.
2. Verifica la precarga: tres vinculaciones con los datos declarados y una fila de inactividad.
3. Diligencia: vinculación 1 sin novedad (validada por la empresa), vinculación 2 con LAB-07, vinculación 3 validada por sábana; inactividad "Referencia confirmó".
4. Genera el Word con docs/laboral/1_Formato_Verificacion_Laboral.docx y verifica:
   - no queda "${" sin resolver; hay tres bloques "VINCULACIÓN LABORAL N° 1/2/3";
   - la vinculación 3 muestra la nota de alcance de la sábana pensional;
   - sección 3: LAB-07 con 50; fórmula "LAB-07: 50 de 100 posibles"; semáforo MEDIO "▲ 50,0 puntos";
   - las gestiones y la nota de seguimiento NO aparecen.
5. Cambia la vinculación 1 a "Pendiente" y confirma que el informe final queda bloqueado.
6. Verifica la entrega al motor como "Validación laboral" con 10 %.
7. Corre todas las pruebas del proyecto (Antecedentes y Académica incluidas) y reporta si algo se rompió.

Al final: archivos creados y modificados, pruebas agregadas y puntos pendientes o resueltos de forma distinta.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si estos prompts y `motor_lab.py` no coinciden, manda `motor_lab.py`. Si el diccionario y la plantilla no coinciden, manda la plantilla (y avisar).
- **El Word no calcula nada:** solo muestra lo que el código deja en los campos y tablas.
- **Reglas especiales de clientes** (por ejemplo, últimas tres vinculaciones o no validar la empresa actual): NO se programan; las identifica el analista al definir el periodo y las vinculaciones.
- **Datos reales:** las pruebas usan datos inventados. No subir a Cursor informes ni soportes de candidatos reales.
