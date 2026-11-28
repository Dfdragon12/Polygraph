import { useEffect, useState } from 'react'
import api from '../services/api'

// El catálogo de los siete estados (nombre, color, orden y texto para el cliente) lo da el servidor; las pantallas lo leen de aquí y nunca muestran el código
// interno (EN_EJECUCION...). Se pide una sola vez por sesión.
let promesa = null
let cache = null

function cargar() {
  if (!promesa) promesa = api.get('/catalogo/estados-subproceso').then(r => { cache = r.data; return cache }).catch(() => { promesa = null; return [] })
  return promesa
}

/** { estados: [{codigo, nombre, color, orden, textoCliente}], de: (codigo) => estado | undefined, nombre: (codigo) => 'En ejecución' } */
export function useEstadosSubproceso() {
  const [estados, setEstados] = useState(cache ?? [])
  useEffect(() => {
    if (cache) return
    let vigente = true
    cargar().then(l => { if (vigente) setEstados(l) })
    return () => { vigente = false }
  }, [])
  const de = (codigo) => estados.find(e => e.codigo === codigo)
  return { estados, de, nombre: (codigo) => de(codigo)?.nombre ?? '' }
}
