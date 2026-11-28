import api from './api'

// El semáforo de estado de TODOS los perfiles (SE-04): contadores y lista filtrada salen del mismo servicio del backend.
// filtros: { tiempo, idCliente, idResponsable, rol, idTipoProgreso } — todos opcionales y combinables.
// Cliente, gestor y administrador reciben servicios; analista, poligrafista, visitador y programador, SUS subprocesos.
const semaforoService = {
  // { unidad: 'SERVICIOS' | 'SUBPROCESOS', total, estados: [{ codigo, nombre, color, orden, textoCliente, servicios, subprocesos }] }
  resumen: (filtros = {}) => api.get('/semaforo', { params: filtros }).then(r => r.data),

  // Página de servicios que pertenecen al estado (al menos un subproceso en él); sin estado, todos los del alcance.
  // Cada fila trae resumen por estado, avance, etiqueta de publicación, tiempo y subprocesos (resaltados los del estado).
  // Sin asignar, Novedades abiertas, Citas de hoy, Vencidas, Para hoy, Por confirmar. Una que no aplica al perfil viene en null.
  alertas: () => api.get('/semaforo/alertas').then(r => r.data),

  servicios: ({ estado, page = 0, size = 10, ...filtros } = {}) =>
    api.get('/semaforo/servicios', { params: { estado, page, size, ...filtros } }).then(r => r.data),
}

export default semaforoService
