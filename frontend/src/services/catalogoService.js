import api from './api'

const catalogoService = {
  async listarServicios() {
    const { data } = await api.get('/catalogo/procesos/activos')
    return data
  },

  async obtenerSubprocesos(idProceso) {
    const { data } = await api.get(`/catalogo/procesos/${idProceso}/pasos-publico`)
    return data
  },

  async listarCiudades() {
    const { data } = await api.get('/ciudades')
    return data
  },

  async crearCiudad({ nombreCiudad, departamento, codigoDaneCiudad, codigoDaneDepto }) {
    const { data } = await api.post('/ciudades', { nombreCiudad, departamento, codigoDaneCiudad, codigoDaneDepto })
    return data
  },
}

export default catalogoService
