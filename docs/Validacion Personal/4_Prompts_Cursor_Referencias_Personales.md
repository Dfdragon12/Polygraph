# Prompts para Cursor – Formato de Verificación de referencias personales con calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.0, 06/10/2026
**Requisito previo:** Antecedentes legales, Verificación académica y Verificación laboral ya implementados. Este servicio sigue el mismo patrón y debe reutilizar lo construido (catálogo configurable, servicio de calificación, campos calculados, avisos, entrega al motor y la división de Validaciones en tres servicios).

**Archivos que acompañan estos prompts (ponerlos en `docs/personal/`):**

| Archivo | Para qué sirve |
|---|---|
| `1_Formato_Referencias_Personales.docx` | Plantilla Word con todos los marcadores (4 funciones de repetición) |
| `2_Diccionario_marcadores_Referencias_Personales.xlsx` | Campos, tablas, listas, catálogo, cálculos, umbrales y casos de prueba |
| `motor_per.py` | Motor de referencia en Python (especificación ejecutable; el Java debe dar los mismos resultados) |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen.

**Lo propio de este servicio:**
- El candidato registra 4 referencias; en el Estudio Avanzado se validan 2. La sección 1 muestra las 4 declaradas (sin teléfonos) y la sección 5 una ficha por cada referencia validada.
- No hay códigos críticos. PER-03, PER-05 y PER-06 los genera la plataforma.
- Validaciones de los teléfonos al registrar (formulario del candidato).

---

## Prompt 0 – Revisar lo que ya existe (no escribir nada todavía)

```
Vamos a construir el servicio "Verificación de referencias personales" del Estudio Avanzado, con el mismo patrón de Antecedentes, Académica y Laboral. No modifiques ningún archivo en este paso.

1. Muéstrame lo reutilizable: catálogo configurable, servicio de calificación base, nivel y semáforo, avisos, entrega al motor, servicio "Validación personal" (4 %).
2. Ubica dónde guarda la plataforma las referencias personales que registra el candidato (nombre, parentesco, teléfono, tiempo de conocimiento) y cómo se valida hoy ese formulario.
3. Confirma que el generador de Word soporta repeatDocPart (lo usan Antecedentes y Laboral) y columnas que no se imprimen.

Lee docs/personal/2_Diccionario_marcadores_Referencias_Personales.xlsx y docs/personal/motor_per.py.

Nota: el módulo del candidato está en actualización. Si sus nombres de campos cambian, se mapean a las claves del diccionario; no se cambian las claves del formato.

Al final dame la propuesta de clases nuevas y reutilizadas, y las dudas.
```

---

## Prompt 1 – Catálogo y validaciones de registro

```
1. Crea el catálogo de "Referencias personales" con el mismo mecanismo de configuración:

| Código | Descripción | Puntos | Acumula | Lo marca |
|---|---|---|---|---|
| PER-01 | Referencia falsa: no conoce al evaluado o no es la persona registrada | 70 | Por referencia | Analista |
| PER-02 | La referencia da información que contradice lo verificado (trabajo, residencia) | 40 | Por referencia | Analista |
| PER-03 | Concepto desfavorable: promedio menor a 3, o la referencia expresa reservas | 50 | Por referencia | Analista o automático (promedio < 3) |
| PER-05 | No verificable: no hay 2 referencias válidas y contactables en 4 días hábiles | 45 | Una vez por estudio | Automático |
| PER-06 | Referencia presentada sin cumplir los requisitos | 20 | Una vez por estudio | Automático (estado de la declarada) |

ALR-PER-01 (la referencia no contesta) es informativa: no suma.
Reutiliza los umbrales del modelo v1.8.

2. En el formulario del CANDIDATO (registro de referencias), agrega estas validaciones que impiden guardar, con mensaje para corregir:
   - teléfono que no tenga 10 dígitos → "El celular debe tener 10 dígitos.";
   - el mismo teléfono en dos referencias → "Este número ya lo registró en otra referencia.";
   - teléfono igual al del candidato → "No puede registrar su propio número como referencia.";
   - mínimo 4 referencias obligatorias.
   Si alguna referencia ya registrada (de antes del cambio) incumple, muéstralo como alerta al analista; no bloquees el estudio.

Prueba: 5 códigos cargados; las tres validaciones de registro con un caso cada una.
```

---

## Prompt 2 – Formulario del servicio

