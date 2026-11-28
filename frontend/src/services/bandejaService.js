import api from './api'

// PB-05: «Mi bandeja del día» del gestor y lo que necesita la pantalla Solicitudes. Todo sale del servidor (con el alcance de sus clientes).
const bandejaService = {
  bandeja: () => api.get('/gestor/bandeja').then(r => r.data),

  // Riesgo detectado por periodo: HOY | SEMANA | MES | ANIO | TOTAL
  riesgo: (periodo) => api.get('/gestor/bandeja/riesgo', { params: { periodo } }).then(r => r.data),

  // «Avisar al programador»: le llega una notificación con las citas sin agendar y queda registrado. Sin idServicio avisa de todas.
  avisarProgramador: (idServicio) =>
    api.post('/gestor/bandeja/avisar-programador', null, { params: { idServicio: idServicio ?? undefined } }).then(r => r.data),

  // Chips de tiempo de Solicitudes: { conteo: { VENCIDAS, VENCE_HOY, VENCE_MANANA, A_TIEMPO, EN_PAUSA }, vencidasPorResponsable }
  tiempos: ({ idCliente, idProceso, idResponsable } = {}) =>
    api.get('/gestor/solicitudes/tiempos', { params: { idCliente, idProceso, idResponsable } }).then(r => r.data),

  // Opciones de los selectores Cliente, Estudio y Responsable
  opcionesFiltro: () => api.get('/gestor/solicitudes/filtros').then(r => r.data),
}

export default bandejaService
