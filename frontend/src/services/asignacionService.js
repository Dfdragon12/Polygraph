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

  // motivo: { idMotivo, observacion } — el motivo sale de la lista de devolución (SE-02)
  devolver: (id, motivo) =>
    api.patch(`/gestor/asignaciones/${id}/devolver`, motivo).then(r => r.data),

  aprobar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/aprobar`).then(r => r.data),

  desasignar: (id) =>
    api.patch(`/gestor/asignaciones/${id}/desasignar`).then(r => r.data),

  // motivo: { idMotivo, texto } — lista de cancelación (SE-02)
  cancelar: (id, motivo) =>
    api.patch(`/gestor/asignaciones/${id}/cancelar`, motivo).then(r => r.data),

  // ── Citas (SE-07) ──
  iniciarInforme: (id) => api.patch(`/gestor/asignaciones/${id}/iniciar-informe`).then(r => r.data),
  evaluadoNoHaLlegado: (id) => api.patch(`/gestor/asignaciones/${id}/evaluado-no-ha-llegado`).then(r => r.data),
  // { idMotivo, texto }
  citaNoRealizada: (id, payload) => api.patch(`/gestor/asignaciones/${id}/cita-no-realizada`, payload).then(r => r.data),
  // { fechaProgramada, idMotivo, texto, lugar }
  reprogramarCita: (id, payload) => api.patch(`/gestor/asignaciones/${id}/reprogramar-cita`, payload).then(r => r.data),
  // { idUsuarioAsignado, fechaProgramada, idMotivo, texto, lugar }
  reasignarCita: (id, payload) => api.patch(`/gestor/asignaciones/${id}/reasignar-cita`, payload).then(r => r.data),
  citasPorConfirmar: () => api.get(`/gestor/asignaciones/citas/por-confirmar`).then(r => r.data),
  citasResumen: () => api.get(`/gestor/asignaciones/citas/resumen`).then(r => r.data),

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

  // Poligrafía/visita: dirección del candidato (prellena el lugar) + la sugerencia de horario que
  // mandó desde su portal → { direccionCandidato, sugerenciaPendiente: { comentario, fechaCreacion, opciones } }
  citaCandidato: (id) => api.get(`/gestor/asignaciones/${id}/cita-candidato`).then(r => r.data),

  // Ids de subprocesos con sugerencia de horario del candidato sin atender (para marcarlos).
  sugerenciasPendientes: () => api.get('/gestor/asignaciones/sugerencias-pendientes').then(r => r.data),

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
