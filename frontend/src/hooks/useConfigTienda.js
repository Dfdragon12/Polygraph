import { useEffect, useState } from 'react'
import pagosService from '../services/pagosService'

const INICIAL = { cargada: false, cobroConIva: false, ivaPorcentaje: 0, tiendaPorPlanes: false, vigenciaPorPlan: false, coberturaPorSede: false, pospagoPlan100: false, altaPospagoControlada: false, moraAutomatica: false }

let promesa = null            // una sola consulta por carga de la página; se vuelve a pedir si falla
let ultimo = INICIAL

/** Interruptores de la tienda (IVA, planes, vigencia, cobertura). Hasta que llegan, todo está apagado: se ve la tienda actual. */
export function useConfigTienda() {
  const [config, setConfig] = useState(ultimo)
  useEffect(() => {
    let vivo = true
    if (!promesa) {
      promesa = pagosService.obtenerConfiguracionTienda()
        .then((c) => { ultimo = { ...INICIAL, ...c, cargada: true }; return ultimo })
        .catch(() => { promesa = null; return INICIAL })
    }
    promesa.then((c) => { if (vivo) setConfig(c) })
    return () => { vivo = false }
  }, [])
  return config
}
