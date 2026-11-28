# Prompts para Cursor – Formato de Verificación de antecedentes financieros con calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.0, 06/10/2026
**Requisito previo:** Antecedentes legales, Verificación académica, Verificación laboral y Referencias personales ya implementados. Este servicio sigue el mismo patrón y debe reutilizar lo construido (catálogo configurable, servicio de calificación, campos calculados, avisos y entrega al motor).

**Archivos que acompañan estos prompts (ponerlos en `docs/financiero/`):**

| Archivo | Para qué sirve |
|---|---|
| `1_Formato_Financiero.docx` | Plantilla Word con todos los marcadores |
| `2_Diccionario_marcadores_Financiero.xlsx` | Campos, tablas, listas, siglas, catálogo, cálculos, umbrales y casos de prueba |
| `motor_fin.py` | Motor de referencia en Python (especificación ejecutable; el Java debe dar los mismos resultados) |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen.

**Lo propio de este servicio:**
- **Un solo formato** reemplaza los cuatro anteriores (síntesis, verificación, especial sin reporte y OEA).
- **Fuente general:** la consulta de **síntesis** de centrales de riesgo (siempre). **Solo OEA:** además el **reporte detallado** y la consulta de propietarios.
- Las secciones **8 (obligaciones con reporte negativo) y 9 (declaración de renta y patrimonio)** solo aparecen en estudios OEA. El número del concepto final cambia (10 en OEA, 8 en los demás).
- En la plantilla y en la interfaz se dice **"centrales de riesgo"**, nunca el nombre del proveedor.
- El score es **informativo**: no suma puntos.
- No hay códigos críticos.

---

## Prompt 0 – Revisar lo que ya existe (no escribir nada todavía)

```
Vamos a construir el servicio "Verificación de antecedentes financieros" del estudio de seguridad, con el mismo patrón de Antecedentes, Académica, Laboral y Referencias personales. No modifiques ningún archivo en este paso.

1. Muéstrame lo reutilizable: catálogo configurable, servicio de calificación base, nivel y semáforo, avisos, entrega al motor, servicio "Financiero" (15 % en el Avanzado).
2. Dime cómo sabe la plataforma si un estudio es OEA (tipo de estudio) y si existe un estudio OEA anterior del mismo evaluado.
3. Dime cómo se adjuntan hoy archivos a un estudio y cómo se generaría un enlace de descarga para el informe (la consulta de síntesis y, en OEA, el reporte detallado).
4. Revisa el generador de Word con la plantilla docs/financiero/1_Formato_Financiero.docx y confirma:
   a) que repeatDocPart funciona cuando la lista está VACÍA (el bloque debe desaparecer completo, sin dejar título ni tabla);
   b) que repeatDocPart funciona cuando el rango del comentario empieza en un párrafo y termina DENTRO de una tabla, y cuando el rango es una tabla completa de una fila (así se arman las secciones 8 y 9: tablas seguidas que se ven como una sola);
   c) que el mismo nombre de lista (tablas['oea']) puede usarse en tres comentarios distintos.
   Si algo de esto no funciona, NO cambies la plantilla: explícame qué falla y propón la solución.

Lee docs/financiero/2_Diccionario_marcadores_Financiero.xlsx y docs/financiero/motor_fin.py.

Nota: el módulo del candidato está en actualización. Si sus nombres de campos cambian, se mapean a las claves del diccionario; no se cambian las claves del formato.

Al final dame la propuesta de clases nuevas y reutilizadas, y las dudas.
```

---

## Prompt 1 – Catálogo, bandas del score y siglas

```
1. Crea el catálogo de "Antecedentes financieros" con el mismo mecanismo de configuración:

| Código | Descripción | Puntos | Acumula |
|---|---|---|---|
| FIN-01 | Obligación en cartera castigada sin pagar, o con mora vigente superior a 180 días | 60 | Por obligación (OEA); una vez con la síntesis |
| FIN-02 | Obligación con mora vigente de 90 a 180 días | 45 | Por obligación (OEA); una vez con la síntesis |
| FIN-03 | Moras de 30 a 89 días repetidas: 3 o más meses con mora 30 o mora 60 en el último año | 25 | Una vez por estudio |
| FIN-04 | Endeudamiento alto: la utilización del ingreso estimado frente a la cuota mensual supera el 70 % | 40 | Una vez por estudio |
| FIN-05 | Proceso ejecutivo o embargo vigente | 50 | Una vez por estudio |
| FIN-06 | Codeudor de una obligación en mora (estudios OEA, con el reporte detallado) | 20 | Por obligación |
| FIN-07 | Inconsistencia patrimonial: bienes declarados que no aparecen en los registros, bienes registrados que no declaró, o cambio patrimonial frente al estudio anterior que no se explica con sus ingresos, sin explicación razonable (estudios OEA) | 30 | Una vez por estudio |
| FIN-08 | Ocultamiento financiero: negó u omitió deudas, moras, reportes o embargos que aparecen en centrales de riesgo (poligrafía, visita domiciliaria o formulario del evaluado) | 40 | Una vez por estudio |

ALR-FIN-01 (score en la banda más baja sin ningún código FIN) es una alerta: no suma.
Reutiliza los umbrales del modelo v1.8.

2. Crea una configuración "Centrales de riesgo" con, por cada central: nombre (solo interno) y sus 4 bandas del indicador de pago (desde, hasta, nombre). Valores iniciales de la central actual: 150–400 Muy bajo, 401–500 Bajo, 501–600 Medio, 601–950 Alto. El nombre de la central NUNCA se imprime.

3. Carga la lista de siglas de tipos de cuenta (hoja Siglas del diccionario; el área de operaciones completará la lista desde el documento de apoyo).

Prueba: 8 códigos cargados; bandas configurables; ninguna etiqueta visible al cliente dice el nombre del proveedor.
```

