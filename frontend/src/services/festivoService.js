import api from './api'

// Festivos de Colombia que usa el cálculo de días hábiles. Los de la ley salen calculados; el administrador desactiva uno
// o agrega un día no laborable propio.
const festivoService = {
  listar: (anio) => api.get('/admin/festivos', { params: { anio } }).then(r => r.data),

  // activo=false desactiva un festivo de la ley (o quita un día propio); activo=true lo reactiva (un día propio pide descripción).
  cambiar: (fecha, activo, descripcion) =>
    api.put(`/admin/festivos/${fecha}`, { activo, descripcion }).then(r => r.data),
}

export default festivoService
