import api from './api'

const soporteService = {
  // Cliente
  listarCliente: () => api.get('/client/soportes').then(r => r.data),
  subirCliente: (tipo, archivo) => {
    const formData = new FormData()
    formData.append('archivo', archivo)
    return api.post(`/client/soportes/${tipo}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data)
  },
  descargarCliente: (idSoporte) =>
    api.get(`/client/soportes/${idSoporte}/descargar`, { responseType: 'blob' }).then(r => r.data),
  eliminarCliente: (idSoporte) =>
    api.delete(`/client/soportes/${idSoporte}`).then(r => r.data),

  // Admin — revisión de documentos por cliente (por ahora la hace ADMIN_POLYGRAPH, no el gestor)
  listarAdmin: (idCliente) => api.get(`/admin/soportes/cliente/${idCliente}`).then(r => r.data),
  validarAdmin: (idSoporte, aprobado, observaciones) =>
    api.patch(`/admin/soportes/${idSoporte}/validar`, { aprobado, observaciones }).then(r => r.data),
  descargarAdmin: (idSoporte) =>
    api.get(`/admin/soportes/${idSoporte}/descargar`, { responseType: 'blob' }).then(r => r.data),
}

export default soporteService
