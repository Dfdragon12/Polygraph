import { useCallback, useEffect, useState } from 'react'
import semaforoService from '../services/semaforoService'
import { useCambios } from './useCambios'

// La serie de estados de un tablero (SE-09), sacada del MISMO resumen del servidor que usa el semáforo: mismos nombres, colores y conteos en todas las
// pantallas. Devuelve [{ clave, name, value, color }] en el orden del semáforo; los dashboards ya la saben dibujar.
export function useSemaforoSerie() {
  const [serie, setSerie] = useState(null)
  const cargar = useCallback(() => {
    semaforoService.resumen({}).then(d => {
      const subprocesos = d.unidad === 'SUBPROCESOS'
      setSerie(d.estados.map(e => ({ clave: e.codigo, name: e.nombre, value: subprocesos ? e.subprocesos : e.servicios, color: e.color })))
    }).catch(() => setSerie([]))
  }, [])
  useEffect(() => { cargar() }, [cargar])
  useCambios(cargar)
  return serie ?? []
}
