import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

function IconoPuntos({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <circle cx="12" cy="5" r="1.75" />
      <circle cx="12" cy="12" r="1.75" />
      <circle cx="12" cy="19" r="1.75" />
    </svg>
  )
}

const ANCHO_MENU = 208 // w-52

/**
 * Menú de acciones de una fila de tabla — un solo botón de 3 puntos gris que despliega las
 * opciones aplicables, en vez de varios botones de ícono sueltos con colores. Estilo estándar de
 * las tablas del sistema (ver Facturacion.jsx / GestionCuentas.jsx, donde se usó por primera vez).
 *
 * El menú se renderiza en un portal a `document.body` con `position: fixed`, calculando su
 * posición desde el botón — así escapa de cualquier ancestro con `overflow-x-auto` (que, por la
 * regla de CSS, también recorta verticalmente aunque solo se haya puesto overflow en X). Además
 * decide abrir hacia arriba en vez de hacia abajo si no hay espacio suficiente debajo del botón.
 *
 * `opciones`: array de { label, icono, onClick, destacado?, disabled?, title? } — un elemento
 * `false`/`null`/`undefined` se filtra solo, así se puede armar la lista condicionalmente con `&&`
 * sin envolver en un if. `destacado` es una clase de color opcional (ej. "text-red-600") para una
 * opción que lo necesite (ej. "Eliminar"); si no se pasa, usa el gris por defecto. `disabled` deja
 * la opción visible pero no clickeable (ej. "Eliminar" bloqueado porque el registro está en uso);
 * `title` en ese caso explica el porqué en el tooltip.
 */
function MenuAcciones({ opciones }) {
  const [abierto, setAbierto] = useState(false)
  const [posicion, setPosicion] = useState(null)
  const botonRef = useRef(null)
  const menuRef = useRef(null)
  const visibles = opciones.filter(Boolean)

  const calcularPosicion = () => {
    const rect = botonRef.current.getBoundingClientRect()
    const altoEstimado = visibles.length * 34 + 8
    const espacioAbajo = window.innerHeight - rect.bottom
    const haciaArriba = espacioAbajo < altoEstimado && rect.top > altoEstimado

    let left = rect.right - ANCHO_MENU
    left = Math.max(8, Math.min(left, window.innerWidth - ANCHO_MENU - 8))

    setPosicion(
      haciaArriba
        ? { left, bottom: window.innerHeight - rect.top + 4, top: null }
        : { left, top: rect.bottom + 4, bottom: null }
    )
  }

  const alternar = () => {
    if (abierto) { setAbierto(false); return }
    calcularPosicion()
    setAbierto(true)
  }

  useEffect(() => {
    if (!abierto) return
    const cerrarPorClickFuera = (e) => {
      if (botonRef.current?.contains(e.target) || menuRef.current?.contains(e.target)) return
      setAbierto(false)
    }
    // Se cierra en scroll/resize en vez de reposicionarse en vivo — más simple y evita que el
    // menú quede "flotando" desalineado de su botón mientras la tabla se desplaza.
    const cerrar = () => setAbierto(false)
    document.addEventListener('mousedown', cerrarPorClickFuera)
    window.addEventListener('scroll', cerrar, true)
    window.addEventListener('resize', cerrar)
    return () => {
      document.removeEventListener('mousedown', cerrarPorClickFuera)
      window.removeEventListener('scroll', cerrar, true)
      window.removeEventListener('resize', cerrar)
    }
  }, [abierto])

  if (!visibles.length) return null

  return (
    <div className="relative inline-block">
      <button type="button" ref={botonRef} onClick={alternar}
        className="p-1.5 rounded-md text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors">
        <IconoPuntos />
      </button>
      {abierto && posicion && createPortal(
        <div
          ref={menuRef}
          className="fixed z-[100] w-52 bg-white rounded-lg border border-gray-200 shadow-lg py-1 text-left"
          style={{
            left: posicion.left,
            top: posicion.top ?? undefined,
            bottom: posicion.bottom ?? undefined,
          }}
        >
          {visibles.map((op, i) => (
            <button key={i} type="button" disabled={op.disabled} title={op.title}
              onClick={() => { setAbierto(false); op.onClick() }}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs transition-colors ${
                op.disabled ? 'text-gray-300 cursor-not-allowed' : `hover:bg-gray-50 ${op.destacado ?? 'text-gray-700'}`}`}>
              {op.icono}
              {op.label}
            </button>
          ))}
        </div>,
        document.body
      )}
    </div>
  )
}

export default MenuAcciones