---

## Prompt 2 – Formulario del servicio

```
Configura el formulario con EXACTAMENTE las claves del diccionario (hojas Campos, Tablas y Listas).

1. Bloque "Consulta de síntesis" (todos los estudios):
   - cr_fecha, cr_mayor_mora (lista);
   - tabla fija de 4 sectores x 6 columnas: s_{fin,real,coop,tel}_{vig,cer,pri,cod,cuota,mora}. Cuota y saldo en mora se registran EN MILES, como los muestra la consulta;
   - fin_meses_mora_90 y fin_meses_mora_30_60 (números 0–12, NO se imprimen) con esta ayuda en pantalla: "Cuente en la barra de comportamiento de los últimos 12 meses";
   - fin_comportamiento (texto largo);
   - fin_ingreso (miles, con opción "No reportado") y fin_endeudamiento (%);
   - cr_score y cr_probabilidad.
2. Bloque "Consolidado financiero" (todos los estudios): c_{ing,deu,mor,bie}_{cr,poli,visita,res} y c_observaciones. Límite de 120 caracteres por casilla y 300 en observaciones.
   - c_*_poli: precarga desde la poligrafía (Obligaciones al día → Deudas y cuotas; Obligaciones en mora → Moras y reportes; Bienes patrimoniales → Bienes; Ingresos → "No se pregunta"). Si el estudio no incluye poligrafía: "No incluido en el estudio".
   - c_*_visita: texto del analista por ahora. Si el estudio no incluye visita: "No incluido en el estudio".
3. fin_ejecutivo (Sí/No) + fin_ejecutivo_detalle: precarga "Sí" si Antecedentes registró un proceso ejecutivo o embargo vigente.
4. Bloque OEA (visible solo si el estudio es OEA):
   - tabla obligaciones_negativas: o_entidad, o_tipo (siglas), o_calidad, o_estado, o_dias, o_valor (miles);
   - tabla patrimonios: pt_tipo, pt_ubicacion, pt_matricula, pt_estado, pt_explicacion. Si existe un estudio OEA anterior del mismo evaluado, precarga sus bienes con pt_estado vacío y llena oea_estudio_anterior con "N° y fecha"; si no existe, "Primer estudio";
   - oea_observaciones, oea_inconsistencia (Sí/No) + detalle.
5. Adjuntos: la consulta de síntesis (obligatoria) y, en OEA, el reporte detallado (obligatorio). cr_reporte_enlace = enlace(s) de descarga.
6. Calculados de solo lectura: s_tot_*, cr_cuotas, cp_ref, cr_salvedad, fin_endeudamiento_ref, cr_banda_*, cr_m*, o_dias_mora, o_valor_mora, o_ref, pt_novedad, oea_negativas_nota, sec_concepto, hallazgos, srv_formula, srv_puntaje, sem_*.
7. ev_criticidad: la misma del estudio; no se imprime.

No uses "Califica riesgo" del constructor; califica el servicio del Prompt 3.

Prueba: las claves impresas del formulario coinciden con los marcadores de docs/financiero/1_Formato_Financiero.docx; los campos "No se imprime" del diccionario no aparecen en el Word.
```

---

## Prompt 3 – Servicio de calificación

