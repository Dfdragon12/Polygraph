import { useEffect, useState } from 'react'
import agendaService from '../services/agendaService'

// Los sábados, domingos y festivos de un rango (AAAA-MM-DD a AAAA-MM-DD): el cronograma no los muestra como días de trabajo (SE-08).
// Devuelve un Map fecha → motivo ('Sábado' | 'Domingo' | 'Festivo'). Mientras carga, o si falla, el mapa viene vacío y nada se marca.
export function useDiasNoHabiles(desde, hasta) {
  const [mapa, setMapa] = useState(() => new Map())
  useEffect(() => {
    if (!desde || !hasta) return
    let vigente = true
    agendaService.diasNoHabiles(desde, hasta)
      .then(l => { if (vigente) setMapa(new Map(l.map(d => [d.fecha, d.motivo]))) })
      .catch(() => { if (vigente) setMapa(new Map()) })
    return () => { vigente = false }
  }, [desde, hasta])
  return mapa
}