```
Configura el formulario con EXACTAMENTE las claves del diccionario (hojas Campos, Tablas y Listas).

1. Tabla referencias_declaradas (sección 1): precarga las 4 referencias que registró el candidato (rd_nombre, rd_parentesco, rd_tiempo, rd_telefono). rd_telefono NO se imprime.
   El analista solo diligencia rd_estado: Validada; Descartada – no cumple requisitos; Descartada – no contesta; Descartada – número errado no corregido; No contactada (no fue necesaria).
2. Tabla referencias (sección 5, una ficha por referencia validada): al marcar una declarada como "Validada", crea su ficha y precarga r_nombre y r_parentesco.
   El analista diligencia r_tiempo, r_ultima, r_trabajo, r_trabajo_coincide, r_residencia, r_residencia_coincide, r_a1 a r_a5 (escala 1 a 5), r_concepto y r_ref (—, PER-01, PER-02, PER-03).
3. Calculados de solo lectura: r_numero, r_promedio, r_calificacion, per_promedio_general, ref_nota, tabla hallazgos, srv_formula, srv_puntaje, sem_*.
4. Tabla gestiones (solo plataforma): g_referencia, g_fecha, g_medio, g_resultado.
5. ev_criticidad: la misma del estudio; no se imprime.

No uses "Califica riesgo" del constructor; califica el servicio del Prompt 3.

Prueba: las claves impresas del formulario coinciden con los marcadores de docs/personal/1_Formato_Referencias_Personales.docx; rd_telefono, rd_estado, r_ref y gestiones no aparecen en el Word.
```

---

## Prompt 3 – Servicio de calificación

```
Crea el servicio de calificación de Referencias personales, traducción fiel de calificar(), validar() y alertas_registro() de docs/personal/motor_per.py.

Reglas:
1. Numera las referencias validadas (1, 2) antes de calificar.
2. Por cada referencia: r_promedio = promedio de r_a1..r_a5. Códigos = r_ref (si es PER-01/02/03) + PER-03 automático si r_promedio < 3 (sin repetir).
   Cada código → fila de hallazgo: h_fuente = "Referencia N° {n}", h_detalle = "{r_nombre} ({r_parentesco})".
3. PER-05 una vez si hay menos de 2 referencias validadas: h_fuente "Referencias personales", h_detalle "{k} de 2 referencias validadas".
4. PER-06 una vez si alguna declarada tiene rd_estado = "Descartada – no cumple requisitos": h_fuente "Referencias declaradas".
5. Puntaje = suma con tope 100; nivel por umbrales; fórmula y semáforo como en los demás servicios.
6. per_promedio_general = promedio de los r_promedio; r_calificacion = códigos de la referencia separados por coma o "—"; ref_nota si no hay referencias validadas.

Pruebas obligatorias (CASOS de motor_per.py):

| Caso | Criticidad | Hallazgos | Fórmula | Puntaje | Nivel |
|---|---|---|---|---|---|
| P1 Dos referencias favorables | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| P2 Una referencia con promedio 2,6 | Estándar | PER-03 | PER-03: 50 de 100 posibles | 50,0 | MEDIO |
| P3 Referencia falsa + otra descartada por requisitos | Estándar | PER-01, PER-06 | 70 + 20 = 90 de 100 posibles | 90,0 | ALTO |
| P4 Solo una referencia validada | Sensible | PER-05 | PER-05: 45 de 100 posibles | 45,0 | MEDIO |
| P5 Ambas contradicen lo verificado | Crítico | PER-02, PER-02 | 40 + 40 = 80 de 100 posibles | 80,0 | ALTO |
| P6 Teléfonos repetidos al registrar, referencias favorables | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |

Avisos esperados: P2 → promedio menor a 3; P4 → menos de 2 referencias (confirmar plazo); P6 → mismo teléfono, menos de 10 dígitos y teléfono igual al del candidato.
```

---

## Prompt 4 – Campos calculados, integración y prueba de punta a punta

```
1. Llena los campos que lee la plantilla (al guardar y antes de generar el Word): tablas referencias_declaradas, referencias (con r_numero, r_promedio, r_calificacion), hallazgos; srv_puntaje, srv_formula, sem_*, per_promedio_general, ref_nota.
2. Entrega el resultado al motor del estudio como servicio "Validación personal", peso 4 % en el Avanzado, separado de académica (6 %) y laboral (10 %). No tiene críticos. No modifiques las reglas del modelo v1.8.
   Pruebas (cargo Estándar, demás servicios en 0): PER-01 (70) → ponderado 2,8 → piso A → MEDIO, puntaje_final 31. PER-02 (40) → ponderado 1,6 → BAJO.
3. Avisos al guardar: los de validar() de motor_per.py; solo "Falta la criticidad del cargo" bloquea.
4. Prueba de punta a punta: estudio Avanzado de prueba con 4 referencias inventadas; la 2 "no contesta", se validan la 1 y la 3; la 3 dice que el evaluado trabaja en otra empresa (No coincide, PER-02).
   Verifica en el Word: la sección 1 muestra las 4 sin teléfonos; la sección 5 tiene dos fichas "REFERENCIA PERSONAL N° 1/2"; la sección 3 muestra PER-02 con 40; el semáforo marca MEDIO "▲ 40,0 puntos"; no queda "${" sin resolver.
5. Corre todas las pruebas del proyecto (los cuatro servicios) y reporta si algo se rompió.

Al final: archivos creados y modificados, pruebas agregadas y puntos pendientes.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si estos prompts y `motor_per.py` no coinciden, manda `motor_per.py`. Si el diccionario y la plantilla no coinciden, manda la plantilla (y avisar).
- **Privacidad:** los teléfonos de las referencias son datos de terceros: se usan para validar y llamar, pero nunca se imprimen en el informe.
- **Datos reales:** las pruebas usan datos inventados.
