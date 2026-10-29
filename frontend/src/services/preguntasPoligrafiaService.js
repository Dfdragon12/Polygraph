import api from './api'

// Temas que el cliente puede pedir evaluar en una poligrafía (Admin → Novedades → Temas de poligrafía).
const base = '/catalogo/preguntas-poligrafia'

const preguntasPoligrafiaService = {
  listar: () => api.get(base).then(r => r.data),
  crear: (datos) => api.post(base, datos).then(r => r.data),
  actualizar: (id, datos) => api.put(`${base}/${id}`, datos).then(r => r.data),
  eliminar: (id) => api.delete(`${base}/${id}`),
}

export default preguntasPoligrafiaService
