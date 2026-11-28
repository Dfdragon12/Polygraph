import api from './api'

const perfilService = {
  // "Mi perfil" del ANALISTA_CLIENTE (sus datos personales, no los de la empresa)
  miPerfil: () => api.get('/client/mi-perfil').then(r => r.data),
  actualizarMiPerfil: (payload) => api.put('/client/mi-perfil', payload).then(r => r.data),

  obtener: () => api.get('/client/perfil').then(r => r.data),
  actualizar: (payload) => api.put('/client/perfil', payload).then(r => r.data),

  // Autoservicio: el ADMIN_CLIENTE crea sus propios usuarios ANALISTA_CLIENTE (tope de 2) — ver
  // pantalla "Mi Equipo" del portal cliente.
  misUsuarios: () => api.get('/client/perfil/usuarios').then(r => r.data),
  crearUsuario: (payload) => api.post('/client/perfil/usuarios', payload).then(r => r.data),
  actualizarUsuario: (idUsuario, payload) => api.put(`/client/perfil/usuarios/${idUsuario}`, payload).then(r => r.data),
  activarUsuario: (idUsuario) => api.patch(`/client/perfil/usuarios/${idUsuario}/activar`).then(r => r.data),
  desactivarUsuario: (idUsuario) => api.patch(`/client/perfil/usuarios/${idUsuario}/desactivar`).then(r => r.data),
}

export default perfilService
