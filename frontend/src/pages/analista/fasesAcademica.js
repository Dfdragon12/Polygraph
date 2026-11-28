import { leerPreguntas, preguntasSinResponder, codigoDePreguntas } from './reglasEstudio.js'

// AC-01 · Las 8 secciones de la plantilla «Validación académica» se reagrupan en 3 fases y 4 temas, en el orden real del trabajo del analista.
// Los datos que se guardan son los mismos (las claves no cambian): solo cambia en qué lugar de la pantalla se capturan.
// `s` = clave de la sección; `cols` = solo esas columnas (la sección se reparte entre temas); `sinCols` = todas menos esas; `soloLectura` = lo arma el sistema.

// Columnas de «5. Estudio verificado» que no son del tema 2 o que solo aplican con ciertos códigos: no cuentan en el avance.
const SIN_CONTAR = ['aca_criterio', 'aca_estudio_sel', 'aca_decl', 'aca_preguntas', 'aca_soporte', 'aca_ac01_base', 'aca_vobo', 'aca_fecha_sol_eval', 'aca_fecha_limite']

export const FASES = [
  {
    id: 1, nombre: 'El evaluado',
    temas: [
      // La criticidad viene de la solicitud (se muestra en el encabezado): la columna sigue en la plantilla porque el cálculo la usa, pero no se pide.
      { id: 'datos', titulo: 'Datos del evaluado', vista: 'datos', partes: [{ s: 'datos_evaluado', sinCols: ['ev_criticidad'] }] },
      // Los estudios que declaró el evaluado se muestran de solo lectura (tabla fija de su hoja de vida) y debajo el criterio de verificación.
      { id: 'educacion', titulo: 'Educación declarada y criterio', vista: 'educacion', declarada: true, partes: [{ s: 'estudio_verificado', cols: ['aca_criterio', 'aca_estudio_sel'] }] },
    ],
  },
  {
    id: 2, nombre: 'Verificación',
    temas: [
      // Las gestiones siguen siendo su tabla; en AC-03 pasan a mostrarse junto al plazo, dentro de este mismo tema.
      { id: 'estudio', titulo: 'Estudio verificado', vista: 'estudio', partes: [{ s: 'estudio_verificado', sinCols: SIN_CONTAR }, { s: 'gestiones' }] },
    ],
  },
  {
    id: 3, nombre: 'Revisión y envío',
    temas: [
      {
        id: 'revision', titulo: 'Revisión y envío', informe: true,
        // Lo que arma el sistema va de solo lectura.
        partes: [{ s: 'resultado_servicio', soloLectura: true }, { s: 'hallazgos', soloLectura: true }, { s: 'informativos' }, { s: 'concepto_analista' }],
      },
    ],
  },
]

export const TODOS_LOS_TEMAS = FASES.flatMap(f => f.temas.map(t => ({ ...t, fase: f.id })))

