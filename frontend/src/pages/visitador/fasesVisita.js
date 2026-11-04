// VD-01 · Las 31 secciones de la plantilla «Visita Domiciliaria presencial» se reagrupan en 3 fases y 10 temas, en el orden real de la visita.
// Los datos que se guardan son los mismos: solo cambia en qué lugar de la pantalla se capturan. `s` = clave de la sección; `cols` = solo esas
// columnas (la sección se reparte entre varios temas); `modulo` = nombre del módulo opcional (la sección es una tabla de UNA fila que existe
// solo si el módulo está activo); `soloLectura` = lo arma el sistema.

const COLS_RUTINA_CAMBIOS = [
  'rc_anterior', 'rc_viv_ant', 'rc_viv_act', 'rc_viv_cambio', 'rc_fam_ant', 'rc_fam_act', 'rc_fam_cambio', 'rc_edu_ant', 'rc_edu_act', 'rc_edu_cambio',
  'rc_ing_ant', 'rc_ing_act', 'rc_ing_cambio', 'rc_deu_ant', 'rc_deu_act', 'rc_deu_cambio', 'rc_pat_ant', 'rc_pat_act', 'rc_pat_cambio', 'rc_obs',
]
const COLS_RUTINA_VINCULACION = [
  'rv_ingreso_emp', 'rv_ingreso_vis', 'rv_ingreso_res', 'rv_contrato_emp', 'rv_contrato_vis', 'rv_contrato_res', 'rv_cargo_emp', 'rv_cargo_vis', 'rv_cargo_res',
  'rv_salario_emp', 'rv_salario_vis', 'rv_salario_res', 'rv_jefe_emp', 'rv_jefe_vis', 'rv_jefe_res', 'rv_horario', 'rv_relacion_jefe', 'rv_ambiente',
  'rv_llamados', 'rv_investigaciones', 'rv_obs',
]

export const FASES = [
  {
    id: 1, nombre: 'Antes de la visita',
    temas: [
      { id: 'generales', titulo: 'Datos generales', candidato: true, partes: [{ s: 'datos_generales' }] },
      { id: 'gestion', titulo: 'Gestión de la visita', novedades: true, partes: [{ s: 'intentos' }] },
    ],
  },
  {
    id: 2, nombre: 'En la visita',
    temas: [
      {
        id: 'familia', titulo: '5. Familia',
        partes: [{ s: 'familia_origen' }, { s: 'familia_pareja' }, { s: 'familia_convive' }, { s: 'dinamica_familiar' },
          { s: 'rutina', cols: COLS_RUTINA_CAMBIOS, modulo: 'Rutina · cambios frente a la visita anterior' }],
      },
      { id: 'estudios', titulo: '6. Estudios', partes: [{ s: 'estudios' }, { s: 'estudios_obs' }] },
      {
        id: 'empleos', titulo: '7. Experiencia laboral',
        partes: [{ s: 'empleos' }, { s: 'empleos_obs' }, { s: 'rutina', cols: COLS_RUTINA_VINCULACION, modulo: 'Rutina · vinculación laboral actual' }],
      },
      {
        id: 'vivienda', titulo: '8. Sector y vivienda',
        partes: [{ s: 'sector_vivienda' }, { s: 'virtual', modulo: 'Virtual · ubicación' }, { s: 'vehicular', modulo: 'Vehicular · zona de parqueo' }],
      },
      {
        id: 'economia', titulo: '9. Economía del hogar',
        partes: [{ s: 'economia' }, { s: 'ingresos_hogar' }, { s: 'egresos_hogar' }, { s: 'patrimonio_fam' }, { s: 'deudas' },
          { s: 'oea_bd', modulo: 'OEA · paso 2 (confidencial)', confidencial: true }, { s: 'oea', modulo: 'OEA · paso 3 y análisis de variación' }],
      },
      { id: 'protocolo', titulo: '10. Protocolo de seguridad', partes: [{ s: 'protocolo' }, { s: 'teletrabajo', modulo: 'Teletrabajo' }] },
      { id: 'concepto', titulo: '11. Concepto final', partes: [{ s: 'concepto_final' }] },
      {
        id: 'fotos', titulo: '12. Fotos',
        partes: [{ s: 'fotos' }, { s: 'fotos_vehicular', modulo: 'Vehicular · fotos de la zona de parqueo' }, { s: 'fotos_teletrabajo', modulo: 'Teletrabajo · fotos del lugar' }],
      },
    ],
  },
  {
    id: 3, nombre: 'Revisión y envío',
    temas: [
      {
        id: 'revision', titulo: 'Revisión', informe: true,
        partes: [{ s: 'resultado_servicio', soloLectura: true }, { s: 'hallazgos', soloLectura: true }, { s: 'informativos' }, { s: 'anexos' }],
      },
    ],
  },
]

