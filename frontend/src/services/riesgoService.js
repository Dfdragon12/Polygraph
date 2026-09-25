import api from './api'

// Riesgo de un caso (servicio) — pestaña "Riesgo" del detalle de la solicitud del gestor.
const riesgoService = {
  obtener: (idServicio) => api.get(`/riesgo/servicios/${idServicio}`).then(r => r.data),

  // criticidad null = volver a la del proceso
  cambiarCriticidad: (idServicio, criticidad) =>
    api.put(`/riesgo/servicios/${idServicio}/criticidad`, { criticidad }).then(r => r.data),

  decidir: (idServicio, nivel, justificacion) =>
    api.put(`/riesgo/servicios/${idServicio}/decision`, { nivel, justificacion }).then(r => r.data),

  // Encabezado del formulario: riesgo del subproceso (y del estudio, solo gestor/admin)
  resumenSubproceso: (idServicioSubproceso) =>
    api.get(`/riesgo/subprocesos/${idServicioSubproceso}/resumen`).then(r => r.data),
}

export default riesgoService
