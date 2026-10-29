import axios from 'axios'

// Cliente sin autenticación para endpoints públicos del evaluado
const apiPublica = axios.create({ baseURL: '/api/v1' })

// Llave de acceso al portal: se obtiene escribiendo el número de documento (verificarDocumento) y
// va en X-Acceso-Candidato de cada petición. Dura lo que dure la pestaña/sesión del navegador.
const claveAcceso = (token) => `acceso-candidato-${token}`
export function accesoCandidato(token) {
  try { return sessionStorage.getItem(claveAcceso(token)) } catch { return null }
}
export function olvidarAcceso(token) {
  try { sessionStorage.removeItem(claveAcceso(token)) } catch { /* sin almacenamiento */ }
}
apiPublica.interceptors.request.use((config) => {
  const token = /\/evaluees\/link\/([^/?]+)/.exec(config.url ?? '')?.[1]
  const acceso = token ? accesoCandidato(token) : null
  if (acceso) config.headers['X-Acceso-Candidato'] = acceso
  return config
})

const evaluadoService = {
  // Entrada al portal con el número de documento: guarda la llave para las demás peticiones.
  verificarDocumento: (token, documento) =>
    apiPublica.post(`/evaluees/link/${token}/verificar`, { documento }).then(r => {
      try { sessionStorage.setItem(claveAcceso(token), r.data.acceso) } catch { /* sin almacenamiento */ }
      return r.data
    }),

  validarLink: (token) => apiPublica.post(`/evaluees/link/${token}/validate`),

  obtenerFormulario: (token) => apiPublica.get(`/evaluees/link/${token}`),

  // Listas (libreta militar, EPS, fondo de pensiones, tipo de contrato) y preguntas de declaración
  // que administra el admin: { LIBRETA_MILITAR: [{ id, nombre, descripcion }], ..., PREGUNTA: [...] }
  catalogo: (token) => apiPublica.get(`/evaluees/link/${token}/catalogo`).then(r => r.data),

  // Primera pantalla del portal: acepta el tratamiento de datos (Ley 1581).
  autorizarDatos: (token) => apiPublica.post(`/evaluees/link/${token}/autorizacion`),

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

  // "Mis citas": poligrafías y visitas del servicio de este link.
  citas: (token) => apiPublica.get(`/evaluees/link/${token}/citas`).then(r => r.data),

  // { opciones: [{ fecha: 'YYYY-MM-DD', franja: 'MANANA'|'TARDE'|'CUALQUIERA' }], comentario }
  sugerirHorario: (token, idCita, data) =>
    apiPublica.post(`/evaluees/link/${token}/citas/${idCita}/sugerencias`, data).then(r => r.data),
}

export default evaluadoService
