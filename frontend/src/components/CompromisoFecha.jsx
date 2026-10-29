// La fecha de compromiso que ve el CLIENTE (AJ-04). Mientras el reloj de la solicitud está en pausa la fecha todavía no es real (se correrá los días hábiles
// que dure la pausa), así que se dice «Por definir» y por qué. El servidor sigue guardando la fecha como siempre: solo cambia lo que se muestra.
// Las pantallas internas (gestor, programador, administrador) no usan este componente.

const NOTA_PAUSA = {
  CANDIDATO: 'Se fija cuando el candidato envíe su hoja de vida',
  REPROGRAMACION: 'Se ajusta cuando se reprograme la cita',
  CLIENTE: 'Se fija cuando respondas lo que te pedimos',
}

/** null si la fecha es real; si el reloj está en pausa, { nota } con el motivo. */
export function compromisoPorDefinir(tiempo) {
  if (tiempo?.estadoTiempo !== 'EN_PAUSA') return null
  return { nota: NOTA_PAUSA[tiempo.pausaOrigen] ?? NOTA_PAUSA.CANDIDATO }
}

/** El texto de la fecha: «Por definir» en pausa; si no, la fecha ya formateada por quien llama. */
export function textoCompromiso(tiempo, fechaFormateada) {
  return compromisoPorDefinir(tiempo) ? 'Por definir' : fechaFormateada
}

/** Nota bajo la fecha cuando está por definir (nada si la fecha es real). */
export function NotaCompromiso({ tiempo, className = '' }) {
  const p = compromisoPorDefinir(tiempo)
  return p ? <span className={`block text-[11px] text-gray-600 ${className}`}>{p.nota}</span> : null
}
