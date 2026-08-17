import api from './api'

const reversionService = {
  listar: ({ estado, q, page = 0, size = 20, base = 'admin' } = {}) =>
    api.get(`/${base}/reversiones`, { params: { estado, q, page, size } }).then(r => r.data),

  contarPendientes: () =>
    api.get('/admin/reversiones/pendientes/conteo').then(r => r.data.pendientes),

  aprobar: (id, comentario) =>
    api.patch(`/admin/reversiones/${id}/aprobar`, { comentario: comentario || null }),

  rechazar: (id, comentario) =>
    api.patch(`/admin/reversiones/${id}/rechazar`, { comentario: comentario || null }),
}

export default reversionService
