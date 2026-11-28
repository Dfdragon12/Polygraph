import api from './api'

// Gestión admin del catálogo de barrios (ver BarrioController) — el listado/creación "pública"
// para selects (ej. formulario del candidato) vive en catalogoService.js.
const barrioService = {
  listarPorCiudad: (idCiudad) => api.get('/barrios', { params: { idCiudad } }).then(r => r.data),

  contarPendientes: () =>
    api.get('/barrios/pendientes/conteo').then(r => r.data.pendientes),

  confirmar: (id) => api.patch(`/barrios/${id}/confirmar`).then(r => r.data),

  eliminar: (id) => api.delete(`/barrios/${id}`),
}

export default barrioService
