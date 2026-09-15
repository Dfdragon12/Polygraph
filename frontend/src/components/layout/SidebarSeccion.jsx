import { useState } from 'react'
import { NavLink } from 'react-router-dom'

/** Un grupo del menú lateral (ej. "General" con Dashboard/Equipo/Clientes debajo) — el título
 *  funciona como acordeón para ocultar/mostrar sus enlaces, para que el menú no se sienta tan
 *  largo cuando un rol tiene muchas secciones. Compartido entre Admin/Interno/Cliente para que
 *  los tres layouts se comporten igual — antes cada uno repetía este bloque sin poder colapsarlo.
 *  Cada grupo recuerda su propio abierto/cerrado mientras el layout sigue montado (no persiste
 *  entre sesiones a propósito: es solo para despejar la vista, no una preferencia que valga la
 *  pena recordar entre visitas). */
export default function SidebarSeccion({ titulo, items, collapsed, badges = {}, onNavClick }) {
  const [abierta, setAbierta] = useState(true)

  // Con el sidebar en modo ícono (collapsed) no hay título que mostrar ni nada que acordeonar —
  // siempre se ven todos los íconos.
  if (collapsed) {
    return (
      <div className="space-y-0.5">
        {items.map(({ ruta, etiqueta, icono, badgeKey }) => {
          const badge = badgeKey ? badges[badgeKey] : null
          return (
            <NavLink key={ruta} to={ruta} onClick={onNavClick} title={etiqueta}
              className={({ isActive }) =>
                `flex items-center justify-center rounded-lg text-sm p-2.5 transition-colors ${
                  isActive ? 'bg-primary-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }>
              <span className="text-base flex-shrink-0 relative">
                {icono}
                {badge > 0 && <span className="absolute -top-1 -right-1.5 w-3.5 h-3.5 bg-amber-500 rounded-full" />}
              </span>
            </NavLink>
          )
        })}
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
                <span className="text-base flex-shrink-0">{icono}</span>
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
