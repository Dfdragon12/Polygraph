// NS-07: mismas reglas que DatosEvaluadoValidador del servidor (que es quien manda). Devuelve { campo: mensaje } solo con lo que falla.
export const CRITICIDADES = [
  { valor: 'ESTANDAR', titulo: 'Estándar', detalle: 'operativo o de bajo acceso' },
  { valor: 'SENSIBLE', titulo: 'Sensible', detalle: 'maneja información o dinero' },
  { valor: 'CRITICO', titulo: 'Crítico', detalle: 'alta confianza o seguridad' },
]

// pideDireccion: algún servicio elegido incluye una visita (NS-08) y la dirección de la visita es obligatoria.
export function validarEvaluado(e, pideDireccion = false) {
  const t = (v) => (v ?? '').toString().trim()
  const errores = {}
  if (!t(e.cedula)) errores.cedula = 'La cédula es obligatoria.'
  else if (!/^\d{5,15}$/.test(t(e.cedula))) errores.cedula = 'La cédula debe ser numérica, sin puntos ni espacios.'
  if (!t(e.nombres)) errores.nombres = 'Los nombres son obligatorios.'
  if (!t(e.apellidos)) errores.apellidos = 'Los apellidos son obligatorios.'
  if (!t(e.cargo)) errores.cargo = 'El cargo es obligatorio.'
  if (!t(e.criticidadCargo)) errores.criticidadCargo = 'Elige la criticidad del cargo.'
  if (!t(e.celular)) errores.celular = 'El celular es obligatorio.'
  else if (!/^3\d{9}$/.test(t(e.celular))) errores.celular = 'El celular debe tener 10 dígitos y empezar por 3.'
  if (!t(e.email)) errores.email = 'El correo es obligatorio.'
  else if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(t(e.email))) errores.email = 'El correo no tiene un formato válido.'
  if (e.idCiudad == null) errores.idCiudad = 'Elige la ciudad.'
  if (pideDireccion) {
    if (!t(e.direccionVisita)) errores.direccionVisita = 'La dirección de la visita es obligatoria.'
    else if (t(e.direccionVisita).length < 6) errores.direccionVisita = 'La dirección de la visita está incompleta (calle o carrera, número y barrio).'
  }
  return errores
}

// NS-08: cómo se llama la ciudad según el servicio. Una visita manda sobre la prueba (si el servicio tiene ambas se dice las dos).
export function rotuloCiudad(proceso) {
  if (proceso?.requiereDireccion && proceso?.ciudadEsDePrueba) return 'Ciudad de la visita y de la prueba'
  if (proceso?.requiereDireccion) return 'Ciudad de la visita'
  if (proceso?.ciudadEsDePrueba) return 'Ciudad donde presentará la prueba'
  return 'Ciudad'
}