// VD-03 · Preguntas del protocolo de seguridad, con el texto del Word. `n` = clave interna (ps_N, no cambia); el número que se muestra es continuo.
export const PREGUNTAS_PROTOCOLO = [
  { n: 1, texto: "¿Ha tenido periodos de inactividad laboral iguales o superiores a un mes?" },
  { n: 2, texto: "¿Ha estado o está reportado actualmente en alguna central de información de riesgo financiero?" },
  { n: 3, texto: "¿Usted o algún miembro de su familia han estado implicados en procesos judiciales o penales?", precision: { clave: 'ps_3_quien', pregunta: '¿Quién?' } },
  { n: 4, texto: "¿Usted o algún miembro de la familia ha estado privado de la libertad?", precision: { clave: 'ps_4_quien', pregunta: '¿Quién?' } },
  { n: 5, texto: "¿Usted o algún miembro de la familia ha estado detenido o arrestado?", precision: { clave: 'ps_5_quien', pregunta: '¿Quién?' } },
  { n: 6, texto: "¿Usted o algún miembro de su familia tiene un arma de fuego y/o salvoconducto?", precision: { clave: 'ps_6_salvoconducto', pregunta: '¿Tiene salvoconducto?' } },
  { n: 7, texto: "¿Usted o algún miembro de su familia frecuenta lugares de juegos de azar, casinos o similares?", precision: { clave: 'ps_7_impacto', pregunta: '¿Afecta su economía?' } },
  { n: 8, texto: "¿Alguno de sus familiares ha laborado o labora actualmente para la empresa?" },
  { n: 9, texto: "¿Usted o algún miembro de su familia han tenido vínculos con personas al margen de la ley?", precision: { clave: 'ps_9_tipo', pregunta: '¿Vínculo o víctima?' } },
  { n: 11, texto: "¿Algún miembro de su familia se encuentra radicado fuera del país? ¿Por qué?" },
  { n: 12, texto: "¿Usted ha viajado alguna vez fuera del país?" },
  { n: 13, texto: "¿En alguno de los empleos anteriores ha sido despedido con justa causa o por inconvenientes laborales?" },
  { n: 14, texto: "¿En alguno de los empleos anteriores ha sido despedido o ha tenido que renunciar sin justa causa por inconvenientes personales?" },
  { n: 15, texto: "¿Por razones de trabajo o de actividad personal usted o su familia han sido contactados por personas pertenecientes a grupos armados ilegales o dedicados a actividades ilícitas?", precision: { clave: 'ps_15_tipo', pregunta: '¿Vínculo o víctima?' } },
  { n: 16, texto: "¿Se han presentado dificultades al interior de la familia relacionadas con el consumo de sustancias psicoactivas?" },
  { n: 17, texto: "¿Usted consume o ha consumido sustancias psicoactivas?", precision: { clave: 'ps_17_actual', pregunta: '¿Consumo actual o en el pasado?' } },
]

// Pregunta 10 (sindical): apagada por decisión jurídica, igual que en el Word (NumeracionProtocolo.SINDICAL_ACTIVA en el backend).
export const SINDICAL_ACTIVA = false

// Número que se muestra de cada pregunta: continuo (1 a 16 con la sindical apagada, 1 a 17 encendida). Mismo orden que el Word.
export function numerosProtocolo(sindical = SINDICAL_ACTIVA) {
  const r = {}
  let n = 0
  for (let p = 1; p <= 17; p += 1) {
    if (p === 10 && !sindical) continue
    n += 1
    r[p] = n
  }
  return r
}

// VD-04 · Indicadores con nombre, como en el Word. Escala de 1 a 4 (los textos de la guía no están en el diccionario: propuesta pendiente de confirmar).
export const ESCALA_INDICADORES = [
  { valor: '1', texto: 'Deficiente' }, { valor: '2', texto: 'Aceptable' }, { valor: '3', texto: 'Bueno' }, { valor: '4', texto: 'Muy bueno' },
]

