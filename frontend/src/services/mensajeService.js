import api from './api'

const mensajeService = {
  // Cliente
  listarCliente:   () => api.get('/client/mensajes').then(r => r.data),
  noLeidosCliente: () => api.get('/client/mensajes/no-leidos').then(r => r.data),
  enviarCliente:   (mensaje) => api.post('/client/mensajes', { mensaje }).then(r => r.data),

  // Gestor
  misConversaciones: () => api.get('/gestor/mensajes/conversaciones').then(r => r.data),
  listarGestor:      (idCliente) => api.get('/gestor/mensajes', { params: { idCliente } }).then(r => r.data),
  enviarGestor:      (idCliente, mensaje) => api.post('/gestor/mensajes', { mensaje }, { params: { idCliente } }).then(r => r.data),
}

export default mensajeService
