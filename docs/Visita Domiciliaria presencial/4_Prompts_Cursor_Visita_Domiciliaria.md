# Prompts para Cursor – Formato de Visita domiciliaria con módulos y calificación automática

**Para:** el ingeniero que programa el ERP de Polygraph (Java)
**Versión:** 1.0, 08/10/2026
**Requisito previo:** Antecedentes, Académica, Laboral, Referencias personales y Financiero ya implementados. Este servicio sigue el mismo patrón (catálogo configurable, servicio de calificación, campos calculados, avisos, entrega al motor).

**Archivos que acompañan estos prompts (ponerlos en `docs/visita/`):**

| Archivo | Para qué sirve |
|---|---|
| `1_Formato_Visita_Domiciliaria.docx` | Plantilla Word única con todos los marcadores y módulos |
| `2_Diccionario_marcadores_Visita_Domiciliaria.xlsx` | Campos, tablas, **módulos**, listas, catálogo, cálculos, umbrales y casos de prueba |
| `motor_vis.py` | Motor de referencia en Python (especificación ejecutable; el Java debe dar los mismos resultados) |

**Cómo usar estos prompts:** pegar uno a la vez en Cursor, en orden. No pasar al siguiente hasta que el anterior compile y sus pruebas pasen.

**Lo propio de este servicio (leer antes de empezar):**
- **Un solo formato con módulos opcionales.** La sección fija es igual para todas las visitas; los módulos aparecen solo si están activos.
- **Los módulos los activa el visitador** en la plataforma, al iniciar la visita. La solicitud del estudio puede dejarlos sugeridos (por ejemplo, un estudio OEA llega con OEA marcado), pero la decisión final queda en el formulario del visitador:
  - **Clase de visita:** Pre-empleo o Rutina. Rutina activa dos cuadros: cambios frente a la visita anterior y vinculación laboral actual.
  - **Tipo de visita:** Presencial o Virtual. Virtual activa la ubicación obligatoria (georreferenciación).
  - **OEA, Teletrabajo, Vehicular:** casillas Sí/No, combinables entre sí y con cualquier clase.
- **Cada módulo trae sus anexos de fotos.** Las fotos de la vivienda van siempre; las de **zona de parqueo** solo con Vehicular; las del **lugar de teletrabajo** (20 fotos) solo con Teletrabajo. Si el módulo no está activo, su grupo de fotos no se pide ni aparece.
- **Salud y la pregunta 10 (sindical)** NO las activa el visitador: se manejan por configuración del sistema (decisión jurídica pendiente). Hoy están apagadas. Nunca suman ni generan alertas.
- **El visitador diligencia directamente en la plataforma** (debe funcionar bien en celular o tableta).
- **Precarga del módulo del candidato:** ciudad, barrio, dirección, adicionales, estrato, estado civil, estudios y experiencia laboral. Las referencias personales NO se precargan.
- **No calificar dos veces:** lo que la visita confirma y califica otro servicio pasa a ese servicio y aquí no suma.

---

## Prompt 0 – Revisar lo que ya existe (no escribir nada todavía)

```
Vamos a construir el servicio "Visita domiciliaria" con el mismo patrón de los demás servicios. No modifiques ningún archivo en este paso.

1. Muéstrame lo reutilizable: catálogo configurable, servicio de calificación base, nivel y semáforo, avisos, entrega al motor, servicio "Visita domiciliaria" (15 % en el Avanzado).
2. Dime cómo se agenda hoy una visita y si existe un registro de intentos (fecha, modalidad, resultado). Lo necesitamos para VIS-04.
3. Dime cómo se suben archivos e imágenes a un estudio desde un celular.
4. Revisa el generador de Word con docs/visita/1_Formato_Visita_Domiciliaria.docx y confirma:
   a) que repeatDocPart funciona con lista VACÍA (el módulo desaparece completo, sin dejar título ni tabla);
   b) que una misma lista (por ejemplo tablas['rutina'] o tablas['oea']) puede usarse en dos o tres comentarios distintos;
   c) que repeatTableRow funciona sobre una sola fila dentro de una tabla fija (la pregunta 10 del protocolo);
   d) que puede insertar IMÁGENES en el marcador ${['ft_imagen']} de las fotos (si no puede, propón cómo: por ejemplo, un objeto imagen en vez de texto).
   Si algo no funciona, NO cambies la plantilla: explícame qué falla y propón la solución.

Lee docs/visita/2_Diccionario_marcadores_Visita_Domiciliaria.xlsx (todas las hojas, en especial "Módulos") y docs/visita/motor_vis.py.

Al final dame la propuesta de clases nuevas y reutilizadas, y las dudas.
```

---

## Prompt 1 – Catálogo, alertas y configuración

