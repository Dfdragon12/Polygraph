import api from './api'

const tokenService = {
  estadisticas: () =>
    api.get('/gestor/tokens/estadisticas').then(r => r.data),

  porServicio: (idServicio) =>
    api.get(`/gestor/tokens/servicio/${idServicio}`).then(r => r.data || null),

  historialPorServicio: (idServicio) =>
    api.get(`/gestor/tokens/servicio/${idServicio}/historial`).then(r => r.data),

  resumenPorServicio: (idServicio) =>
    api.get(`/gestor/tokens/servicio/${idServicio}/resumen`).then(r => r.data),

  descargarDocumento: (idServicio, idDocumento) =>
    api.get(`/gestor/tokens/servicio/${idServicio}/documentos/${idDocumento}/descargar`, { responseType: 'blob' })
      .then(r => r.data),

  eliminar: (id) =>
    api.delete(`/gestor/tokens/${id}`),

  generar: (idServicio) =>
    api.post('/gestor/tokens', { idServicio }).then(r => r.data),

  enviarCorreo: (id) =>
    api.post(`/gestor/tokens/${id}/enviar-correo`),

  revocar: (id) =>
    api.patch(`/gestor/tokens/${id}/revocar`),

  revertir: (id) =>
    api.patch(`/gestor/tokens/${id}/revertir`).then(r => r.data),
}

export default tokenService
