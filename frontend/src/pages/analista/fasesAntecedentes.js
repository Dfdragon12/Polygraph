// AN-01 · Las 22 secciones de la plantilla «Antecedentes» se reagrupan en 3 fases y 9 temas, en el orden real del trabajo del analista.
// Los datos que se guardan son los mismos (las claves no cambian): solo cambia en qué lugar de la pantalla se capturan.
// `s` = clave de la sección; `cols` = solo esas columnas (la sección se reparte entre temas); `sinCols` = todas menos esas; `soloLectura` = lo arma el sistema.

export const FASES = [
  {
    id: 1, nombre: 'El evaluado',
    temas: [
      {
        id: 'datos', titulo: 'Datos del evaluado', candidato: true,
        // La criticidad viene de la solicitud (se muestra en el encabezado): la columna sigue en la plantilla porque el cálculo la usa, pero no se pide.
        partes: [{ s: 'datos_evaluado', sinCols: ['ev_criticidad'] }, { s: 'validacion_evaluado', cols: ['ev_tipo_cargo', 'ev_conduccion'] }],
      },
      { id: 'validacion', titulo: 'Validación del evaluado', partes: [{ s: 'validacion_evaluado', cols: ['ev_estado_doc', 'ev_rut_actividad'] }] },
      { id: 'declaracion', titulo: 'Declaración del evaluado', partes: [{ s: 'declaracion_p1' }, { s: 'declaracion_p2' }, { s: 'declaracion_p3' }] },
    ],
  },
  {
    id: 2, nombre: 'Consultas',
    temas: [
      { id: 'nacionales', titulo: 'Listas nacionales', partes: [{ s: 'listas_nacionales' }] },
      { id: 'internacionales', titulo: 'Listas internacionales y PEP', partes: [{ s: 'listas_internacionales' }] },
      { id: 'transito', titulo: 'Tránsito', partes: [{ s: 'transito_simit' }, { s: 'simur' }, { s: 'runt' }] },
      {
        id: 'identidad', titulo: 'Identidad y contacto',
        partes: [{ s: 'alertas_id' }, { s: 'direcciones' }, { s: 'telefonos' }, { s: 'correos' }, { s: 'identidad_contacto' }],
      },
      { id: 'procesos', titulo: 'Procesos judiciales', partes: [{ s: 'procesos' }, { s: 'procesos_privados' }] },
    ],
  },
  {
    id: 3, nombre: 'Revisión y envío',
    temas: [
      {
        id: 'revision', titulo: 'Revisión', informe: true,
        // Lo que arma el sistema va de solo lectura.
        partes: [{ s: 'resultado_servicio', soloLectura: true }, { s: 'hallazgos', soloLectura: true }, { s: 'informativos' },
          { s: 'notas_procesos', soloLectura: true }, { s: 'concepto_analista' }],
      },
    ],
  },
]

export const TODOS_LOS_TEMAS = FASES.flatMap(f => f.temas.map(t => ({ ...t, fase: f.id })))

// Es el formato de antecedentes si trae las secciones que identifican esta plantilla.
export function esFormatoAntecedentes(secciones = []) {
  const claves = new Set(secciones.map(s => s.clave))
  return ['datos_evaluado', 'declaracion_p1', 'listas_nacionales', 'listas_internacionales', 'procesos', 'procesos_privados', 'concepto_analista'].every(c => claves.has(c))
}

// Las partes de un tema con su sección y las columnas que le tocan.
export function partesDelTema(tema, secciones) {
  const partes = []
  ;(tema.partes ?? []).forEach(p => {
    const seccion = secciones.find(s => s.clave === p.s && !s.origenFijoClave)
    if (!seccion) return
    let columnas = seccion.columnas
    if (p.cols) columnas = columnas.filter(c => p.cols.includes(c.clave))
    if (p.sinCols) columnas = columnas.filter(c => !p.sinCols.includes(c.clave))
    if (columnas.length === 0) return
    partes.push({ seccion, columnas, soloLectura: !!p.soloLectura })
  })
  return partes
}

const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// { llenos, total } de un tema: lo que alguien diligencia. Lo que arma el sistema no cuenta; una tabla cuenta 1 si tiene filas.
export function avanceDelTema(tema, secciones) {
  let llenos = 0
  let total = 0
  if (tema.informe) {
    // En la Revisión solo se diligencia el concepto del analista.
    partesDelTema(tema, secciones).filter(p => p.seccion.clave === 'concepto_analista').forEach(p => {
      const m = p.columnas.filter(c => !c.automatico)
      total += m.length
      llenos += m.filter(c => tieneValor(p.seccion.valores[c.idColumna])).length
    })
    return { llenos, total }
  }
  partesDelTema(tema, secciones).forEach(p => {
    if (p.soloLectura || p.seccion.calculada) return
    const manuales = p.columnas.filter(c => !c.automatico && !c.calculado)
    if (p.seccion.tipo === 'CAMPOS') {
      total += manuales.length
      llenos += manuales.filter(c => tieneValor(p.seccion.valores[c.idColumna])).length
    } else {
      total += 1
      llenos += p.seccion.filas.length > 0 ? 1 : 0
    }
  })
  return { llenos, total }
}

// Lo que el servidor exige para completar: los campos obligatorios que siguen vacíos, por tema y con la clave para llevar a la pregunta exacta.
export function faltantesDeAntecedentes(secciones) {
  const grupos = []
  FASES.forEach(f => f.temas.forEach(t => {
    const items = []
    partesDelTema(t, secciones).forEach(p => {
      const obligatorias = p.columnas.filter(c => c.obligatorio && !c.automatico && !c.calculado)
      if (p.seccion.tipo === 'CAMPOS') {
        obligatorias.filter(c => !tieneValor(p.seccion.valores[c.idColumna])).forEach(c => items.push({ texto: c.nombre || c.clave, clave: c.clave }))
      } else {
        obligatorias.filter(c => p.seccion.filas.some(fila => !tieneValor(fila.valores[c.idColumna]))).forEach(c => items.push({ texto: `${p.seccion.nombre.replace(/^\d+\.\s*/, '')}: ${c.nombre || c.clave}`, clave: c.clave }))
      }
    })
    if (items.length > 0) grupos.push({ id: t.id, titulo: t.titulo, fase: f.id, items })
  }))
  return { grupos, total: grupos.reduce((n, g) => n + g.items.length, 0) }
}

// UNA regla para los temas, las fases y la lista de faltantes: ✓ cuando no le falta ningún obligatorio y tiene algo diligenciado.
export function estadoDeTemas(secciones) {
  const faltan = new Map(faltantesDeAntecedentes(secciones).grupos.map(g => [g.id, g.items.length]))
  const estados = {}
  let llenosTotal = 0
  let total = 0
  TODOS_LOS_TEMAS.forEach(t => {
    const { llenos, total: n } = avanceDelTema(t, secciones)
    if (!t.informe) { llenosTotal += llenos; total += n }
    estados[t.id] = n === 0 ? 'sin' : (faltan.get(t.id) ?? 0) > 0 ? (llenos === 0 ? 'vacio' : 'parcial') : llenos === 0 ? 'vacio' : 'completo'
  })
  return { estados, llenos: llenosTotal, total }
}
