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

  subirDocumento: (token, tipoDocumento, archivo) => {
    const formData = new FormData()
    formData.append('tipoDocumento', tipoDocumento)
    formData.append('archivo', archivo)
    return apiPublica.post(`/evaluees/link/${token}/documentos`, formData)
  },

  listarDocumentos: (token) => apiPublica.get(`/evaluees/link/${token}/documentos`),

  eliminarDocumento: (token, idDocumento) =>
    apiPublica.delete(`/evaluees/link/${token}/documentos/${idDocumento}`),
}

export default evaluadoService
