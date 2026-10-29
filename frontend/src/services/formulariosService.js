import api from './api'

// Genérico — sirve para cualquier subproceso (tipos_progreso), no solo Antecedentes. El
// administrador arma la plantilla (secciones/columnas/filas sugeridas) desde admin/catálogo →
// Plantilla; el analista la llena desde su formulario.
const formulariosService = {
  obtenerEstructura: (idTipoProgreso) =>
    api.get(`/catalogo/formularios/${idTipoProgreso}/estructura`).then(r => r.data),

  guardarEstructura: (idTipoProgreso, secciones) =>
    api.put(`/catalogo/formularios/${idTipoProgreso}/estructura`, { secciones }).then(r => r.data),

  // Qué secciones/columnas con respuestas ya guardadas se archivarían con este cambio — para
  // avisar ANTES de guardar de verdad.
  verificarCambiosEstructura: (idTipoProgreso, secciones) =>
    api.post(`/catalogo/formularios/${idTipoProgreso}/verificar-cambios`, { secciones }).then(r => r.data),

  // Campos "antiguos" (archivados) — el panel del admin para verlos y, si quiere, restaurarlos.
  obtenerArchivados: (idTipoProgreso) =>
    api.get(`/catalogo/formularios/${idTipoProgreso}/archivados`).then(r => r.data),

  restaurarSeccion: (idTipoProgreso, idSeccion) =>
    api.put(`/catalogo/formularios/${idTipoProgreso}/secciones/${idSeccion}/restaurar`),

  restaurarColumna: (idTipoProgreso, idColumna) =>
    api.put(`/catalogo/formularios/${idTipoProgreso}/columnas/${idColumna}/restaurar`),

  obtenerFormulario: (idServicioSubproceso) =>
    api.get(`/gestor/formularios/subproceso/${idServicioSubproceso}`).then(r => r.data),

  // seccionesOmitidas: ids de las secciones opcionales que quien diligencia quitó (V212)
  guardarFormulario: (idServicioSubproceso, secciones, seccionesOmitidas = []) =>
    api.put(`/gestor/formularios/subproceso/${idServicioSubproceso}`, { secciones, seccionesOmitidas }).then(r => r.data),

  descargarDocumento: (idServicioSubproceso) =>
    api.get(`/gestor/formularios/subproceso/${idServicioSubproceso}/documento`, { responseType: 'blob' }).then(r => r.data),

  // Mismo documento que descargarDocumento (mismo .docx con los marcadores ya resueltos),
  // convertido a PDF en el backend — mismo diseño que armó el administrador en Word. Responde 202
  // con un trabajo de la cola: seguirlo con esperarTrabajo y bajar con
  // trabajoService.descargarResultado (ver services/trabajoService.js).
  solicitarDocumentoPdf: (idServicioSubproceso) =>
    api.post(`/gestor/formularios/subproceso/${idServicioSubproceso}/documento-pdf`).then(r => r.data),

  // Qué tan lleno está el formulario — para que gestor vea el avance sin abrir cada uno.
  progreso: (idServicioSubproceso) =>
    api.get(`/gestor/formularios/subproceso/${idServicioSubproceso}/progreso`).then(r => r.data),

  // Ficha del candidato del subproceso: datos agrupados + soportes que cargó (cruzados con los que
  // pide el proceso) — { grupos: [{ nombre, campos }], soportes: [...] }.
  // Aprobar (APROBADO) o devolver (DEVUELTO + observación) un soporte del candidato — devuelve
  // la ficha actualizada.
  revisarSoporte: (idServicioSubproceso, idDocumento, estado, observacion) =>
    api.patch(`/gestor/formularios/subproceso/${idServicioSubproceso}/soportes/${idDocumento}/revision`,
      { estado, observacion }).then(r => r.data),

  fichaCandidato: (idServicioSubproceso) =>
    api.get(`/gestor/formularios/subproceso/${idServicioSubproceso}/candidato`).then(r => r.data),

  descargarSoporte: (idServicioSubproceso, idDocumento) =>
    api.get(`/gestor/formularios/subproceso/${idServicioSubproceso}/soportes/${idDocumento}`, { responseType: 'blob' })
      .then(r => r.data),

  // Columnas tipo ARCHIVO — el analista sube el archivo apenas lo selecciona; la "ruta" que
  // devuelve se guarda como el valor de esa columna, igual que cualquier otro dato.
  subirArchivo: (idServicioSubproceso, archivo) => {
    const formData = new FormData()
    formData.append('archivo', archivo)
    return api.post(`/gestor/formularios/subproceso/${idServicioSubproceso}/archivo`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data.ruta)
  },

  descargarArchivo: (idServicioSubproceso, ruta) =>
    api.get(`/gestor/formularios/subproceso/${idServicioSubproceso}/archivo`, {
      params: { ruta }, responseType: 'blob',
    }).then(r => r.data),

  // Hoja de vida REAL del candidato (referencias, educación, experiencia laboral, inactividades)
  // — analista/poligrafista/visitador la corrigen/amplían aquí directamente; un cambio se ve en
  // cualquier otro servicio de esa misma persona, no es una copia por servicio.
  obtenerHojaVidaCandidato: (idCandidato) =>
    api.get(`/gestor/candidatos/${idCandidato}/hoja-vida`).then(r => r.data),

  guardarTablaHojaVidaCandidato: (idCandidato, clave, filas) =>
    api.put(`/gestor/candidatos/${idCandidato}/hoja-vida/${clave}`, { filas }).then(r => r.data),

  // Genera un .docx de ejemplo a partir de la estructura que se está editando en la plantilla
  // (aunque todavía no se haya guardado) — solo para admin/catálogo → Plantilla.
  vistaPrevia: (idTipoProgreso, secciones) =>
    api.post(`/catalogo/formularios/${idTipoProgreso}/vista-previa`, { secciones }, { responseType: 'blob' }).then(r => r.data),

  // Ruta del .docx con marcadores subido para este subproceso, si hay uno.
  obtenerConfigPlantilla: (idTipoProgreso) =>
    api.get(`/catalogo/formularios/${idTipoProgreso}/config`).then(r => r.data),

  // Campos del candidato disponibles para usar en CUALQUIER columna de la plantilla (ver
  // construirPayload → campoCandidato) — no es una config aparte, es una opción por columna.
  obtenerCamposCandidatoDisponibles: () =>
    api.get('/catalogo/campos-candidato').then(r => r.data),

  obtenerCamposClienteDisponibles: () =>
    api.get('/catalogo/campos-cliente').then(r => r.data),

  // Datos del estudio (N° de estudio, fecha de emisión, cliente, cargo, profesional).
  obtenerCamposServicioDisponibles: () =>
    api.get('/catalogo/campos-servicio').then(r => r.data),

  subirPlantillaWord: (idTipoProgreso, archivo) => {
    const formData = new FormData()
    formData.append('archivo', archivo)
    return api.post(`/catalogo/formularios/${idTipoProgreso}/plantilla-word`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data)
  },

  quitarPlantillaWord: (idTipoProgreso) =>
    api.delete(`/catalogo/formularios/${idTipoProgreso}/plantilla-word`),

  // El .docx tal cual se subió (nombre original) — para que el admin lo recupere si perdió su
  // copia local, lo edite en Word y lo vuelva a subir.
  descargarPlantillaWord: (idTipoProgreso) =>
    api.get(`/catalogo/formularios/${idTipoProgreso}/plantilla-word`, { responseType: 'blob' }).then(r => r.data),

  // Todas las versiones del .docx subidas para este subproceso — la activa y las archivadas,
  // con fecha de carga y quién la subió.
  obtenerHistorialPlantillaWord: (idTipoProgreso) =>
    api.get(`/catalogo/formularios/${idTipoProgreso}/plantilla-word/historial`).then(r => r.data),

  // Descarga una versión ARCHIVADA (no la activa — para esa está descargarPlantillaWord).
  descargarVersionHistorialPlantillaWord: (idTipoProgreso, idHistorial) =>
    api.get(`/catalogo/formularios/${idTipoProgreso}/plantilla-word/historial/${idHistorial}`, { responseType: 'blob' }).then(r => r.data),

  // Borra una versión ARCHIVADA del historial — el registro y el .docx en disco. No afecta la
  // versión activa (para esa está quitarPlantillaWord).
  eliminarVersionHistorialPlantillaWord: (idTipoProgreso, idHistorial) =>
    api.delete(`/catalogo/formularios/${idTipoProgreso}/plantilla-word/historial/${idHistorial}`),

  // Compara los marcadores del .docx subido contra las columnas/secciones que se están editando
  // ahora mismo (aunque no se hayan guardado todavía).
  analizarPlantillaWord: (idTipoProgreso, secciones) =>
    api.post(`/catalogo/formularios/${idTipoProgreso}/plantilla-word/analisis`, { secciones }).then(r => r.data),
}

export default formulariosService
