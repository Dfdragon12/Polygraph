# Prompts para Cursor – Prueba de poligrafía pre-empleo con calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.0, 22/10/2026
**Requisito previo:** Antecedentes, Académica, Laboral, Referencias, Financiero, Visita domiciliaria e Informe de análisis final ya implementados. Este servicio sigue el mismo patrón (catálogo configurable, servicio de calificación, campos calculados, avisos, entrega al motor del estudio).

**Archivos que acompañan estos prompts (ponerlos en `docs/poligrafia/`):**

| Archivo | Para qué sirve |
|---|---|
| `1_Formato_Poligrafia_PreEmpleo.docx` | Plantilla Word con todos los marcadores (diseño del formato actual de Polygraph) |
| `2_Diccionario_marcadores_Poligrafia_PreEmpleo.xlsx` | Campos, tablas, listas, catálogo, **otros catálogos**, **pantalla del poligrafista**, cálculos, umbrales y casos de prueba |
| `motor_pol.py` | Motor de referencia en Python (especificación ejecutable; el Java debe dar los mismos resultados) |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen.

**Lo propio de este servicio (leer antes de empezar):**
- **El poligrafista nunca escoge un código.** Avanza por las secciones de su formato y responde Sí/No. Si es Sí, escribe su texto como hoy y responde 1 a 3 listas cortas (cuánto, cuántas veces, cuándo, tiempo de mora…). La plataforma calcula el código y lo muestra al lado ("→ POL-05 · 50 puntos"). El texto se copia solo a "Admisiones" (viñetas). Hoja **Pantalla del poligrafista**.
- **Resultado de la prueba:** cada pregunta relevante es Veraz, No veraz o Inconclusa; la plataforma calcula el resultado general (Veraz, Veraz con información, No veraz, No opinión, Sin examen).
- **Preguntas relevantes:** llegan con las que el cliente escogió del banco; el poligrafista puede modificarlas y queda registro (qué, quién, cuándo).
- **Precarga del link del candidato:** datos personales, estudios, experiencia laboral (con llamados de atención declarados), 2 preguntas financieras y 3 de antecedentes.
- **Regla del servicio no contratado:** si el estudio incluye antecedentes, laboral o financiero, lo que el evaluado declara sobre procesos, medidas, fechas de empleo o deudas lo califica ese servicio (aquí solo aviso). Si el servicio es ÚNICAMENTE poligrafía, la poligrafía lo califica con los códigos LEG, LAB-15 y FIN de la hoja **Otros catálogos**, marcados "declarado por el evaluado, sin verificación en fuentes".
- **Las conductas del catálogo POL** (robos, alteración, consumo, abandono, etc.) siempre suman en la poligrafía, aunque otro servicio también las encuentre.
- **Excepción aprobada:** POL-05 (robo de menos de un salario mínimo, una o dos veces) suma 50 en vez de ser crítico. Debe quedar en el modelo v1.9.
- **Datos sensibles:** salud, historia personal, datos de terceros y de menores no suman ni se imprimen (categoría "Personal o de salud"). Se califica la conducta laboral, nunca un diagnóstico.

---

## Prompt 0 – Revisar lo que ya existe (no escribir nada todavía)

