import api from './api'

// La Agenda del equipo (SE-08). Todo sale del servidor, con la hora de Colombia.
const agendaService = {
  // { fecha, esDiaHabil, motivoNoHabil, equipo: [{ idUsuario, nombre, apellido, rol, minutosOcupados, minutosLibres, minutosBloqueados, citas, atrasados }] }
  equipo: (fecha) => api.get('/gestor/agenda/equipo', { params: { fecha } }).then(r => r.data),

  // { ..., bloques: [{ tipo: 'OCUPADO'|'LIBRE'|'BLOQUEADO', inicio, fin, minutos, motivo, idSubproceso, subproceso, evaluado, idServicio }] }
  diaDePersona: (idUsuario, fecha) => api.get(`/gestor/agenda/equipo/${idUsuario}/dia`, { params: { fecha } }).then(r => r.data),

  // Lo que estaba programado para un día ya pasado y no se hizo («Atrasado en agenda»).
  paraReprogramar: () => api.get('/gestor/agenda/para-reprogramar').then(r => r.data),

  // [{ fecha, motivo: 'Sábado'|'Domingo'|'Festivo' }]
  diasNoHabiles: (desde, hasta) => api.get('/gestor/agenda/dias-no-habiles', { params: { desde, hasta } }).then(r => r.data),
}

export default agendaService