```
1. Crea el catálogo de "Visita domiciliaria" con el mismo mecanismo de configuración (11 códigos, todos "una vez por estudio"):

| Código | Descripción | Puntos |
|---|---|---|
| VIS-01 | No reside en la dirección declarada, o la ubicación de la visita virtual no corresponde, sin explicación | 70 |
| VIS-02 | Vínculo (relación, colaboración o negocios) con personas al margen de la ley o grupos armados ilegales | 70 |
| VIS-03 | Arma de fuego sin salvoconducto en el hogar | 50 |
| VIS-04 | Visita no realizada por causa del candidato: dos intentos fallidos o negativa a recibirla | 45 |
| VIS-05 | Consumo actual de sustancias psicoactivas declarado | 40 |
| VIS-06 | Economía desequilibrada: los egresos superan los ingresos sin explicación | 35 |
| VIS-07 | Frecuenta juegos de azar con impacto en su economía | 35 |
| VIS-08 | Falta de colaboración en la visita (se niega a responder, a mostrar espacios o a las fotos, o trato irrespetuoso) | 35 |
| VIS-09 | Recibe la visita bajo efectos aparentes de alcohol o sustancias | 50 |
| VIS-10 | Teletrabajo: no garantiza la seguridad de la información | 35 |
| VIS-11 | Vehicular: el parqueadero no ofrece seguridad para el vehículo | 35 |

2. Crea las 9 alertas ALR-VIS-01 a ALR-VIS-09 (textos en la hoja Catálogo). No suman; el analista debe revisarlas y cerrarlas antes de entregar.
3. Agrega a la configuración del sistema dos interruptores: "Mostrar estado de salud en la visita" y "Mostrar pregunta 10 (sindical)". Valor inicial: apagado. Solo un administrador los cambia.

Prueba: 11 códigos y 9 alertas cargados; los interruptores existen y están apagados.
```

---

## Prompt 2 – Activación de módulos por el visitador

```
En el formulario de la visita, al inicio, crea el bloque "Configuración de la visita" que diligencia el VISITADOR:

- Tipo de visita (v_tipo): Presencial | Virtual.
- Clase de visita (v_clase): Pre-empleo | Rutina.
- Módulos adicionales: OEA (mod_oea), Teletrabajo (mod_teletrabajo), Vehicular (mod_vehicular), cada uno Sí/No.

Reglas:
1. Si la solicitud del estudio trae el módulo (por ejemplo, estudio OEA o cargo con vehículo), déjalo marcado por defecto; el visitador puede cambiarlo.
2. Al activar un módulo se muestran sus campos y su grupo de fotos; al desactivarlo se ocultan (no se borran mientras la visita esté abierta).
3. v_modulos (impreso) = clase (con "OEA " delante si mod_oea = Sí) + " · Teletrabajo" + " · Vehicular" según corresponda. Ej.: "OEA rutina · Teletrabajo". Nunca vacío.
4. Listas del generador: rutina, virtual, vehicular, oea, oea_bd, teletrabajo, fotos_vehicular, fotos_teletrabajo con elementos solo si el módulo está activo; si no, vacías. salud y ps_sindical según los interruptores del Prompt 1.

Prueba: una visita sin módulos genera el Word sin ningún bloque "MÓDULO…" y sin los subtítulos de fotos de parqueo y teletrabajo; activando cada módulo, aparece solo el suyo.
```

---

## Prompt 3 – Formulario del visitador

```
Configura el formulario con EXACTAMENTE las claves del diccionario (hojas Campos, Tablas, Módulos y Listas). Debe funcionar en celular.

1. Precarga:
   - dc_*_reg y dc_*_vis desde el módulo del candidato (ciudad, barrio, dirección, adicionales, estrato, estado civil). El visitador corrige dc_*_vis.
   - estudios y empleos (una ficha por empresa) desde el módulo del candidato; el visitador marca Coincide / Corregido y completa l_funciones.
   - v_salario y ev_cargo desde la solicitud. En Rutina: rv_*_emp desde lo que reportó la empresa en la solicitud; rc_*_ant desde la visita anterior del mismo colaborador.
   - En OEA: oea_bd (paso 2) desde lo que registre el analista de Polygraph antes de la visita. Es CONFIDENCIAL: el candidato no debe verlo.
2. Familia: tres cuadros obligatorios (familia_origen, familia_pareja, familia_convive). No deja cerrar si alguno está vacío; si no aplica, una fila "No tiene" y su observación.
3. Protocolo de seguridad: respuesta Sí/No por pregunta; si es Sí, el detalle es obligatorio y aparecen las preguntas de precisión (ps_3/4/5_quien, ps_6_salvoconducto, ps_7_impacto, ps_9_tipo, ps_15_tipo, ps_17_actual).
4. Conducta durante la visita (cf_conducta_lista): selección múltiple con las opciones de la hoja Listas.
5. Gestión de la visita (tabla intentos): cada intento con fecha, modalidad, resultado y observación. Se registra desde la agenda.
6. Fotos: un cargador por grupo (vivienda siempre; parqueo con Vehicular; teletrabajo con Teletrabajo), cada foto con descripción.
7. Egresos: un gasto compartido se registra una sola vez (monto total) y en eg_aportantes quién paga y cuánto. El aporte del candidato se suma desde ahí.
8. Calculados de solo lectura: dc_*_res, v_modulos, ps_*_ref, cf_conducta, porcentajes y conceptos de las escalas, perfil_pct, perfil_concepto, tt_seg, vh_concepto_seg, oea_col_*, oea_intervalo, hallazgos, informativos, srv_formula, srv_puntaje, sem_*.

Prueba: las claves impresas coinciden con los marcadores de la plantilla; los campos "No se imprime" no aparecen en el Word.
```