// Es el formato de validación académica si trae las secciones que identifican esta plantilla.
export function esFormatoAcademica(secciones = []) {
  const claves = new Set(secciones.map(s => s.clave))
  return ['datos_evaluado', 'educacion', 'estudio_verificado', 'gestiones', 'concepto_analista'].every(c => claves.has(c))
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
const manuales = (p) => p.columnas.filter(c => !c.automatico && !c.calculado && c.clave !== 'analista_fecha')

// { llenos, total } de un tema: lo que alguien diligencia. Lo que arma el sistema no cuenta; las gestiones son control interno y no cuentan. En la Revisión solo se diligencia el concepto.
export function avanceDelTema(tema, secciones) {
  let llenos = 0
  let total = 0
  partesDelTema(tema, secciones).forEach(p => {
    if (p.soloLectura || p.seccion.calculada || p.seccion.tipo !== 'CAMPOS') return
    if (tema.informe && p.seccion.clave !== 'concepto_analista') return
    const m = manuales(p)
    total += m.length
    llenos += m.filter(c => tieneValor(p.seccion.valores[c.idColumna])).length
  })
  return { llenos, total }
}

// Lo que el servidor exige para completar: los campos obligatorios que siguen vacíos, por tema y con la clave para llevar al campo exacto.
export function faltantesDeAcademica(secciones, declarados = 1) {
  const grupos = []
  FASES.forEach(f => f.temas.forEach(t => {
    const items = []
    partesDelTema(t, secciones).forEach(p => {
      if (p.soloLectura || p.seccion.calculada || p.seccion.clave === 'gestiones') return
      if (p.seccion.clave === 'informativos') {   // cada hallazgo sin impacto lleva el tratamiento que escribe el analista
        const idT = p.seccion.columnas.find(c => c.clave === 'i_tratamiento')?.idColumna
        p.seccion.filas.forEach((fila, n) => { if (!tieneValor(fila.valores[idT])) items.push({ texto: `Hallazgos sin impacto · fila ${n + 1}: escribe el tratamiento`, clave: 'i_tratamiento' }) })
        return
      }
      if (p.seccion.clave === 'estudio_verificado' && t.id === 'educacion') {   // AC-02: criterio y estudio a verificar
        const v = (clave) => { const c = p.seccion.columnas.find(x => x.clave === clave); return c ? (p.seccion.valores[c.idColumna] ?? '') : '' }
        if (!tieneValor(v('aca_criterio'))) items.push({ texto: 'Criterio de verificación', clave: 'aca_criterio' })
        if (!tieneValor(v('aca_estudio_sel')) && declarados > 0) items.push({ texto: 'Elige el estudio que se va a verificar', clave: 'aca_estudio_sel' })
        return
      }
      if (p.seccion.clave === 'estudio_verificado' && declarados === 0) return   // AC-06: sin estudio declarado no hay nada que verificar
      if (p.seccion.clave === 'estudio_verificado') {   // AC-02: con un criterio distinto al de por defecto, Observaciones explica el motivo
        const leer = (clave) => { const col = p.seccion.columnas.find(x => x.clave === clave); return col ? (p.seccion.valores[col.idColumna] ?? '') : '' }
        const crit = (() => { const c = secciones.find(x => x.clave === 'estudio_verificado'); const col = c?.columnas.find(x => x.clave === 'aca_criterio'); return col ? (c.valores[col.idColumna] ?? '') : '' })()
        if (tieneValor(crit) && crit !== 'Último estudio finalizado' && !tieneValor(leer('aca_observaciones'))) items.push({ texto: 'Observaciones: explica por qué se verifica este estudio', clave: 'aca_observaciones' })
        // AC-03 · preguntas para el código, motivo de un código distinto al sugerido, sustento, visto bueno, fecha de solicitud y soporte
        const resp = leerPreguntas(leer('aca_preguntas'))
        const ref = tieneValor(leer('aca_ref')) ? leer('aca_ref') : '—'
        const sugerido = codigoDePreguntas(resp).codigo
        if (preguntasSinResponder(resp).length > 0) items.push({ texto: 'Responde las preguntas para el código', clave: 'aca_preguntas' })
        if (ref !== sugerido && !tieneValor(leer('aca_observaciones')) && !items.some(i => i.clave === 'aca_observaciones')) items.push({ texto: `Observaciones: explica por qué la calificación es ${ref} y no ${sugerido}`, clave: 'aca_observaciones' })
        if (ref === 'ACA-01' && !tieneValor(leer('aca_ac01_base'))) items.push({ texto: 'Sustento del ACA-01', clave: 'aca_ac01_base' })
        if (ref === 'ACA-01' && leer('aca_ac01_base') === 'Alteración evidente en inspección visual' && (!tieneValor(leer('aca_vobo')) || leer('aca_vobo') === '—')) items.push({ texto: 'Visto bueno del segundo analista', clave: 'aca_vobo' })
        if (ref === 'ACA-04' && !tieneValor(leer('aca_fecha_sol_eval'))) items.push({ texto: 'Fecha de solicitud al evaluado (botón «Solicitar documento al evaluado»)', clave: 'aca_fecha_sol_eval' })
        if (['ACA-04', 'ALR-ACA-02'].includes(ref) && (secciones.find(x => x.clave === 'gestiones')?.filas.length ?? 0) === 0) items.push({ texto: 'Registra al menos una gestión: con este código son el soporte', clave: 'ac-gestiones' })
        const soporte = leer('aca_soporte')
        if (!['ACA-04', 'ALR-ACA-02'].includes(ref) && (!tieneValor(soporte) || soporte === '[]')) items.push({ texto: 'Soporte de la verificación', clave: 'aca_soporte' })
      }
      const obligatorias = p.columnas.filter(c => c.obligatorio && !c.automatico && !c.calculado && c.clave !== 'analista_fecha')
      if (p.seccion.tipo === 'CAMPOS') {
        obligatorias.filter(c => !tieneValor(p.seccion.valores[c.idColumna])).forEach(c => items.push({ texto: c.nombre || c.clave, clave: c.clave }))
      } else {
        obligatorias.filter(c => p.seccion.filas.some(fila => !tieneValor(fila.valores[c.idColumna])))
          .forEach(c => items.push({ texto: `${p.seccion.nombre.replace(/^\d+\.\s*/, '')}: ${c.nombre || c.clave}`, clave: c.clave }))
      }
    })
    if (items.length > 0) grupos.push({ id: t.id, titulo: t.titulo, fase: f.id, items })
  }))
  return { grupos, total: grupos.reduce((n, g) => n + g.items.length, 0) }
}

// UNA regla para los temas, las fases y la lista de faltantes: ✓ cuando no le falta ningún obligatorio y tiene algo diligenciado.
export function estadoDeTemas(secciones, declarados = 1) {
  const faltan = new Map(faltantesDeAcademica(secciones, declarados).grupos.map(g => [g.id, g.items.length]))
  const estados = {}
  let llenosTotal = 0
  let total = 0
  TODOS_LOS_TEMAS.forEach(t => {
    const { llenos, total: n } = avanceDelTema(t, secciones)
    if (!t.informe) { llenosTotal += llenos; total += n }
    estados[t.id] = n === 0 || (t.id === 'estudio' && declarados === 0) ? 'sin' : (faltan.get(t.id) ?? 0) > 0 ? (llenos === 0 ? 'vacio' : 'parcial') : llenos === 0 ? 'vacio' : 'completo'
  })
  return { estados, llenos: llenosTotal, total }
}
