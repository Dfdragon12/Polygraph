import api from './api'

// Listas de motivos de reprogramación, cancelación y devolución (las administra el admin).
// tipo: 'REPROGRAMACION' | 'CANCELACION' | 'DEVOLUCION'
const motivosService = {
  activos: (tipo) => api.get('/catalogo/motivos', { params: { tipo } }).then(r => r.data),
  todos: () => api.get('/catalogo/motivos/todos').then(r => r.data),
  crear: (payload) => api.post('/catalogo/motivos', payload).then(r => r.data),
  actualizar: (id, payload) => api.put(`/catalogo/motivos/${id}`, payload).then(r => r.data),
}

export default motivosService
