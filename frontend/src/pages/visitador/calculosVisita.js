// VD-05 · Cálculos automáticos de la visita domiciliaria. El visitador nunca escribe un resultado que el sistema pueda calcular: aquí se calculan con la
// misma regla de motor_vis.py y se guardan en las columnas de siempre (las claves no cambian), así que el Word sale igual.
import { INDICADORES, calcularEscala, redondear } from './fasesVisita'

export { redondear }
const tiene = (v) => v !== undefined && v !== null && String(v).trim() !== ''

export const conceptoDe = (pct) => (pct < 40 ? 'Desfavorable' : pct < 70 ? 'Favorable' : pct < 85 ? 'Bien favorable' : 'Muy favorable')

// Número guardado (texto de dígitos) → "$4.800.000"; vacío si no hay número.
export function formatoMoneda(n) {
  if (n === null || n === undefined || n === '' || Number.isNaN(Number(n))) return ''
  const entero = Math.round(Number(n))
  return `${entero < 0 ? '-' : ''}$${String(Math.abs(entero)).replace(/\B(?=(\d{3})+(?!\d))/g, '.')}`
}

// Solo dígitos: lo que escribe el visitador en un campo de dinero ("$4.800.000" → "4800000").
export const soloDigitos = (t) => String(t ?? '').replace(/\D/g, '')

const norm = (s) => String(s ?? '').trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')

// «Madre $450.000; Candidato $450.000» ⇄ [{ integrante, monto }]. El texto es lo que se imprime en el Word.
export function leerAportantes(texto) {
  if (!tiene(texto)) return []
  return String(texto).split(';').map(p => p.trim()).filter(Boolean).map(p => {
    const m = p.match(/^(.*?)\s*\$\s*([\d.]+)\s*$/)
    return m ? { integrante: m[1].trim(), monto: soloDigitos(m[2]) } : { integrante: p, monto: '' }
  })
}
export function textoAportantes(lista) {
  return lista.filter(a => tiene(a.integrante) || tiene(a.monto))
    .map(a => `${a.integrante ?? ''}${tiene(a.monto) ? ` ${formatoMoneda(a.monto)}` : ''}`.trim()).join('; ')
}
export const esElCandidato = (integrante) => norm(integrante) === 'candidato'

// Lista de integrantes que pueden pagar un egreso: el candidato y quienes conviven con él (sección 5).
export function integrantes(seccionConvive) {
  const filas = (seccionConvive?.filas ?? []).map(f => {
    const v = (clave) => f.valores[seccionConvive.columnas.find(c => c.clave === clave)?.idColumna]
    return { nombre: String(v('fc_nombre') ?? '').trim(), parentesco: String(v('fc_parentesco') ?? '').trim() }
  }).filter(f => f.nombre || f.parentesco)
  const etiqueta = (f) => {
    const repetido = filas.filter(o => norm(o.parentesco) === norm(f.parentesco)).length > 1
    return f.parentesco && !repetido ? f.parentesco : [f.nombre, f.parentesco && `(${f.parentesco})`].filter(Boolean).join(' ')
  }
  return ['Candidato', ...new Set(filas.map(etiqueta).filter(Boolean))]
}

const valorDe = (s, clave, fila) => {
  const c = s?.columnas.find(x => x.clave === clave)
  if (!c) return ''
  return (fila ? fila.valores : s.valores)[c.idColumna] ?? ''
}

