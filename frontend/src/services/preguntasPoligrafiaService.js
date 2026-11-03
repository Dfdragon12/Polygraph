import api from './api'

// Temas que el cliente puede pedir evaluar en una poligrafía (Admin → Novedades → Temas de poligrafía).
const base = '/catalogo/preguntas-poligrafia'

const preguntasPoligrafiaService = {
  listar: () => api.get(base).then(r => r.data),
  crear: (datos) => api.post(base, datos).then(r => r.data),
  actualizar: (id, datos) => api.put(`${base}/${id}`, datos).then(r => r.data),
  eliminar: (id) => api.delete(`${base}/${id}`),
  // Temas activos que se pueden pedir (los mismos que ve el cliente) y cambio de los temas de una solicitud ya hecha (admin / gestor del cliente).
  activas: () => api.get('/solicitudes/temas-poligrafia').then(r => r.data),
  reemplazarEnSolicitud: (idSubproceso, temas) => api.put(`/gestor/asignaciones/${idSubproceso}/temas-poligrafia`, temas).then(r => r.data),
}

export default preguntasPoligrafiaService