---

## Prompt 4 – Servicio de calificación

```
Crea el servicio de calificación de la visita, traducción fiel de calificar() y validar() de docs/visita/motor_vis.py (reglas detalladas en la hoja Cálculos).

Pruebas obligatorias (CASOS de motor_vis.py):

| Caso | Criticidad | Hallazgos | Fórmula | Puntaje | Nivel |
|---|---|---|---|---|---|
| V1 Sin novedades | Estándar | — | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| V2 Economía desequilibrada | Estándar | VIS-06 | VIS-06: 35 de 100 posibles | 35,0 | MEDIO |
| V3 No reside en la dirección declarada | Estándar | VIS-01 | VIS-01: 70 de 100 posibles | 70,0 | ALTO |
| V4 Arma sin salvoconducto + consumo actual | Sensible | VIS-03, VIS-05 | 50 + 40 = 90 de 100 posibles | 90,0 | ALTO |
| V5 Contactado como víctima + arma con salvoconducto | Estándar | — (ALR-VIS-04, ALR-VIS-06) | Sin hallazgos que sumen puntos | 0,0 | BAJO |
| V6 Dos intentos fallidos por causa del candidato | Crítico | VIS-04 | VIS-04: 45 de 100 posibles | 45,0 | ALTO |
| V7 Falta de colaboración y bajo efectos aparentes | Estándar | VIS-08, VIS-09 | 35 + 50 = 85 de 100 posibles | 85,0 | ALTO |
| V8 Teletrabajo: red compartida + condiciones con observaciones | Estándar | VIS-10 (y ALR-VIS-05) | VIS-10: 35 de 100 posibles | 35,0 | MEDIO |
| V9 Vehicular: parqueadero inseguro | Estándar | VIS-11 | VIS-11: 35 de 100 posibles | 35,0 | MEDIO |
| V10 Vínculo con personas al margen de la ley | Estándar | VIS-02 | VIS-02: 70 de 100 posibles | 70,0 | ALTO |

Las alertas van a la tabla informativos (sección 4) con el texto del catálogo y su código.
```

---

## Prompt 5 – Lo que pasa a otros servicios, integración y prueba de punta a punta

```
1. Envío a otros servicios (sin puntos en la visita):
   - estudios con Resultado "Corregido" → aviso en la verificación académica;
   - empleos "Corregido" y preguntas 1, 13 y 14 en Sí → aviso en la verificación laboral;
   - ingresos, deudas, reportes (eco_reportado_*) y patrimonio → columna "Visita domiciliaria" del consolidado financiero (c_*_visita);
   - patrimonio en OEA → servicio financiero (FIN-07);
   - preguntas 3, 4 y 5 cuando es el candidato → aviso en Antecedentes.
2. Entrega al motor del estudio como servicio "Visita domiciliaria", peso 15 % en el Avanzado. No tiene críticos. No modifiques las reglas del modelo v1.8.
   Pruebas (cargo Estándar, demás servicios en 0): V3 (70) → ponderado 10,5 → piso A → MEDIO, puntaje_final 31. V2 (35) → ponderado 5,25 → BAJO.
3. Avisos al guardar y al cerrar: los de validar(); al cerrar, mínimo de fotos por grupo activo (3 vivienda, 2 parqueo, 20 teletrabajo).
4. Prueba de punta a punta con datos inventados:
   a) Pre-empleo presencial sin módulos, con VIS-06: el Word no muestra ningún módulo; semáforo MEDIO "▲ 35,0 puntos".
   b) OEA rutina virtual con teletrabajo y vehicular: aparecen los módulos rutina (2 bloques), virtual, vehicular, OEA (con su paso 2 y el análisis de variación con los años de cada estudio) y teletrabajo, y en la sección 12 los tres grupos de fotos.
   c) Visita no realizada (dos intentos "No atendió"): VIS-04 y secciones "No evaluable".
   En los tres: no queda "${" sin resolver.
5. Corre todas las pruebas del proyecto (los seis servicios) y reporta si algo se rompió.
```

---

## Notas para el ingeniero

- **Fuente de verdad:** si estos prompts y `motor_vis.py` no coinciden, manda `motor_vis.py`. Si el diccionario y la plantilla no coinciden, manda la plantilla (y avisar).
- **Módulos:** los activa el visitador; la solicitud solo los sugiere. Salud y sindical: solo configuración.
- **Fotos:** vivienda siempre; parqueo y teletrabajo solo con su módulo. No pueden aparecer menores de edad.
- **Confidencialidad OEA:** el paso 2 (patrimonio en bases de datos) nunca se muestra al candidato.
- **Datos sensibles:** salud y pertenencia sindical no se usan para calificar ni generar alertas.
- **Datos reales:** las pruebas usan datos inventados.
