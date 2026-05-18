import axios from 'axios'

// Cliente sin autenticación para endpoints públicos del evaluado
const apiPublica = axios.create({ baseURL: '/api/v1' })

const evaluadoService = {
  validarLink: (token) => apiPublica.post(`/evaluees/link/${token}/validate`),

  obtenerFormulario: (token) => apiPublica.get(`/evaluees/link/${token}`),

  guardarProgreso: (token, data) =>
    apiPublica.post(`/evaluees/link/${token}/progress`, data),

  enviarFormulario: (token, data) =>
    apiPublica.post(`/evaluees/link/${token}/submit`, data),
}

export default evaluadoService
