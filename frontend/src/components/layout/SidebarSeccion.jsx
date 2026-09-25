import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { NavLink, useLocation } from 'react-router-dom'

/** Un ítem individual en modo ícono (sidebar colapsado) — para secciones de un solo enlace, donde
 *  no hace falta agrupar nada: el ícono ya es la acción completa. */
function ItemColapsado({ ruta, etiqueta, icono, badge, onNavClick }) {
  return (
    <NavLink to={ruta} onClick={onNavClick} title={etiqueta}
      className={({ isActive }) =>
        `flex items-center justify-center rounded-lg text-sm p-2.5 transition-colors ${
          isActive ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
        }`
      }>
      <span className="flex-shrink-0 relative">
        {icono}
        {badge > 0 && <span className="absolute -top-1 -right-1.5 w-3.5 h-3.5 bg-amber-500 rounded-full" />}
      </span>
    </NavLink>
  )
}

/** Una sección con varias acciones, en modo ícono — un solo botón representa al grupo (con el
 *  ícono del primer ítem, ej. "General" muestra el ícono de Dashboard) y, al pasar el mouse por
 *  encima, se despliega la lista de acciones de esa sección como un flyout. Se renderiza en un
 *  portal a document.body porque el <nav> del sidebar tiene overflow-y-auto — eso recorta
 *  cualquier cosa que se salga por el costado si no se saca del flujo normal. */
function SeccionColapsada({ titulo, items, badges, onNavClick }) {
  const [abierto, setAbierto] = useState(false)
  const [pos, setPos] = useState(null)
  const ref = useRef(null)
  const cierreTimeout = useRef(null)
  const { pathname } = useLocation()

  const cancelarCierre = () => {
    if (cierreTimeout.current) {
      clearTimeout(cierreTimeout.current)
      cierreTimeout.current = null
    }
  }

  // El ícono y el flyout no son vecinos en el DOM real (el flyout va en un portal a document.body),
  // así que al mover el mouse del uno al otro hay una fracción de segundo donde no está sobre
  // ninguno — sin este margen, eso cerraba el flyout antes de llegar y tocaba pasar el mouse dos
  // veces. Con el timeout, un mouseenter mientras el cierre está pendiente lo cancela.
  const abrir = () => {
    cancelarCierre()
    const r = ref.current.getBoundingClientRect()
    setPos({ top: r.top, left: r.right + 8 })
    setAbierto(true)
  }
  const programarCierre = () => {
    cancelarCierre()
    cierreTimeout.current = setTimeout(() => setAbierto(false), 200)
  }

  useEffect(() => cancelarCierre, [])

  const activa = items.some(i => pathname === i.ruta || pathname.startsWith(i.ruta + '/'))
  const tieneBadge = items.some(i => i.badgeKey && badges[i.badgeKey] > 0)

  return (
    <div ref={ref} onMouseEnter={abrir} onMouseLeave={programarCierre}>
      <div title={titulo}
        className={`flex items-center justify-center rounded-lg text-sm p-2.5 transition-colors cursor-default ${
          activa ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
        }`}>
        <span className="flex-shrink-0 relative">
          {items[0]?.icono}
          {tieneBadge && <span className="absolute -top-1 -right-1.5 w-3.5 h-3.5 bg-amber-500 rounded-full" />}
        </span>
      </div>

      {abierto && pos && createPortal(
        <div style={{ position: 'fixed', top: pos.top, left: pos.left }}
          onMouseEnter={abrir} onMouseLeave={programarCierre}
          className="z-50 w-52 bg-slate-800 text-white rounded-lg shadow-xl ring-1 ring-slate-700 py-1.5 animate-fade-in">
          <p className="px-3 pb-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">{titulo}</p>
          {items.map(({ ruta, etiqueta, icono, badgeKey }) => {
            const badge = badgeKey ? badges[badgeKey] : null
            return (
              <NavLink key={ruta} to={ruta} onClick={() => { cancelarCierre(); setAbierto(false); onNavClick?.() }}
                className={({ isActive }) =>
                  `flex items-center gap-2.5 px-3 py-2 text-sm transition-colors ${
                    isActive ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-700 hover:text-white'
                  }`
                }>
                <span className="flex-shrink-0">{icono}</span>
                <span className="flex-1">{etiqueta}</span>
                {badge > 0 && (
                  <span className="bg-amber-500 text-white text-xs font-bold rounded-full px-1.5 py-0.5 leading-none">
                    {badge}
                  </span>
                )}
              </NavLink>
            )
          })}
        </div>,
        document.body,
      )}
    </div>
  )
}

/** Un grupo del menú lateral (ej. "General" con Dashboard/Equipo/Clientes debajo) — el título
 *  funciona como acordeón para ocultar/mostrar sus enlaces, para que el menú no se sienta tan
 *  largo cuando un rol tiene muchas secciones. Compartido entre Admin/Interno/Cliente para que
 *  los tres layouts se comporten igual — antes cada uno repetía este bloque sin poder colapsarlo.
 *  Cada grupo recuerda su propio abierto/cerrado mientras el layout sigue montado (no persiste
 *  entre sesiones a propósito: es solo para despejar la vista, no una preferencia que valga la
 *  pena recordar entre visitas). */
export default function SidebarSeccion({ titulo, items, collapsed, badges = {}, onNavClick }) {
  const [abierta, setAbierta] = useState(true)

  // Con el sidebar en modo ícono (collapsed): una sección de un solo ítem se ve como su propio
  // ícono clickeable; una sección con varias acciones se agrupa en un solo ícono con flyout al
  // pasar el mouse (ver SeccionColapsada) — mostrar cada acción suelta ahí amontonaba demasiado.
  if (collapsed) {
    if (items.length === 1) {
      const item = items[0]
      return (
        <div className="space-y-0.5">
          <ItemColapsado {...item} badge={item.badgeKey ? badges[item.badgeKey] : null} onNavClick={onNavClick} />
        </div>
      )
    }
    return (
      <div className="space-y-0.5">
        <SeccionColapsada titulo={titulo} items={items} badges={badges} onNavClick={onNavClick} />
      </div>
    )
  }

  return (
    <div>
      <button type="button" onClick={() => setAbierta(v => !v)}
        aria-expanded={abierta}
        className="w-full flex items-center justify-between gap-2 px-3 mb-1.5 text-xs font-semibold text-slate-500 uppercase tracking-wider hover:text-slate-300 transition-colors">
        {titulo}
        <svg className={`h-3 w-3 flex-shrink-0 transition-transform duration-200 ${abierta ? '' : '-rotate-90'}`}
          fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {abierta && (
        <div className="space-y-0.5">
          {items.map(({ ruta, etiqueta, icono, badgeKey }) => {
            const badge = badgeKey ? badges[badgeKey] : null
            return (
              <NavLink key={ruta} to={ruta} onClick={onNavClick}
                className={({ isActive }) =>
                  `flex items-center rounded-lg text-sm transition-colors gap-3 px-3 py-2.5 ${
                    isActive ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                  }`
                }>
                <span className="flex-shrink-0">{icono}</span>
                <span className="flex-1 flex items-center justify-between gap-2">
                  {etiqueta}
                  {badge > 0 && (
                    <span className="bg-amber-500 text-white text-xs font-bold rounded-full px-1.5 py-0.5 leading-none">
                      {badge}
                    </span>
                  )}
                </span>
              </NavLink>
            )
          })}
        </div>
      )}
    </div>
  )
}
