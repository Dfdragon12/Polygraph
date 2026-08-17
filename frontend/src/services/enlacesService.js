import api from './api'

const enlacesService = {
  listar:        ()          => api.get('/enlaces').then(r => r.data),
  listarTodos:   ()          => api.get('/enlaces/todos').then(r => r.data),
  crear:         (data)      => api.post('/enlaces', data).then(r => r.data),
  actualizar:    (id, data)  => api.put(`/enlaces/${id}`, data).then(r => r.data),
  cambiarEstado: (id, activo) => api.patch(`/enlaces/${id}/${activo ? 'activar' : 'desactivar'}`).then(r => r.data),
  eliminar:      (id)        => api.delete(`/enlaces/${id}`),
}

export default enlacesService