```
Vamos a construir el servicio "Prueba de poligrafía" (pre-empleo) con el mismo patrón de los demás servicios. No modifiques ningún archivo en este paso.

1. Muéstrame lo reutilizable: catálogo configurable, servicio de calificación base, semáforo, avisos entre servicios, entrega al motor del estudio
   (servicio "Prueba de poligrafía", 25 % y obligatorio en el Avanzado) y el registro de los servicios contratados en cada estudio.
2. Dime cómo se guardan hoy los datos del link del candidato (estudios, empleos con fechas y motivo de retiro, llamados de atención, preguntas
   financieras y de antecedentes) y cómo los leería este servicio.
3. Revisa el generador de Word con docs/poligrafia/1_Formato_Poligrafia_PreEmpleo.docx y confirma:
   a) que repeatTableRow funciona con lista vacía (queda el encabezado de la tabla);
   b) que repeatDocPart inserta un cuadro por página en el anexo de la autorización;
   c) que el marcador ${['au_imagen']} recibe un objeto Image (bytes + ancho máximo) y no texto;
   d) si el generador puede cambiar el color de fondo de una celda según el valor (resultado Veraz/No veraz). Si no puede, se deja en texto.
   Si algo no funciona, NO cambies la plantilla: explícame qué falla y propón la solución.

Lee docs/poligrafia/2_Diccionario_marcadores_Poligrafia_PreEmpleo.xlsx (todas las hojas, en especial "Pantalla del poligrafista" y
"Otros catálogos") y docs/poligrafia/motor_pol.py. Al final dame la propuesta de clases nuevas y reutilizadas, y las dudas.
```

---

## Prompt 1 – Catálogos y listas

```
1. Crea el catálogo POL (hoja "Catálogo": 29 códigos agrupados A a G, puntos o "Crítico") y las alertas ALR-POL-01 a 05 (todas visibles al cliente),
   con la definición completa de POL-08 y POL-05 (DEFINICIONES en motor_pol.py).
2. Crea la tabla de "otros catálogos" (hoja "Otros catálogos"): códigos LEG, LAB-15 y FIN que la poligrafía usa solo cuando el servicio dueño
   no está contratado, con su servicio dueño, puntos y si suman por cada uno. Usa los MISMOS códigos y puntos de los catálogos de antecedentes,
   laboral y financiero que ya existen (no dupliques definiciones: referencia el catálogo del servicio dueño). LAB-15 (rotación, 30) es nuevo y
   también se agrega al catálogo laboral.
3. Carga las listas de la hoja "Listas": categorías de admisión, cuándo, tipo de antecedente, tiempo de mora, motivo de retiro, frecuencia de
   consumo, resultados de pregunta, estado del examen, técnicas (AFMGQT, Utah) y equipos (lista por confirmar con Polygraph).
Todo configurable, no constantes en código.
```

---

## Prompt 2 – Pantalla del poligrafista y selección de códigos

```
Construye el formulario del poligrafista siguiendo la hoja "Pantalla del poligrafista" (debe funcionar bien en computador y tableta):

1. Las secciones van en el mismo orden del formato. Los datos del link llegan precargados; el poligrafista los confirma.
2. Preguntas relevantes: precargadas con las del cliente; editables con registro de cambios. Por cada una: Veraz | No veraz | Inconclusa.
3. Cada pregunta de las secciones (robos, complicidad, alteración, despido, sobornos, consumo, falsificación, antecedentes, vínculos, deudas,
   contramedidas, instrucciones) es Sí/No. Con Sí se abre el texto y SOLO las listas de esa pregunta (1 a 3).
4. Al lado de cada respuesta muestra el código que calcula la plataforma, en tiempo real (por ejemplo "→ POL-05 · 50 puntos" o, en un estudio
   avanzado, "→ lo califica Antecedentes legales"). El poligrafista no puede escribir ni elegir códigos.
5. Botón "Agregar otra admisión": texto + tema (lista agrupada como el catálogo) + cuándo. Incluye la categoría "Personal o de salud (no se
   imprime)".
6. Cada respuesta con Sí crea su admisión (texto + categoría + listas) y el texto se copia a la sección Admisiones del informe, en viñetas.
   No se escribe dos veces.
7. Experiencia laboral: por empresa, motivo de retiro (lista); la permanencia en meses sale de las fechas del link.
8. Historia financiera: por deuda, tiempo de mora, embargo o cobro jurídico, y si es "gota a gota"; ingresos y cuotas mensuales.
9. Firma: el poligrafista sale de su usuario; técnica y equipo de listas; el número de preguntas relevantes se cuenta solo.
```

---

