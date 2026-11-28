import api from './api'

const ciudadService = {
  listar: () => api.get('/ciudades').then(r => r.data),

  contarPendientes: () =>
    api.get('/ciudades/pendientes/conteo').then(r => r.data.pendientes),

  // esSede (TP-08): si viene, marca o desmarca la ciudad como sede; si no, no se toca.
  clasificar: (id, nivelCiudad, esSede) =>
    api.patch(`/ciudades/${id}/nivel`, esSede === undefined ? { nivelCiudad } : { nivelCiudad, esSede }).then(r => r.data),

  confirmar: (id, codigosDane) =>
    api.patch(`/ciudades/${id}/confirmar`, codigosDane ?? {}).then(r => r.data),

  eliminar: (id) =>
    api.delete(`/ciudades/${id}`),
}

export default ciudadService
