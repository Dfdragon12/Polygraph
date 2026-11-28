import api from './api'

// "Mi perfil" de analista, poligrafista, programador, visitador y gestor: siempre el usuario
// autenticado (ningún endpoint recibe un id).
const perfilInternoService = {
  obtener: () => api.get('/perfil').then(r => r.data),
  actualizar: (payload) => api.put('/perfil', payload).then(r => r.data),
}

export default perfilInternoService
