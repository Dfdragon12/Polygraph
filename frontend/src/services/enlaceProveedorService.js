import api from './api'

// PF-09 · Enlace del proveedor externo. generar/historial/revocar los usa el programador (o el admin); sesion() lo llama la pantalla
// del proveedor para comprobar que su enlace sirve.
const enlaceProveedorService = {
  generar: (idSubproceso) => api.post(`/gestor/asignaciones/${idSubproceso}/enlace-proveedor`).then(r => r.data),
  historial: (idSubproceso) => api.get(`/gestor/asignaciones/${idSubproceso}/enlace-proveedor`).then(r => r.data),
  revocar: (idSubproceso) => api.delete(`/gestor/asignaciones/${idSubproceso}/enlace-proveedor`),
  sesion: () => api.get('/proveedor/sesion').then(r => r.data),
  urlDelEnlace: (token) => `${window.location.origin}/proveedor/${token}`,
}

export default enlaceProveedorService
