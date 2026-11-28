import api from './api'

const departamentoService = {
  listar: () => api.get('/departamentos').then(r => r.data),

  crear: (payload) => api.post('/departamentos', payload).then(r => r.data),

  actualizar: (id, payload) => api.patch(`/departamentos/${id}`, payload).then(r => r.data),

  eliminar: (id) => api.delete(`/departamentos/${id}`),
}

export default departamentoService