## Prompt 3 – Servicio de calificación

```
Traduce calificar() de docs/poligrafia/motor_pol.py a Java.

Entrada: campos del formulario, preguntas, admisiones (texto, categoría, cuándo, tipo, cantidad, mora, embargo), empleos (meses, motivo, años desde
el retiro) y servicios_contratados del estudio.
Salida: los campos y tablas del diccionario (preguntas, admisiones imprimibles, hallazgos, alertas), puntaje, nivel, si hay crítico, estado
(Cerrado | Pendiente) y avisos a otros servicios.

Reglas clave (hoja "Cálculos"):
- Resultado general: No veraz → POL-01 crítico; Inconclusa → No opinión (pendiente si se puede repetir); Sin examen → ALR-POL-03.
- Código por categoría y cuándo; crítico siendo menor de edad → POL-29; no crítico de hace más de 5 años → POL-29.
- Robos: menos de un salario mínimo y 1 o 2 veces → POL-05; si no → POL-04. Drogas: POL-21 / POL-22 / POL-29. Conducir bajo efectos: POL-27 si el
  cargo es de conducción, si no POL-26. Solo el más grave en robos y en drogas.
- Regla del servicio no contratado: LEG (suma por cada uno donde aplique), LAB-15 (rotación) y FIN (cada uno una vez), marcados como declarados.
- Cada código una vez (salvo los LEG que suman por cada uno), tope 100, crítico → 100 y ALTO.
Pruebas unitarias: los 23 casos de la hoja "Casos de prueba" deben dar exactamente esos resultados.
```

---

## Prompt 4 – Generación del informe y entrega

```
1. Llena la plantilla con el diccionario. La primera hoja siempre llega completa hasta las preguntas relevantes; no cambies estilos.
2. Anexo 2: la autorización firmada como imagen (un cuadro por página; si llega en PDF, convierte cada página en imagen).
3. Avisos a otros servicios (sin puntos aquí cuando el servicio está contratado): laboral, académica, antecedentes y financiero (consolidado,
   columna Poligrafía).
4. Entrega al motor del estudio como "Prueba de poligrafía" (25 %, obligatorio en el Avanzado). Agrega ALR-POL-01 a 05 al catálogo único de
   alertas con "Visible al cliente = Sí". No modifiques el modelo salvo la excepción documentada de POL-05.
5. Si el estudio es solo poligrafía, el servicio individual se califica con su propio semáforo (cobertura 100 %).
```

---

## Prompt 5 – Prueba de punta a punta

```
Con datos inventados:
a) Avanzado, cargo estándar, veraz con información: vínculo pasado + consumo ocasional hace 3 años → POL-20 + POL-22 = 60, MEDIO; deudas →
   ALR-POL-04 y aviso al financiero.
b) Sin examen (desistió): robo de baja cuantía + problemas por alcohol, cargo sensible → 90, ALTO; ALR-POL-03, 04 y 05.
c) Solo poligrafía: tres medidas correctivas y dos procesos terminados → LEG-18 + LEG-19 + LEG-19 = 60 con la nota de servicios no incluidos.
d) El mismo caso (c) en un estudio avanzado → 0 puntos en la poligrafía y aviso a antecedentes.
e) No veraz → ALTO, crítico.
En todos: no queda "${" sin resolver, la primera hoja llega hasta las preguntas, y lo marcado "Personal o de salud" no aparece en el Word.
Corre todas las pruebas del proyecto y reporta si algo se rompió.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si estos prompts y `motor_pol.py` no coinciden, manda `motor_pol.py`. Si el diccionario y la plantilla no coinciden, manda la plantilla (y avisar).
- **El poligrafista no escoge códigos.** Si no está de acuerdo con el código calculado, lo comenta al analista o al gestor.
- **Datos sensibles:** salud, historia personal, datos de terceros y de menores no suman ni se imprimen.
- **Datos reales:** las pruebas usan datos inventados.
