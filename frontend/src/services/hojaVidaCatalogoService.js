import api from './api'

// Listas y preguntas de la hoja de vida del candidato (Admin → Novedades → Hoja de vida del
// candidato): libreta militar, EPS, fondo de pensiones, tipo de contrato y preguntas de declaración.
const base = '/catalogo/hoja-vida'

const hojaVidaCatalogoService = {
  listar: () => api.get(base).then(r => r.data),
  crear: (datos) => api.post(base, datos).then(r => r.data),
  actualizar: (id, datos) => api.put(`${base}/${id}`, datos).then(r => r.data),
  eliminar: (id) => api.delete(`${base}/${id}`),
}

export default hojaVidaCatalogoService
