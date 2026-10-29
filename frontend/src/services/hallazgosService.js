import api from './api'

// Catálogo de hallazgos (Admin → Calificación de riesgo): la base de la calificación por hallazgos.
// Es uno solo para todos: qué hallazgos ofrece cada formulario lo define su plantilla.
const base = '/catalogo/hallazgos'

const hallazgosService = {
  listar: (soloActivos = false) => api.get(base, { params: { soloActivos } }).then(r => r.data),
  crear: (datos) => api.post(base, datos).then(r => r.data),
  actualizar: (id, datos) => api.put(`${base}/${id}`, datos).then(r => r.data),
  eliminar: (id) => api.delete(`${base}/${id}`),
  ordenar: (ids) => api.put(`${base}/orden`, ids).then(r => r.data),
  // Estudio de ejemplo de Antecedentes (datos ficticios del PDF de docs/antecedentes).
  cargarEjemploAntecedentes: () => api.post('/catalogo/ejemplos/antecedentes').then(r => r.data),
}

/** "LEG-05 · Proceso penal activo… (Crítico)" — cómo se muestra un hallazgo en una lista. */
export function etiquetaHallazgo(h) {
  if (!h) return ''
  const valor = h.tipo === 'CRITICO' ? 'Crítico' : h.tipo === 'ALERTA' ? 'Alerta' : `${h.puntos} pts`
  return `${h.codigo} · ${h.descripcion} (${valor})`
}

export default hallazgosService
