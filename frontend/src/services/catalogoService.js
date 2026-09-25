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

  async listarDepartamentos() {
    const { data } = await api.get('/departamentos')
    return data
  },

  async crearCiudad({ nombreCiudad, departamento, codigoDaneCiudad }) {
    const { data } = await api.post('/ciudades', { nombreCiudad, departamento, codigoDaneCiudad })
    return data
  },

  async listarBarrios(idCiudad) {
    const { data } = await api.get('/barrios', { params: { idCiudad } })
    return data
  },

  async crearBarrio({ nombre, idCiudad }) {
    const { data } = await api.post('/barrios', { nombre, idCiudad })
    return data
  },
}

export default catalogoService
