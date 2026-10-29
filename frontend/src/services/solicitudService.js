import api from './api'

const solicitudService = {
  mora: () => api.get('/requests/mora').then(r => r.data),
  entregaEstimada: (procesos) => api.get('/requests/entrega-estimada', { params: { procesos: procesos.join(',') } }).then(r => r.data),
  resumenCobro: (idProceso, idCiudad) => api.get('/requests/resumen-cobro', { params: { idProceso, idCiudad: idCiudad ?? undefined } }).then(r => r.data),

  // clave: llave de idempotencia (NS-03) — el mismo envío repetido no cobra dos veces.
  crear: (data, clave) => api.post('/requests', data, clave ? { headers: { 'Idempotency-Key': clave } } : undefined),

  // Poligrafías: campos de cada formato adicional (PRE_EMPLEO | RUTINA | ESPECIFICA) y temas activos que se pueden pedir evaluar.
  formatos: () => api.get('/solicitudes/formatos').then(r => r.data),
  temasPoligrafia: () => api.get('/solicitudes/temas-poligrafia').then(r => r.data),

  // estadoTiempo: A_TIEMPO | POR_VENCER | VENCIDA | EN_PAUSA | ENTREGADA_A_TIEMPO | ENTREGADA_TARDE. orden='retraso': lo más atrasado primero.
  listar: ({ idCliente, estado, estadoTiempo, orden, pagina = 0, tamano = 10 } = {}) =>
    api.get('/requests', { params: { idCliente, estado, estadoTiempo: estadoTiempo || undefined, orden: orden || undefined, pagina, tamano } }),

  conteo: () => api.get('/requests/conteo').then(r => r.data),

  detalle: (id) => api.get(`/requests/${id}`),

  // PDF del informe de un subproceso aprobado (solo con la solicitud publicada). Responde 202
  // con un trabajo: seguirlo con esperarTrabajo y bajar el archivo con
  // trabajoService.descargarResultado (ver services/trabajoService.js).
  solicitarInforme: (id, idServicioSubproceso) =>
    api.post(`/requests/${id}/informes/${idServicioSubproceso}/pdf`).then(r => r.data),

  cambiarEstado: (id, estado, observacion) =>
    api.patch(`/requests/${id}/status`, { estado, observacion }),

  // Responde 202 con un trabajo (cola CARGA_MASIVA); el resumen llega en trabajo.resultado.
  cargaMasiva: (archivo) => {
    const form = new FormData()
    form.append('archivo', archivo)
    return api.post('/requests/bulk', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data)
  },

  descargarPlantilla: () =>
    api.get('/requests/bulk/template', { responseType: 'blob' }),
}

export default solicitudService