// Por sección: prefijo de las claves (df_1 … df_N, df_pct, df_concepto) y nombre de cada indicador.
export const INDICADORES = {
  dinamica_familiar: { prefijo: 'df', titulo: 'Dinámica familiar', nombres: [
    "Aprendizaje continuo",
    "Compromiso familiar",
    "Manejo y solución de conflictos",
    "Liderazgo",
    "Trabajo en equipo",
    "Negociación y conciliación",
    "Proyecciones",
    "Canales de comunicación",
    "Expone pros y contras de situaciones",
    "Recursividad",
    "Manejo de la autoridad",
    "Tolerancia a la frustración",
  ] },
  sector_vivienda: { prefijo: 'hab', titulo: 'Aspecto habitacional', nombres: [
    "Estado, estructura física, limpieza y organización",
    "Condiciones del entorno",
    "Espacio según el número de habitantes",
  ] },
  economia: { prefijo: 'eco', titulo: 'Aspecto económico', nombres: [
    "Equilibrio económico (ingresos / egresos)",
    "Organización y planificación del presupuesto familiar",
    "Endeudamiento actual de la familia",
  ] },
  teletrabajo: { prefijo: 'tq', titulo: 'Teletrabajo', nombres: [
    "Capacidad de adaptación",
    "Condiciones ergonómicas",
    "Uso exclusivo del computador",
    "Cumplimiento de objetivos",
    "Condiciones ambientales",
    "Manejo y privacidad de contraseñas",
    "Autonomía",
    "Condiciones locativas",
    "Red privada",
    "Disciplina",
    "Ausencia de riesgos en el área",
    "Confidencialidad de la información",
  ] },
}

export const esClaveDeIndicador = (seccionClave, clave) => {
  const g = INDICADORES[seccionClave]
  return !!g && new RegExp(`^${g.prefijo}_(\\d+|pct|concepto)$`).test(clave)
}
export const esClaveCalculada = (seccionClave, clave) => {
  const g = INDICADORES[seccionClave]
  return !!g && (clave === `${g.prefijo}_pct` || clave === `${g.prefijo}_concepto`)
}

// Mismo cálculo que motor_vis.py: porcentaje = suma ÷ (4 × n) × 100 redondeado, solo si están los n indicadores. Concepto por rangos.
// Python redondea la mitad al par (round(62.5) = 62); Math.round sube. Igual que motor_vis.py.
export function redondear(x) {
  const piso = Math.floor(x)
  if (Math.abs(x - piso - 0.5) < 1e-9) return piso % 2 === 0 ? piso : piso + 1
  return Math.round(x)
}

export function calcularEscala(valores, n) {
  const v = valores.filter(x => /^[1-4]$/.test(String(x ?? '')))
  if (v.length < n) return { pct: null, concepto: null }
  const pct = redondear(v.reduce((a, b) => a + Number(b), 0) / (4 * n) * 100)
  const concepto = pct < 40 ? 'Desfavorable' : pct < 70 ? 'Favorable' : pct < 85 ? 'Bien favorable' : 'Muy favorable'
  return { pct, concepto }
}

