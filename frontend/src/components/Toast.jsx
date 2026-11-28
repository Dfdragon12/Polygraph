import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'

// PC-06 · Avisos que se anuncian.
//  · Viven en DOS contenedores que ya existen vacíos desde que carga la página —role="status" aria-live="polite" para los éxitos y role="alert" para los
//    errores—: un lector de pantalla solo anuncia lo que aparece DENTRO de una región viva ya presente, no una región que nace con su texto.
//  · Duran 7 s y se detienen mientras el cursor o el foco están encima (al soltarlos queda al menos 3 s para terminar de leer).
//  · El verde es bg-green-700 con texto blanco (5,0:1; el bg-green-600 anterior daba ~3,3:1) y el rojo bg-red-600 (4,8:1).
//  · El ícono ✓/✕ es decorativo (aria-hidden) y la «×» se llama «Cerrar aviso», con 44 px de área.

const DURACION_MS = 7000
const MINIMO_TRAS_PAUSA_MS = 3000

function region(id, rol) {
  let el = document.getElementById(id)
  if (!el) {
    el = document.createElement('div')
    el.id = id
    el.setAttribute('role', rol)
    el.setAttribute('aria-live', rol === 'alert' ? 'assertive' : 'polite')
    el.setAttribute('aria-atomic', 'true')
    el.className = 'fixed bottom-6 right-6 z-[300] max-w-[calc(100vw-3rem)] pointer-events-none'
    document.body.appendChild(el)
  }
  return el
}

/** Crea las dos regiones vivas (vacías). Se llama al cargar el módulo y otra vez desde App, antes de que exista cualquier aviso. */
export function prepararAvisos() {
  if (typeof document === 'undefined') return
  region('avisos-estado', 'status')
  region('avisos-alerta', 'alert')
}
prepararAvisos()

export default function Toast({ mensaje, tipo = 'exito', onClose }) {
  const esExito = tipo === 'exito'
  const cerrar = useRef(onClose)
  cerrar.current = onClose           // un onClose nuevo en cada render de la pantalla NO reinicia la cuenta
  const temporizador = useRef(null)
  const inicio = useRef(0)
  const restante = useRef(DURACION_MS)
  const retenido = useRef(new Set())

  const armar = () => {
    clearTimeout(temporizador.current)
    inicio.current = Date.now()
    temporizador.current = setTimeout(() => cerrar.current?.(), restante.current)
  }
  const retener = (quien) => {
    retenido.current.add(quien)
    if (temporizador.current) {
      clearTimeout(temporizador.current)
      temporizador.current = null
      restante.current = Math.max(restante.current - (Date.now() - inicio.current), MINIMO_TRAS_PAUSA_MS)
    }
  }
  const soltar = (quien) => {
    retenido.current.delete(quien)
    if (retenido.current.size === 0 && !temporizador.current) armar()
  }

  useEffect(() => {
    // Si el cursor o el foco ya estaban encima cuando el aviso nació (el cursor se quedó donde se hizo clic y el aviso apareció debajo), la cuenta queda detenida
    // desde el principio: NO se borra esa retención al montar.
    restante.current = DURACION_MS
    if (retenido.current.size === 0) armar()
    return () => { clearTimeout(temporizador.current); temporizador.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mensaje, tipo])

  prepararAvisos()
  return createPortal(
    <div
      onMouseEnter={() => retener('cursor')} onMouseLeave={() => soltar('cursor')}
      onFocus={() => retener('foco')} onBlur={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) soltar('foco') }}
      className={`pointer-events-auto flex items-center gap-3 pl-5 pr-2 py-2 min-h-[52px] rounded-xl shadow-lg text-sm font-medium animate-fade-in ${
        esExito ? 'bg-green-700 text-white' : 'bg-red-600 text-white'
      }`}>
      <span aria-hidden="true" className="text-base">{esExito ? '✓' : '✕'}</span>
      <span>{mensaje}</span>
      <button type="button" onClick={onClose} aria-label="Cerrar aviso"
        className="ml-1 min-h-[44px] min-w-[44px] rounded-lg text-base leading-none opacity-80 hover:opacity-100 focus-visible:opacity-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-white">
        <span aria-hidden="true">×</span>
      </button>
    </div>,
    document.getElementById(esExito ? 'avisos-estado' : 'avisos-alerta')
  )
}
