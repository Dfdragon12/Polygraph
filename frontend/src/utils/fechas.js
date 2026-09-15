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