// VD-06 · Las 38 preguntas Sí/No del módulo teletrabajo, en los seis grupos del Word. `n` = clave interna (tt_N).
export const GRUPOS_TELETRABAJO = [
  {
    titulo: "Antecedentes teletrabajo",
    preguntas: [
      { n: 1, texto: "¿Tiene experiencia previa en el teletrabajo?" },
      { n: 2, texto: "¿Realiza trabajos de manera independiente o informal?" },
      { n: 3, texto: "¿Utiliza sus horarios laborales para realizar actividades no productivas para la empresa?" },
      { n: 4, texto: "¿Maneja plataformas, aplicaciones y herramientas de telecomunicación?" },
      { n: 5, texto: "¿Tiene acceso a internet o telefonía?" },
    ],
  },
  {
    titulo: "Condiciones ergonómicas",
    preguntas: [
      { n: 6, texto: "¿Tiene un lugar de la vivienda destinado exclusivamente para trabajar?" },
      { n: 7, texto: "¿El espacio cumple con las dimensiones necesarias (mínimo 150 cm de ancho por 150 cm de alto)?" },
      { n: 8, texto: "¿Los elementos de trabajo se encuentran a menos de 25 cm de alcance?" },
      { n: 9, texto: "¿La ubicación del puesto permite movilidad de la silla (80 cm o más)?" },
      { n: 10, texto: "¿El escritorio permite ajustar el teclado y el mouse según las condiciones ergonómicas?" },
    ],
  },
  {
    titulo: "Condiciones ambientales",
    preguntas: [
      { n: 11, texto: "¿Cuenta con ventanas que permitan la iluminación natural?" },
      { n: 12, texto: "¿La cantidad de luz que ingresa es suficiente?" },
      { n: 13, texto: "¿La intensidad lumínica permite leer sin dificultad?" },
      { n: 14, texto: "¿La luz permite visualizar la pantalla sin fatiga visual por reflejos?" },
      { n: 15, texto: "¿Cuenta con ventilación natural?" },
      { n: 16, texto: "¿Tiene ventilación artificial?" },
      { n: 17, texto: "¿El ruido externo permite trabajar sin interferencia?" },
    ],
  },
  {
    titulo: "Condiciones locativas",
    preguntas: [
      { n: 18, texto: "¿Los pisos son planos y están en buenas condiciones?" },
      { n: 19, texto: "¿Está libre de obstáculos o desperdicios?" },
      { n: 20, texto: "¿El techo está en buenas condiciones?" },
      { n: 21, texto: "¿Las paredes están en buenas condiciones?" },
      { n: 22, texto: "¿Los cuadros, repisas y objetos anclados son seguros?" },
      { n: 23, texto: "¿Las entradas y salidas están libres de obstáculos?" },
      { n: 24, texto: "¿Hay buenas prácticas de orden, limpieza y aseo?" },
    ],
  },
  {
    titulo: "Riesgos",
    preguntas: [
      { n: 25, texto: "¿El área está libre de virus, bacterias, hongos, insectos y roedores?" },
      { n: 26, texto: "¿Hay contacto con residuos biológicos?" },
      { n: 27, texto: "¿Conoce los procedimientos en caso de emergencia?" },
      { n: 28, texto: "¿Cuenta con extintor y conoce su uso?" },
      { n: 29, texto: "¿Hay almacenamiento de líquidos o sólidos combustibles?" },
      { n: 30, texto: "¿Los equipos tienen conexión a tierra?" },
      { n: 31, texto: "¿Las instalaciones eléctricas están protegidas?" },
      { n: 32, texto: "¿Hay ausencia de empalmes o uniones en cables eléctricos?" },
      { n: 33, texto: "¿Los enchufes están sobrecargados de conexiones?" },
      { n: 34, texto: "¿Las cajas de interruptores están cubiertas?" },
    ],
  },
  {
    titulo: "Seguridad de la información",
    preguntas: [
      { n: 35, texto: "¿Usa una red privada (no pública ni compartida con terceros)?" },
      { n: 36, texto: "¿El computador es de uso exclusivo para el trabajo?" },
      { n: 37, texto: "¿Mantiene la privacidad de sus contraseñas?" },
      { n: 38, texto: "¿El espacio de trabajo evita que terceros vean o escuchen información de la empresa?" },
    ],
  },
]

export const TODOS_LOS_TEMAS = FASES.flatMap(f => f.temas.map(t => ({ ...t, fase: f.id })))

// Es la visita domiciliaria si trae las secciones que identifican esta plantilla.
export function esFormatoVisita(secciones = []) {
  const claves = new Set(secciones.map(s => s.clave))
  return ['datos_generales', 'intentos', 'protocolo', 'dinamica_familiar', 'sector_vivienda', 'fotos'].every(c => claves.has(c))
}

// Las partes de un tema con su sección y las columnas que le tocan. Un módulo está activo si su sección tiene su fila.
export function partesDelTema(tema, secciones) {
  const partes = []
  ;(tema.partes ?? []).forEach(p => {
    const seccion = secciones.find(s => s.clave === p.s && !s.origenFijoClave)
    if (!seccion) return
    const columnas = p.cols ? seccion.columnas.filter(c => p.cols.includes(c.clave)) : seccion.columnas
    if (columnas.length === 0) return
    // VD-04: los indicadores (y su porcentaje y concepto) se dibujan aparte, con su nombre y la escala.
    const columnasVista = columnas.filter(c => !esClaveDeIndicador(seccion.clave, c.clave) && !(seccion.clave === 'teletrabajo' && /^tt_\d+$/.test(c.clave)))
    partes.push({ seccion, columnas, columnasVista, modulo: p.modulo ?? null, confidencial: !!p.confidencial, soloLectura: !!p.soloLectura, activo: !p.modulo || seccion.filas.length > 0 })
  })
  return partes
}

