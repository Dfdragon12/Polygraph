// AN-01 · Las 22 secciones de la plantilla «Antecedentes» se reagrupan en 3 fases y 9 temas, en el orden real del trabajo del analista.
// Los datos que se guardan son los mismos (las claves no cambian): solo cambia en qué lugar de la pantalla se capturan.
// `s` = clave de la sección; `cols` = solo esas columnas (la sección se reparte entre temas); `sinCols` = todas menos esas; `soloLectura` = lo arma el sistema.

export const FASES = [
  {
    id: 1, nombre: 'El evaluado',
    temas: [
      {
        id: 'datos', titulo: 'Datos del evaluado', vista: 'datos',
        // La criticidad viene de la solicitud (se muestra en el encabezado): la columna sigue en la plantilla porque el cálculo la usa, pero no se pide.
        partes: [{ s: 'datos_evaluado', sinCols: ['ev_criticidad'] }, { s: 'validacion_evaluado', cols: ['ev_tipo_cargo', 'ev_conduccion'] }],
      },
      { id: 'validacion', titulo: 'Validación del evaluado', vista: 'validacion', partes: [{ s: 'validacion_evaluado', cols: ['ev_estado_doc', 'ev_rut_actividad'] }] },
      { id: 'declaracion', titulo: 'Declaración del evaluado', vista: 'declaracion', partes: [{ s: 'declaracion_p1' }, { s: 'declaracion_p2' }, { s: 'declaracion_p3' }] },
    ],
  },
  {
    id: 2, nombre: 'Consultas',
    temas: [
      { id: 'nacionales', titulo: 'Listas nacionales', vista: 'listas', tabla: 'nacionales', partes: [{ s: 'listas_nacionales' }] },
      { id: 'internacionales', titulo: 'Listas internacionales y PEP', vista: 'listas', tabla: 'internacionales', partes: [{ s: 'listas_internacionales' }] },
      { id: 'transito', titulo: 'Tránsito', vista: 'transito', partes: [{ s: 'transito_simit' }, { s: 'simur' }, { s: 'runt' }] },
      {
        id: 'identidad', titulo: 'Identidad y contacto', vista: 'identidad',
        partes: [{ s: 'alertas_id' }, { s: 'direcciones' }, { s: 'telefonos' }, { s: 'correos' }, { s: 'identidad_contacto' }],
      },
      { id: 'procesos', titulo: 'Procesos judiciales', vista: 'procesos', partes: [{ s: 'procesos' }, { s: 'procesos_privados' }] },
    ],
  },
  {
    id: 3, nombre: 'Revisión y envío',
    temas: [
      {
        id: 'revision', titulo: 'Revisión', informe: true,
        // Lo que arma el sistema va de solo lectura.
        partes: [{ s: 'resultado_servicio', soloLectura: true }, { s: 'hallazgos', soloLectura: true }, { s: 'informativos' },
          { s: 'notas_procesos', soloLectura: true, sinCols: ['procesos_revisados', 'procesos_priv_revisados'] }, { s: 'concepto_analista' }],
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
    if (tema.vista === 'identidad') {   // AN-05: alertas revisadas (con filas o «Sin alertas») + información residencial
      const al = secciones.find(s => s.clave === 'alertas_id' && !s.origenFijoClave)
      const id = secciones.find(s => s.clave === 'identidad_contacto' && !s.origenFijoClave)
      const v = (s, clave) => s?.valores[s.columnas.find(c => c.clave === clave)?.idColumna]
      total += 2
      llenos += (al && al.filas.length > 0) || v(id, 'alertas_revisadas') === 'Sí' ? 1 : 0
      llenos += tieneValor(v(id, 'rec_residencial')) ? 1 : 0
      return
    }
    if (tema.vista === 'procesos') {   // AN-06: una fuente cuenta con sus procesos o con «No se encontraron procesos»
      const notas = secciones.find(x => x.clave === 'notas_procesos' && !x.origenFijoClave)
      const v = (clave) => notas?.valores[notas.columnas.find(c => c.clave === clave)?.idColumna]
      ;[['procesos', 'procesos_revisados'], ['procesos_privados', 'procesos_priv_revisados']].forEach(([clave, marca]) => {
        const s = secciones.find(x => x.clave === clave && !x.origenFijoClave)
        total += 1
        llenos += (s && s.filas.length > 0) || v(marca) === 'Sí' ? 1 : 0
      })
      return
    }
    if (tema.vista === 'listas') {   // AN-03: cada fuente cuenta cuando tiene su resultado
      const pre = tema.tabla === 'internacionales' ? 'li' : 'ln'
      const idRes = p.seccion.columnas.find(c => c.clave === `${pre}_resultado`)?.idColumna
      total += p.seccion.filas.length
      llenos += p.seccion.filas.filter(f => tieneValor(f.valores[idRes])).length
      return
    }
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
      // AN-03: en las listas, cada fila necesita su resultado y, con resultado, su soporte.
      if (t.vista === 'procesos') return   // se revisa abajo, una sola vez por tema
      if (t.vista === 'listas') {
        const pre = t.tabla === 'internacionales' ? 'li' : 'ln'
        const id = (clave) => p.seccion.columnas.find(c => c.clave === clave)?.idColumna
        const nombreFuente = (f) => f.valores[id(t.tabla === 'internacionales' ? 'li_lista' : 'ln_fuente')] || 'Fuente'
        p.seccion.filas.forEach(f => {
          if (!tieneValor(f.valores[id(`${pre}_resultado`)])) items.push({ texto: `${nombreFuente(f)}: marca el resultado`, clave: `${p.seccion.clave}-${nombreFuente(f)}` })
          else {
            const sop = f.valores[id(`${pre}_soporte`)]
            if (!tieneValor(sop) || sop === '[]') items.push({ texto: `${nombreFuente(f)}: falta soporte`, clave: `${pre}_soporte` })
          }
        })
        return
      }
      const obligatorias = p.columnas.filter(c => c.obligatorio && !c.automatico && !c.calculado)
      if (p.seccion.tipo === 'CAMPOS') {
        obligatorias.filter(c => !tieneValor(p.seccion.valores[c.idColumna])).forEach(c => items.push({ texto: c.nombre || c.clave, clave: c.clave }))
      } else {
        obligatorias.filter(c => p.seccion.filas.some(fila => !tieneValor(fila.valores[c.idColumna]))).forEach(c => items.push({ texto: `${p.seccion.nombre.replace(/^\d+\.\s*/, '')}: ${c.nombre || c.clave}`, clave: c.clave }))
      }
    })
    if (t.vista === 'procesos') {   // AN-06: cada fuente se completa con sus procesos o con «No se encontraron procesos»; cada proceso lleva código y soporte
      const notas = secciones.find(x => x.clave === 'notas_procesos' && !x.origenFijoClave)
      const idN = (clave) => notas?.columnas.find(c => c.clave === clave)?.idColumna
      ;[['procesos', 'p_', 'procesos_revisados', 'Rama Judicial'], ['procesos_privados', 'pp_', 'procesos_priv_revisados', 'Bases privadas']].forEach(([clave, pre, marca, titulo]) => {
        const s = secciones.find(x => x.clave === clave && !x.origenFijoClave)
        if (!s) return
        const id = (c) => s.columnas.find(x => x.clave === c)?.idColumna
        if (s.filas.length === 0 && notas?.valores[idN(marca)] !== 'Sí') items.push({ texto: `${titulo}: agrega los procesos o marca «No se encontraron procesos»`, clave: `seccion-${clave}` })
        s.filas.forEach((f, i) => {
          if (!tieneValor(f.valores[id(`${pre}calificacion`)])) items.push({ texto: `${titulo} · Proceso ${i + 1}: define el código (o —)`, clave: `${pre}proceso-${i + 1}` })
          const sop = f.valores[id(`${pre}soporte`)]
          if (!tieneValor(sop) || sop === '[]') items.push({ texto: `${titulo} · Proceso ${i + 1}: falta soporte`, clave: `${pre}soporte` })
        })
      })
    }
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
