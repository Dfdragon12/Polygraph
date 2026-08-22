import api from './api'

const perfilService = {
  obtener: () => api.get('/client/perfil').then(r => r.data),
  actualizar: (payload) => api.put('/client/perfil', payload).then(r => r.data),
}

export default perfilService