```
Crea el servicio de calificación de Antecedentes financieros, traducción fiel de calificar() y validar() de docs/financiero/motor_fin.py.

Reglas:
1. Totales: s_tot_* = suma de los 4 sectores; cr_cuotas = "$" + s_tot_cuota + " mil".
2. Estudio NO OEA (solo síntesis): fin_meses_mora_90 ≥ 5 → FIN-01; de 1 a 4 → FIN-02 (una sola fila, h_fuente "Consulta de síntesis").
3. Estudio OEA: por cada obligación negativa, o_ref = FIN-06 si es "Codeudor y otros"; FIN-01 si está "Castigada" o tiene más de 180 días; FIN-02 si tiene de 90 a 180 días; "—" si es "Saldada castigada" o tiene menos de 90 días. Una fila de hallazgo por código (h_fuente "Reporte detallado"). En OEA la barra NO genera FIN-01/02.
4. FIN-03 si fin_meses_mora_30_60 ≥ 3 (todos los estudios).
5. FIN-04 si fin_endeudamiento > 70; si fin_ingreso = "No reportado", no aplica.
6. FIN-05 si fin_ejecutivo = Sí. FIN-08 si algún c_*_res = "No coincide – omitió u ocultó (FIN-08)" (una vez). FIN-07 si es OEA y oea_inconsistencia = Sí.
7. Indicador de pago: bandas de la central configurada; "▲" en la banda del score; fila informativa automática en la sección 4 ("Indicador de pago (score)" | "512 – banda Medio" | "Informativo: no suma puntos.").
8. Secciones OEA: tablas oea = [1 elemento] si es OEA, si no lista vacía; sec_concepto = 10 u 8; cr_salvedad = texto de alcance si no es OEA (ver diccionario).
9. Puntaje = suma con tope 100; nivel por umbrales; fórmula y semáforo como en los demás servicios.

Pruebas obligatorias (CASOS de motor_fin.py):

| Caso | Criticidad | OEA | Hallazgos | Fórmula | Puntaje | Nivel |
|---|---|---|---|---|---|---|
| F1 Sin novedades | Estándar | No | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| F2 Tres meses con mora 30 o 60 | Estándar | No | FIN-03 | FIN-03: 25 de 100 posibles | 25,0 | BAJO |
| F3 Mora 90 o más en 2 meses + moras repetidas | Estándar | No | FIN-02, FIN-03 | 45 + 25 = 70 de 100 posibles | 70,0 | ALTO |
| F4 Mora 90 o más en 6 meses seguidos | Sensible | No | FIN-01 | FIN-01: 60 de 100 posibles | 60,0 | ALTO |
| F5 Endeudamiento 78 % | Estándar | No | FIN-04 | FIN-04: 40 de 100 posibles | 40,0 | MEDIO |
| F6 Embargo vigente + ocultó la mora | Crítico | No | FIN-02, FIN-05, FIN-08 | 45 + 50 + 40 = 135 → 100 (tope máximo) | 100,0 | ALTO |
| F7 OEA: castigada + codeudor en mora + patrimonio inconsistente | Estándar | Sí | FIN-01, FIN-06, FIN-07 | 60 + 20 + 30 = 110 → 100 (tope máximo) | 100,0 | ALTO |
| F8 Score muy bajo sin códigos | Estándar | No | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| F9 OEA sin reportes negativos, primer estudio | Sensible | Sí | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |

Avisos esperados: F8 → ALR-FIN-01. Revisa también los demás avisos de validar() (mora 90 sin saldo en mora, saldo en mora sin meses en mora, OEA con saldo en mora sin obligaciones registradas, bien "Nuevo" o "Ya no aparece" sin explicación).
```

---

## Prompt 4 – Campos calculados, integración y prueba de punta a punta

```
1. Llena los campos que lee la plantilla (al guardar y antes de generar el Word): todos los calculados del Prompt 2 y las tablas hallazgos, informativos, oea, obligaciones_negativas y patrimonios.
2. Entrega el resultado al motor del estudio como servicio "Financiero", peso 15 % en el Avanzado. No tiene críticos. No modifiques las reglas del modelo v1.8.
   Pruebas (cargo Estándar, demás servicios en 0): F3 (70) → ponderado 10,5 → piso A → MEDIO, puntaje_final 31. F5 (40) → ponderado 6,0 → BAJO.
3. Avisos al guardar: los de validar() de motor_fin.py; solo "Falta la criticidad del cargo" bloquea. ALR-FIN-01 pide confirmación antes de cerrar.
4. Prueba de punta a punta con dos estudios de prueba (datos inventados):
   a) Avanzado NO OEA: telcos con saldo en mora 386; 2 meses con mora 90 o más; 2 meses con mora 30 o 60; 38 %. Verifica en el Word: sección 3 con FIN-02 (45); semáforo MEDIO "▲ 45,0 puntos"; aparece la salvedad de alcance; NO aparecen las secciones 8 y 9; el concepto es la sección 8; no queda "${" sin resolver.
   b) OEA con estudio anterior: una obligación castigada y dos bienes (uno "Sin cambio" y uno "Nuevo" con explicación). Verifica: aparecen las secciones 8 y 9; la tabla de patrimonios muestra "Estudio anterior"; el concepto es la sección 10; NO aparece la salvedad.
5. Corre todas las pruebas del proyecto (los cinco servicios) y reporta si algo se rompió.

Al final: archivos creados y modificados, pruebas agregadas y puntos pendientes.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si estos prompts y `motor_fin.py` no coinciden, manda `motor_fin.py`. Si el diccionario y la plantilla no coinciden, manda la plantilla (y avisar).
- **Proveedor neutro:** ni la plantilla ni las etiquetas visibles llevan el nombre de la central de riesgo. Si cambia el proveedor, solo se ajustan sus bandas en la configuración.
- **Datos reales:** las pruebas usan datos inventados. Los reportes de centrales de riesgo son datos sensibles: se guardan como adjuntos del estudio con el mismo control de acceso del informe.
- **Pendiente para la visita domiciliaria:** cuando exista el formato nuevo, precargar c_*_visita igual que c_*_poli.
