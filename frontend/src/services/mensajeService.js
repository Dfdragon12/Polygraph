import api from './api'

const mensajeService = {
  // Cliente
  listarCliente:   () => api.get('/client/mensajes').then(r => r.data),
  noLeidosCliente: () => api.get('/client/mensajes/no-leidos').then(r => r.data),
  enviarCliente:   (mensaje) => api.post('/client/mensajes', { mensaje }).then(r => r.data),

  // Gestor — un hilo por usuario del lado cliente (ADMIN_CLIENTE o cada ANALISTA_CLIENTE), ya no
  // una sola conversación compartida por empresa.
  misConversaciones: () => api.get('/gestor/mensajes/conversaciones').then(r => r.data),
  listarGestor:      (idUsuarioCliente) => api.get('/gestor/mensajes', { params: { idUsuarioCliente } }).then(r => r.data),
  enviarGestor:      (idUsuarioCliente, mensaje) => api.post('/gestor/mensajes', { mensaje }, { params: { idUsuarioCliente } }).then(r => r.data),
}

export default mensajeService
