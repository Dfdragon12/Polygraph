import api from './api'

// Catálogo de tipos de novedad (admin/catálogo → Tipos de novedad) — solo ADMIN_POLYGRAPH.
const base = '/catalogo/novedades'

const tiposNovedadService = {
  listarCategorias: () => api.get(`${base}/categorias`).then(r => r.data),
  crearCategoria: (payload) => api.post(`${base}/categorias`, payload).then(r => r.data),
  actualizarCategoria: (id, payload) => api.put(`${base}/categorias/${id}`, payload).then(r => r.data),
  cambiarEstadoCategoria: (id, activo) => api.patch(`${base}/categorias/${id}/${activo ? 'activar' : 'desactivar'}`).then(r => r.data),
  eliminarCategoria: (id) => api.delete(`${base}/categorias/${id}`),

  listarTipos: () => api.get(`${base}/tipos`).then(r => r.data),
  crearTipo: (payload) => api.post(`${base}/tipos`, payload).then(r => r.data),
  actualizarTipo: (id, payload) => api.put(`${base}/tipos/${id}`, payload).then(r => r.data),
  cambiarEstadoTipo: (id, activo) => api.patch(`${base}/tipos/${id}/${activo ? 'activar' : 'desactivar'}`).then(r => r.data),
  eliminarTipo: (id) => api.delete(`${base}/tipos/${id}`),
}

export default tiposNovedadService
