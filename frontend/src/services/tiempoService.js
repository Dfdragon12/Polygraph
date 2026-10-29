import api from './api'

// Resumen ÚNICO de tiempos que usan todos los tableros (cliente, gestor, administrador, Semáforo): conteos por estado de tiempo, matriz
// etapa × estado de tiempo, cumplimiento del periodo y las más atrasadas. Cada rol recibe solo lo suyo (un cliente, sus solicitudes; un
// gestor, las de sus clientes). desde / hasta: 'AAAA-MM-DD'.
const tiempoService = {
  resumen: ({ idCliente, idGestor, desde, hasta, top } = {}) =>
    api.get('/tiempos/resumen', { params: { idCliente, idGestor, desde, hasta, top } }).then(r => r.data),

  // Las solicitudes detrás de un número del resumen (una tarjeta o una celda de la matriz). etapa: 'EN_EJECUCION'...; estadoTiempo: uno o varios
  // separados por coma.
  solicitudes: ({ idCliente, idGestor, desde, hasta, etapa, estadoTiempo, pagina = 0, tamano = 10 } = {}) =>
    api.get('/tiempos/solicitudes', { params: { idCliente, idGestor, desde, hasta, etapa, estadoTiempo, pagina, tamano } }).then(r => r.data),

  // Parámetros del cálculo (solo administrador): zona horaria, hora de corte, umbral de "por vencer", regla de reprogramación.
  parametros: () => api.get('/admin/parametros-tiempo').then(r => r.data),
  cambiarParametro: (clave, valor) => api.put(`/admin/parametros-tiempo/${clave}`, { valor }).then(r => r.data),
}

export default tiempoService
