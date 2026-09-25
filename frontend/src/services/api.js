import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Instancia aparte (sin los interceptores de arriba) para renovar el token sin
// disparar el propio interceptor de respuesta en un loop si /auth/refresh también falla.
const clienteRefresh = axios.create({ baseURL: '/api/v1' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function limpiarSesionYRedirigir() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('usuario')
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

// Varias llamadas pueden expirar al mismo tiempo (ej. un dashboard que dispara varios
// fetch en paralelo) — se comparte una sola promesa de renovación para todas.
let renovacionEnCurso = null

function renovarSesion() {
  if (!renovacionEnCurso) {
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) {
      renovacionEnCurso = Promise.reject(new Error('No hay refresh token disponible'))
    } else {
      renovacionEnCurso = clienteRefresh
        .post('/auth/refresh', { refreshToken })
        .then(({ data }) => {
          localStorage.setItem('token', data.token)
          localStorage.setItem('refreshToken', data.refreshToken)
          // El backend recalcula puedeSolicitar/tipoCliente en cada refresh — se guarda también
          // acá para que ese dato no quede pegado al valor del login original (ver AuthContext,
          // que escucha este evento para refrescar su estado sin recargar la página).
          localStorage.setItem('usuario', JSON.stringify(data.usuario))
          window.dispatchEvent(new Event('usuario-refrescado'))
          return data.token
        })
    }
    renovacionEnCurso.finally(() => {
      renovacionEnCurso = null
    })
  }
  return renovacionEnCurso
}

// Las descargas (documentos, PDFs, plantillas) piden responseType: 'blob' — cuando el backend
// responde con un error, axios igual entrega ese error como Blob (no como el JSON que realmente
// es), así que err.response?.data?.mensaje siempre da undefined y todo el que lo lee cae al
// mensaje genérico de "catch", sin importar cuál fue el error de verdad. Se normaliza acá, una
// sola vez, para que todas las llamadas con responseType: 'blob' de la app reciban el mensaje real.
async function normalizarErrorBlob(error) {
  const datos = error.response?.data
  if (datos instanceof Blob && datos.type?.includes('json')) {
    try {
      error.response.data = JSON.parse(await datos.text())
    } catch {
      // el Blob no era JSON válido (ej. un error 502 en HTML de un proxy) — se deja tal cual.
    }
  }
  return error
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    await normalizarErrorBlob(error)
    const { response, config } = error

    // 401 = sesión inválida/expirada (ver JwtAuthenticationEntryPoint en backend).
    // Se intenta renovar una sola vez por request antes de rendirse.
    if (response?.status === 401 && config && !config._reintentadoTrasRefresh) {
      config._reintentadoTrasRefresh = true
      try {
        const nuevoToken = await renovarSesion()
        config.headers.Authorization = `Bearer ${nuevoToken}`
        return api(config)
      } catch {
        limpiarSesionYRedirigir()
        return Promise.reject(error)
      }
    }

    // 403 = sesión válida pero sin permiso para esta acción (ver JwtAccessDeniedHandler).
    // No se toca la sesión — el componente que hizo la llamada decide qué mostrar.
    return Promise.reject(error)
  },
)

export default api
