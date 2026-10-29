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
