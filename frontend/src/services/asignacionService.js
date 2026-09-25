import api from './api'

const asignacionService = {
  listar: (params = {}) =>
    api.get('/gestor/asignaciones', { params }).then(r => r.data),

  calendario: (desde, hasta) =>
    api.get('/gestor/asignaciones/calendario', { params: { desde, hasta } }).then(r => r.data),

  conteo: () =>
    api.get('/gestor/asignaciones/conteo').then(r => r.data),

  empleadosDisponibles: (idTipoProgreso, idCiudad) =>
    api.get(`/gestor/asignaciones/tipo-progreso/${idTipoProgreso}/empleados`, { params: { idCiudad } }).then(r => r.data),

  asignar: (id, payload) =>
    api.patch(`/gestor/asignaciones/${id}/asignar`, payload).then(r => r.data),

  asignarMasivo: (payload) =>
    api.patch('/gestor/asignaciones/bulk-asignar', payload).then(r => r.data),

  completar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/completar`).then(r => r.data),

  devolver: (id, observacion) =>
    api.patch(`/gestor/asignaciones/${id}/devolver`, { observacion }).then(r => r.data),

  aprobar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/aprobar`).then(r => r.data),

  desasignar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/desasignar`).then(r => r.data),

  cancelar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/cancelar`).then(r => r.data),

  asignarProgramador: (id, idProgramador) =>
    api.patch(`/gestor/asignaciones/${id}/asignar-programador`, { idProgramador }).then(r => r.data),

  programadores: () =>
    api.get('/gestor/asignaciones/programadores').then(r => r.data),

  confirmarAsistencia: (id, estadoConfirmacion, notas) =>
    api.patch(`/gestor/asignaciones/${id}/confirmacion`, { estadoConfirmacion, notas }).then(r => r.data),

  historial: (id) =>
    api.get(`/gestor/asignaciones/${id}/historial`).then(r => r.data),

  historialLlamadas: (id) =>
    api.get(`/gestor/asignaciones/${id}/historial-llamadas`).then(r => r.data),

  franjasDisponibles: (idUsuarioAsignado, fecha, idTipoProgreso, duracionMinutos, idExcluir) =>
    api.get('/gestor/asignaciones/franjas-disponibles', { params: { idUsuarioAsignado, fecha, idTipoProgreso, duracionMinutos, idExcluir } }).then(r => r.data),

  subprocesos: () =>
    api.get('/gestor/asignaciones/subprocesos').then(r => r.data),

  // Novedades del subproceso — devuelve { novedades, tipos, puedeRegistrar, puedeResolver }.
  novedades: (id) =>
    api.get(`/gestor/asignaciones/${id}/novedades`).then(r => r.data),

  // idTipoNovedad: una opción del catálogo (viene en `tipos` de novedades()).
  registrarNovedad: (id, idTipoNovedad, descripcion) =>
    api.post(`/gestor/asignaciones/${id}/novedades`, { idTipoNovedad, descripcion }).then(r => r.data),

  // nuevaFechaProgramada (opcional, 'YYYY-MM-DDTHH:mm'): además de cerrar la novedad, corre la
  // hora del subproceso.
  // marcarNoRealizado: el subproceso se cierra como "No realizado" (no penaliza en el riesgo).
  resolverNovedad: (id, idNovedad, respuesta, nuevaFechaProgramada = null, marcarNoRealizado = false) =>
    api.patch(`/gestor/asignaciones/${id}/novedades/${idNovedad}/resolver`, { respuesta, nuevaFechaProgramada, marcarNoRealizado }).then(r => r.data),

  agregarSeguimientoNovedad: (id, idNovedad, texto) =>
    api.post(`/gestor/asignaciones/${id}/novedades/${idNovedad}/seguimientos`, { texto }).then(r => r.data),
}

export default asignacionService
