import { faltantesDeVinculacion, estaFueraDelPeriodo } from './reglasRevisionLaboral'
import { iguales } from './reglasLaboral'

// LB-01 · Las 11 secciones de la plantilla «Validación laboral» se reagrupan en 3 fases y 5 temas, en el orden real del trabajo del analista.
// Los datos que se guardan son los mismos (las claves no cambian): solo cambia en qué lugar de la pantalla se capturan.
// `s` = clave de la sección; `cols` = solo esas columnas (la sección se reparte entre temas); `sinCols` = todas menos esas; `soloLectura` = lo arma el sistema.

export const FASES = [
  {
    id: 1, nombre: 'El evaluado',
    temas: [
      // La criticidad viene de la solicitud (se muestra en el encabezado): la columna sigue en la plantilla porque el cálculo la usa, pero no se pide.
      { id: 'datos', titulo: 'Datos del evaluado', vista: 'datos', partes: [{ s: 'datos_evaluado', sinCols: ['ev_criticidad'] }] },
      // La experiencia que declaró el evaluado se muestra de solo lectura (tabla fija de su hoja de vida) antes del alcance.
      { id: 'alcance', titulo: 'Alcance y experiencia declarada', vista: 'alcance', declarada: true, partes: [{ s: 'alcance', cols: ['lab_periodo_desde', 'lab_periodo_hasta'] }] },
    ],
  },
  {
    id: 2, nombre: 'Verificación',
    temas: [
      // Las gestiones siguen siendo su tabla; en LB-03 pasan a mostrarse dentro de cada vinculación.
      { id: 'vinculaciones', titulo: 'Vinculaciones', vista: 'vinculaciones', partes: [{ s: 'alcance', cols: ['vinc_nota'], soloLectura: true }, { s: 'vinculaciones' }, { s: 'gestiones' }] },
      { id: 'inactividad', titulo: 'Inactividad', vista: 'inactividad', partes: [{ s: 'alcance', cols: ['inactividad_nota'], soloLectura: true }, { s: 'inactividad' }, { s: 'obs_inactividad' }] },
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

// Es el formato de validación laboral si trae las secciones que identifican esta plantilla.
export function esFormatoLaboral(secciones = []) {
  const claves = new Set(secciones.map(s => s.clave))
  return ['datos_evaluado', 'alcance', 'vinculaciones', 'inactividad', 'gestiones', 'concepto_analista'].every(c => claves.has(c))
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

// Los periodos de inactividad que se ven: los «Descartado» (ya no hay inactividad) quedan vacíos y no cuentan.
export const periodosVisibles = (seccion) => {
  const id = seccion.columnas.find(c => c.clave === 'in_origen')?.idColumna
  return seccion.filas.filter(f => f.valores[id] !== 'Descartado')
}

const tieneValor = (v) => v !== undefined && v !== null && String(v).trim() !== ''

// Lo que alguien diligencia: los campos manuales de una sección de campos; una tabla cuenta 1 si tiene filas.
const manuales = (p) => p.columnas.filter(c => !c.automatico && !c.calculado && c.clave !== 'analista_fecha')

// { llenos, total } de un tema. Lo que arma el sistema no cuenta. En la Revisión solo se diligencia el concepto del analista.
export function avanceDelTema(tema, secciones) {
  let llenos = 0
  let total = 0
  partesDelTema(tema, secciones).forEach(p => {
    if (p.soloLectura || p.seccion.calculada) return
    if (tema.informe && p.seccion.clave !== 'concepto_analista') return
    if (p.seccion.tipo === 'CAMPOS') {
      const m = manuales(p)
      total += m.length
      llenos += m.filter(c => tieneValor(p.seccion.valores[c.idColumna])).length
    } else if (p.seccion.clave === 'inactividad') {
      // Sin periodos no hay nada que corroborar: el tema está completo. Con periodos, cada uno necesita su corroboración.
      const filas = periodosVisibles(p.seccion)
      const idC = p.seccion.columnas.find(c => c.clave === 'in_corroboracion')?.idColumna
      total += Math.max(1, filas.length)
      llenos += filas.length === 0 ? 1 : filas.filter(f => tieneValor(f.valores[idC])).length
    } else if (p.seccion.clave === 'vinculaciones') {
      total += 1
      llenos += p.seccion.filas.length > 0 ? 1 : 0
    }
  })
  return { llenos, total }
}

// Lo que el servidor exige para completar: los campos obligatorios que siguen vacíos, por tema y con la clave para llevar al campo exacto.
export function faltantesDeLaboral(secciones) {
  const grupos = []
  FASES.forEach(f => f.temas.forEach(t => {
    const items = []
    partesDelTema(t, secciones).forEach(p => {
      if (p.soloLectura || p.seccion.calculada) return
      if (p.seccion.clave === 'vinculaciones') {   // LB-05: lo obligatorio de cada vinculación, con la celda exacta
        const dePeriodo = (clave) => { const a = secciones.find(x => x.clave === 'alcance'); const c = a?.columnas.find(x => x.clave === clave); return c ? (a.valores[c.idColumna] ?? '') : '' }
        const sg = secciones.find(x => x.clave === 'gestiones')
        const idG = (clave) => sg?.columnas.find(c => c.clave === clave)?.idColumna
        p.seccion.filas.forEach((fila, i) => {
          const v = Object.fromEntries(p.seccion.columnas.map(c => [c.clave, fila.valores[c.idColumna] ?? '']))
          if (estaFueraDelPeriodo(v, dePeriodo('lab_periodo_desde'), dePeriodo('lab_periodo_hasta'))) return
          const llamadas = (sg?.filas ?? []).filter(g => String(g.valores[idG('g_vinculacion')] ?? '') === String(i + 1) && iguales(g.valores[idG('g_medio')], 'Llamada')).length
          items.push(...faltantesDeVinculacion(v, i + 1, llamadas))
        })
        return
      }
      if (p.seccion.clave === 'gestiones') return
      if (p.seccion.clave === 'informativos') {   // LB-05: cada hallazgo sin impacto lleva el tratamiento que escribe el analista
        const idT = p.seccion.columnas.find(c => c.clave === 'i_tratamiento')?.idColumna
        p.seccion.filas.forEach((fila, n) => { if (!tieneValor(fila.valores[idT])) items.push({ texto: `Hallazgos sin impacto · fila ${n + 1}: escribe el tratamiento`, clave: 'i_tratamiento' }) })
        return
      }
      if (p.seccion.clave === 'inactividad') {   // cada periodo necesita su corroboración
        const idC = p.seccion.columnas.find(c => c.clave === 'in_corroboracion')?.idColumna
        periodosVisibles(p.seccion).forEach((fila, n) => { if (!tieneValor(fila.valores[idC])) items.push({ texto: `Inactividad · periodo ${n + 1}: indica cómo se corroboró`, clave: `inactividad-${n + 1}` }) })
        return
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
export function estadoDeTemas(secciones) {
  const faltan = new Map(faltantesDeLaboral(secciones).grupos.map(g => [g.id, g.items.length]))
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
