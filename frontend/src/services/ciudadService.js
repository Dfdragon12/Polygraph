import api from './api'

const ciudadService = {
  listar: () => api.get('/ciudades').then(r => r.data),

  contarPendientes: () =>
    api.get('/ciudades/pendientes/conteo').then(r => r.data.pendientes),

  clasificar: (id, nivelCiudad) =>
    api.patch(`/ciudades/${id}/nivel`, { nivelCiudad }).then(r => r.data),

  confirmar: (id) =>
    api.patch(`/ciudades/${id}/confirmar`).then(r => r.data),
}

export default ciudadService
