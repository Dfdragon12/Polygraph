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
    partes.push({ seccion, columnas, modulo: p.modulo ?? null, confidencial: !!p.confidencial, soloLectura: !!p.soloLectura, activo: !p.modulo || seccion.filas.length > 0 })
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
    const manuales = p.columnas.filter(c => !c.automatico)
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
  return { grupos, total: grupos.reduce((n, g) => n + g.items.length, 0) }
}
