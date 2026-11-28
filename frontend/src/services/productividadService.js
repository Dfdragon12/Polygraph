import api from './api'

// Reporte diario de productividad (SE-11): sale del registro de cambios, con corte a las 11:59 p. m. hora de Colombia.
// filtros: { desde, hasta (YYYY-MM-DD), perfil, idPersona, idTipoProgreso, idCliente } — todos opcionales.
const limpiar = (f = {}) => Object.fromEntries(Object.entries(f).filter(([, v]) => v !== '' && v != null))

const productividadService = {
  reporte: (filtros) => api.get('/reportes/productividad', { params: limpiar(filtros) }).then(r => r.data),

  // Descarga el Excel con los mismos filtros.
  exportar: async (filtros) => {
    const r = await api.get('/reportes/productividad/exportar', { params: limpiar(filtros), responseType: 'blob' })
    const nombre = /filename="?([^";]+)"?/.exec(r.headers['content-disposition'] ?? '')?.[1] ?? 'productividad.xlsx'
    const url = URL.createObjectURL(r.data)
    const a = document.createElement('a')
    a.href = url
    a.download = nombre
    a.click()
    URL.revokeObjectURL(url)
  },
}

export default productividadService
