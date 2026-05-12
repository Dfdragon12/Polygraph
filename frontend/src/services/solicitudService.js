import api from './api'

const solicitudService = {
  crear: (data) => api.post('/requests', data),

  listar: ({ idCliente, estado, pagina = 0, tamano = 10 } = {}) =>
    api.get('/requests', { params: { idCliente, estado, pagina, tamano } }),

  detalle: (id) => api.get(`/requests/${id}`),

  cambiarEstado: (id, estado, observacion) =>
    api.patch(`/requests/${id}/status`, { estado, observacion }),

  cargaMasiva: (archivo) => {
    const form = new FormData()
    form.append('archivo', archivo)
    return api.post('/requests/bulk', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  descargarPlantilla: () =>
    api.get('/requests/bulk/template', { responseType: 'blob' }),
}

export default solicitudService
