// PF-01 · Las 19 secciones de la plantilla "Poligrafía pre empleo" se reagrupan en 4 fases, en el orden
// real de la sesión (no en el del informe). Los datos que se guardan son los mismos: solo cambia en qué
// lugar de la pantalla se capturan. `s` = clave de la sección; `fija` = origen fijo de una tabla de la
// hoja de vida (educacion, experiencia_laboral); `cols` = solo esas columnas de la sección (la sección
// se reparte entre varios temas); `resto` = las columnas de la sección que ningún otro tema reclamó.
// Las secciones/columnas que calcula la plataforma (resultado, hallazgos, alertas) viven solo en la fase 4.

export const FASES = [
  {
    id: 1, nombre: 'Antes de la prueba',
    temas: [
      { id: 'foto', titulo: 'Foto y cédula del evaluado', foto: true, partes: [{ s: 'datos_prueba', cols: ['ev_foto'] }] },
      { id: 'evaluado', titulo: 'Datos del evaluado', partes: [{ s: 'datos_prueba', resto: true }] },
      { id: 'idoneidad', titulo: '4. Idoneidad', partes: [{ s: 'idoneidad' }] },
      { id: 'autorizacion', titulo: 'Autorización firmada (anexo)', partes: [{ s: 'autorizacion' }] },
    ],
  },
  {
    id: 2, nombre: 'Entrevista',
    temas: [
      { id: 'familiar', titulo: '5. Entorno familiar', partes: [{ s: 'entorno_familiar' }] },
      { id: 'educacion', titulo: '6. Educación', partes: [{ fija: 'educacion' }, { s: 'estudios_obs' }] },
      { id: 'financiera', titulo: '7. Historia financiera', partes: [{ s: 'historia_financiera' }] },
      { id: 'vehiculos', titulo: '8. Vehículos', partes: [{ s: 'vehiculos' }] },
      { id: 'laboral', titulo: '9. Experiencia laboral', partes: [{ fija: 'experiencia_laboral' }, { s: 'empleos_obs' }] },
      { id: 'antecedentes', titulo: '10. Antecedentes laborales', partes: [{ s: 'antecedentes_laborales' }] },
      { id: 'areas', titulo: '11. Áreas complementarias', partes: [{ s: 'areas' }] },
      { id: 'admisiones', titulo: 'Otras admisiones', partes: [{ s: 'admisiones' }, { s: 'admisiones_sensibles' }] },
    ],
  },
  {
    id: 3, nombre: 'La prueba',
    temas: [
      { id: 'preguntas', titulo: 'Preguntas relevantes', partes: [{ s: 'preguntas' }] },
      {
        id: 'estado', titulo: 'Estado del examen',
        partes: [{ s: 'datos_prueba', cols: ['examen_estado', 'pol_contramedidas', 'pol_contramedidas_det', 'pol_dificultad_instr', 'pol_dificultad_instr_det', 'pol_puede_repetir'] }],
      },
      { id: 'tecnica', titulo: 'Fecha, horas, técnica y equipo', partes: [{ s: 'datos_prueba', cols: ['pol_fecha', 'pol_hora_inicio', 'pol_hora_fin'] }, { s: 'firma', cols: ['pol_tecnica', 'pol_equipo'] }] },
    ],
  },
  {
    id: 4, nombre: 'Revisión y envío',
    temas: [
      { id: 'conclusion', titulo: '12. Conclusión y firma', partes: [{ s: 'firma', resto: true }] },
      {
        id: 'calculado', titulo: 'Revisión de lo calculado', soloLectura: true,
        partes: [{ s: 'datos_prueba', cols: ['pol_resultado'] }, { s: 'resultado_servicio' }, { s: 'hallazgos' }, { s: 'alertas' }],
      },
      { id: 'informe', titulo: 'Vista del informe', informe: true, partes: [] },
    ],
  },
]

export const TODOS_LOS_TEMAS = FASES.flatMap(f => f.temas.map(t => ({ ...t, fase: f.id })))

// Es el formato de poligrafía pre empleo si trae las secciones que identifican esta plantilla.
export function esFormatoPoligrafia(secciones = []) {
  const claves = new Set(secciones.map(s => s.clave))
  return ['datos_prueba', 'preguntas', 'antecedentes_laborales', 'areas'].every(c => claves.has(c))
}

// Columnas de una sección que cada tema reclama por nombre; el "resto" de una sección son las demás.
const RECLAMADAS = (() => {
  const m = {}
  TODOS_LOS_TEMAS.forEach(t => (t.partes ?? []).forEach(p => {
    if (p.s && p.cols) {
      m[p.s] = m[p.s] ?? new Set()
      p.cols.forEach(c => m[p.s].add(c))
    }
  }))
  return m
})()

// Para un tema: lista de { seccion, columnas } editables (CAMPOS/TABLA) y de tablas fijas de la hoja de vida.
export function contenidoDelTema(tema, secciones, tablasFijas) {
  const partes = []
  ;(tema.partes ?? []).forEach(p => {
    if (p.fija) {
      const fija = (tablasFijas ?? []).find(t => t.clave === p.fija)
      if (fija) partes.push({ fija, validacion: secciones.find(s => s.origenFijoClave === p.fija) ?? null })
      return
    }
    const seccion = secciones.find(s => s.clave === p.s && !s.origenFijoClave)
    if (!seccion) return
    let columnas = seccion.columnas
    if (p.cols) columnas = columnas.filter(c => p.cols.includes(c.clave))
    else if (p.resto) columnas = columnas.filter(c => !RECLAMADAS[p.s]?.has(c.clave))
    if (columnas.length > 0) partes.push({ seccion, columnas })
  })
  return partes
}
