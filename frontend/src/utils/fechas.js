// El backend representa las fechas de asignación como LocalDateTime (sin zona horaria): espera
// la hora local tal cual, no UTC. Date#toISOString() convierte a UTC y desplaza la hora por el
// offset del navegador (ej. 5h en Colombia) al guardarse/filtrarse como si fuera local — por eso
// para enviar un LocalDateTime al backend se usa esto en vez de toISOString().
export function toLocalDateTimeString(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// Fecha local (sin hora) en formato yyyy-MM-dd, para inputs type="date".
export function toLocalDateString(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function hoyLocalDateString() {
  return toLocalDateString(new Date())
}

// Rango [00:00:00, 23:59:59] de un día "yyyy-MM-dd" en LocalDateTime local (ver toLocalDateTimeString).
export function rangoDelDia(diaStr) {
  const [y, m, d] = diaStr.split('-').map(Number)
  const inicio = new Date(y, m - 1, d, 0, 0, 0)
  const fin = new Date(y, m - 1, d, 23, 59, 59)
  return { desde: toLocalDateTimeString(inicio), hasta: toLocalDateTimeString(fin) }
}

// Convierte lo que manda el backend en un Date SIN correr el día. Una fecha sola ("2026-10-26", un LocalDate) la lee new Date()
// como medianoche UTC, que en Colombia es el día anterior a las 7 p. m.: por eso el gestor veía la #39 el 25 y el cliente el 26.
// Aquí una fecha sola se toma al mediodía de la zona del navegador. Fecha con hora (LocalDateTime) o con zona se deja como viene.
export function parseFecha(valor) {
  if (valor instanceof Date) return valor
  if (typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor.trim())) {
    const [y, m, d] = valor.trim().split('-').map(Number)
    return new Date(y, m - 1, d, 12, 0, 0)
  }
  return new Date(valor)
}

// ── Cómo se MUESTRA una fecha (PC-05) ──────────────────────────────────────────────────────────────────────────────
// Una sola manera por tipo de dato, la misma en el Dashboard, el detalle y Solicitudes:
//   fechaCorta   → «5 nov 2026»                    fechaHora → «5 nov 2026, 3:26 p. m.»
//   fechaDiaMes  → «5 nov»                         horaCorta → «3:26 p. m.»
//   fechaLarga   → «5 de noviembre de 2026»        fechaConDia → «jue 5 nov»        mesAnio → «nov 2026»
// Se arma con formatToParts: la ICU de cada navegador escribe distinto el «de» y el punto de «nov.», y aquí no debe cambiar.
// Reciben lo que manda el servidor (LocalDate, LocalDateTime o instante) y no corren el día (parseFecha). Sin dato o con un dato
// que no es fecha devuelven `vacio` (por defecto «—»); pasa '' para que la frase que la lleva pueda omitirse entera.
const OPC = {
  corta: new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' }),
  diaMes: new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' }),
  larga: new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric' }),
  conDia: new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short' }),
  mesAnio: new Intl.DateTimeFormat('es-CO', { month: 'short', year: 'numeric' }),
  hora: new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', hour12: true }),
}

function aFecha(valor) {
  if (valor == null || valor === '') return null
  const d = parseFecha(valor)
  return Number.isNaN(d.getTime()) ? null : d
}

function partesDe(formato, d) {
  const o = {}
  for (const p of formato.formatToParts(d)) o[p.type] = p.type === 'month' ? p.value.replace('.', '') : p.value
  return o
}

const horaDe = (d) => {
  const p = partesDe(OPC.hora, d)
  return `${p.hour}:${p.minute} ${p.dayPeriod.replace(/\s+/g, ' ')}`
}

export const fechaCorta = (valor, vacio = '—') => { const d = aFecha(valor); if (!d) return vacio; const p = partesDe(OPC.corta, d); return `${p.day} ${p.month} ${p.year}` }
export const fechaDiaMes = (valor, vacio = '—') => { const d = aFecha(valor); if (!d) return vacio; const p = partesDe(OPC.diaMes, d); return `${p.day} ${p.month}` }
export const fechaLarga = (valor, vacio = '—') => { const d = aFecha(valor); if (!d) return vacio; const p = partesDe(OPC.larga, d); return `${p.day} de ${p.month} de ${p.year}` }
export const fechaConDia = (valor, vacio = '—') => { const d = aFecha(valor); if (!d) return vacio; const p = partesDe(OPC.conDia, d); return `${p.weekday} ${p.day} ${p.month}` }
export const mesAnio = (valor, vacio = '—') => { const d = aFecha(valor); if (!d) return vacio; const p = partesDe(OPC.mesAnio, d); return `${p.month} ${p.year}` }
export const horaCorta = (valor, vacio = '—') => { const d = aFecha(valor); return d ? horaDe(d) : vacio }
export const fechaHora = (valor, vacio = '—') => { const d = aFecha(valor); if (!d) return vacio; return `${fechaCorta(d)}, ${horaDe(d)}` }