const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// { llenos, total } de un tema: campos que alguien diligencia. Los módulos inactivos y lo que arma el sistema no cuentan.
export function avanceDelTema(tema, secciones) {
  let llenos = 0
  let total = 0
  if (tema.informe) return { llenos, total }
  partesDelTema(tema, secciones).forEach(p => {
    if (!p.activo || p.soloLectura || p.seccion.calculada) return
    const manuales = p.columnas.filter(c => !c.automatico && !esClaveCalculada(p.seccion.clave, c.clave) && (p.seccion.clave !== 'protocolo' || /^ps_\d+$/.test(c.clave)))
    if (p.seccion.tipo === 'CAMPOS') {
      total += manuales.length
      llenos += manuales.filter(c => tieneValor(p.seccion.valores[c.idColumna])).length
    } else if (p.modulo) {
      p.seccion.filas.forEach(f => { total += manuales.length; llenos += manuales.filter(c => tieneValor(f.valores[c.idColumna])).length })
    } else {
      total += 1
      llenos += p.seccion.filas.length > 0 ? 1 : 0
    }
  })
  return { llenos, total }
}

// Lo que el servidor exige para completar: los campos marcados como obligatorios que siguen vacíos, agrupados por tema.
export function faltantesDeVisita(secciones) {
  const grupos = []
  FASES.forEach(f => f.temas.forEach(t => {
    const items = []
    partesDelTema(t, secciones).forEach(p => {
      if (!p.activo) return
      const obligatorias = p.columnas.filter(c => c.obligatorio && !c.automatico)
      if (p.seccion.tipo === 'CAMPOS') obligatorias.filter(c => !tieneValor(p.seccion.valores[c.idColumna])).forEach(c => items.push(c.nombre || c.clave))
      else obligatorias.filter(c => p.seccion.filas.some(fila => !tieneValor(fila.valores[c.idColumna]))).forEach(c => items.push(c.nombre || c.clave))
    })
    if (items.length > 0) grupos.push({ id: t.id, titulo: t.titulo.replace(/^\d+\.\s*/, ''), fase: f.id, items })
  }))
  // VD-03: cada respuesta «Sí» del protocolo exige su detalle.
  const prot = secciones.find(s => s.clave === 'protocolo' && !s.origenFijoClave)
  if (prot) {
    const nums = numerosProtocolo()
    const faltan = PREGUNTAS_PROTOCOLO.map(p => {
      const r = prot.columnas.find(c => c.clave === `ps_${p.n}`)
      const d = prot.columnas.find(c => c.clave === `ps_${p.n}_det`)
      return r && d && prot.valores[r.idColumna] === 'Sí' && !tieneValor(prot.valores[d.idColumna]) ? `Pregunta ${nums[p.n]}: detalle` : null
    }).filter(Boolean)
    if (faltan.length > 0) {
      const g = grupos.find(x => x.id === 'protocolo')
      if (g) g.items.push(...faltan)
      else grupos.push({ id: 'protocolo', titulo: 'Protocolo de seguridad', fase: 2, items: faltan })
    }
  }
  // VD-02: «No – con explicación» exige la explicación.
  const dg = secciones.find(s => s.clave === 'datos_generales')
  const colRes = dg?.columnas.find(c => c.clave === 'v_residencia')
  const colDet = dg?.columnas.find(c => c.clave === 'v_residencia_det')
  if (colRes && colDet && dg.valores[colRes.idColumna] === 'No – con explicación' && !tieneValor(dg.valores[colDet.idColumna])) {
    const g = grupos.find(x => x.id === 'generales')
    if (g) g.items.push('Explicación de la residencia')
    else grupos.unshift({ id: 'generales', titulo: 'Datos generales', fase: 1, items: ['Explicación de la residencia'] })
  }
  return { grupos, total: grupos.reduce((n, g) => n + g.items.length, 0) }
}
