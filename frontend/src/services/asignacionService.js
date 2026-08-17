import api from './api'

const asignacionService = {
  listar: (params = {}) =>
    api.get('/gestor/asignaciones', { params }).then(r => r.data),

  calendario: (desde, hasta) =>
    api.get('/gestor/asignaciones/calendario', { params: { desde, hasta } }).then(r => r.data),

  empleadosDisponibles: (idTipoProgreso) =>
    api.get(`/gestor/asignaciones/tipo-progreso/${idTipoProgreso}/empleados`).then(r => r.data),

  asignar: (id, payload) =>
    api.patch(`/gestor/asignaciones/${id}/asignar`, payload).then(r => r.data),

  asignarMasivo: (payload) =>
    api.patch('/gestor/asignaciones/bulk-asignar', payload).then(r => r.data),

  completar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/completar`).then(r => r.data),

  desasignar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/desasignar`).then(r => r.data),

  cancelar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/cancelar`).then(r => r.data),
}

export default asignacionService