// Todo lo que el sistema calcula, como { claveSeccion: { claveColumna: valor } }. Solo trae lo que se puede calcular con lo que hay.
export function calcularVisita(secciones) {
  const por = (clave) => secciones.find(s => s.clave === clave && !s.origenFijoClave)
  const r = {}
  const poner = (sec, clave, valor) => { (r[sec] ??= {})[clave] = valor }

  // Escalas: df, hab, eco
  const pcts = []
  ;['dinamica_familiar', 'sector_vivienda', 'economia'].forEach(sec => {
    const s = por(sec), g = INDICADORES[sec]
    if (!s) return
    const { pct: exacto } = calcularEscala(g.nombres.map((_, i) => valorDe(s, `${g.prefijo}_${i + 1}`)), g.nombres.length)
    poner(sec, `${g.prefijo}_pct`, exacto === null ? '' : `${exacto} %`)
    poner(sec, `${g.prefijo}_concepto`, exacto === null ? '' : conceptoDe(exacto))
    pcts.push(exacto)
  })
  // Teletrabajo (módulo, una fila)
  const tt = por('teletrabajo')
  if (tt?.filas[0]) {
    const g = INDICADORES.teletrabajo
    const v = g.nombres.map((_, i) => valorDe(tt, `tq_${i + 1}`, tt.filas[0]))
    const ok = v.every(x => /^[1-4]$/.test(String(x)))
    const exacto = ok ? redondear(v.reduce((a, b) => a + Number(b), 0) / (4 * g.nombres.length) * 100) : null
    poner('teletrabajo', 'tq_pct', exacto === null ? '' : `${exacto} %`)
    poner('teletrabajo', 'tq_concepto', exacto === null ? '' : conceptoDe(exacto))
  }
  // Perfil: promedio de los tres porcentajes
  if (pcts.length === 3 && pcts.every(x => x !== null)) {
    const prom = redondear(pcts.reduce((a, b) => a + b, 0) / 3)
    poner('concepto_final', 'perfil_pct', `${prom} %`)
    poner('concepto_final', 'perfil_concepto', conceptoDe(prom))
  } else {
    poner('concepto_final', 'perfil_pct', '')
    poner('concepto_final', 'perfil_concepto', '')
  }

  // Economía del hogar
  const ing = por('ingresos_hogar'), egr = por('egresos_hogar')
  if (ing && egr) {
    const filasIng = ing.filas.map(f => ({ quien: String(valorDe(ing, 'in_integrante', f)).trim(), monto: Number(soloDigitos(valorDe(ing, 'in_monto', f))) || 0 }))
    const totalIng = filasIng.reduce((a, f) => a + f.monto, 0)
    const filasEg = egr.filas.map(f => ({ monto: Number(soloDigitos(valorDe(egr, 'eg_monto', f))) || 0, aportes: leerAportantes(valorDe(egr, 'eg_aportantes', f)) }))
    const totalEg = filasEg.reduce((a, f) => a + f.monto, 0)
    const aporte = filasEg.reduce((a, f) => a + f.aportes.filter(x => esElCandidato(x.integrante)).reduce((b, x) => b + (Number(x.monto) || 0), 0), 0)
    const hayIng = filasIng.some(f => f.monto > 0), hayEg = filasEg.some(f => f.monto > 0)
    poner('economia', 'eco_total_ing', hayIng ? formatoMoneda(totalIng) : '')
    poner('economia', 'eco_total_eg', hayEg ? formatoMoneda(totalEg) : '')
    poner('economia', 'eco_aporte', hayEg ? formatoMoneda(aporte) : '')
    poner('economia', 'eco_pct_candidato', hayEg && totalEg > 0 ? `${redondear(aporte / totalEg * 100)} %` : '')
    // Mejor ingreso: el integrante con más dinero en total; con empate no se decide (queda editable).
    const sumas = new Map()
    filasIng.filter(f => f.quien && f.monto > 0).forEach(f => { const k = norm(f.quien); sumas.set(k, { quien: f.quien, total: (sumas.get(k)?.total ?? 0) + f.monto }) })
    const orden = [...sumas.values()].sort((a, b) => b.total - a.total)
    if (orden.length > 0 && (orden.length === 1 || orden[0].total > orden[1].total)) poner('economia', 'eco_mejor_ingreso', orden[0].quien)
    r._economia = { totalIng, totalEg, hayIng, hayEg }
  }
  return r
}

// Claves que el visitador ya no escribe (el sistema las calcula). eco_mejor_ingreso queda editable por si hay empate.
export const CLAVES_CALCULADAS = new Set([
  'df_pct', 'df_concepto', 'hab_pct', 'hab_concepto', 'eco_pct', 'eco_concepto', 'tq_pct', 'tq_concepto', 'perfil_pct', 'perfil_concepto',
  'eco_total_ing', 'eco_total_eg', 'eco_aporte', 'eco_pct_candidato',
])
