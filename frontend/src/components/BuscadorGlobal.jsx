import { useState, useEffect, useRef, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

// Minúsculas + sin tildes — mismo criterio que la búsqueda de Usuarios Internos, para que buscar
// "polizrafia" con o sin tilde encuentre igual "Poligrafía".
const RANGO_DIACRITICOS = new RegExp('[' + String.fromCharCode(0x0300) + '-' + String.fromCharCode(0x036f) + ']', 'g')
const normalizar = (s) => (s ?? '').toString().toLowerCase().normalize('NFD').replace(RANGO_DIACRITICOS, '')

// Orden fijo de las categorías de datos reales (backend /busqueda-global) — antes que las
// secciones de navegación, porque si alguien escribe "Carlos" quiere ver a Carlos, no una
// pantalla que se llame parecido.
const TITULO_TIPO = {
  CLIENTE: 'Clientes',
  CANDIDATO: 'Candidatos',
  SERVICIO: 'Servicios en curso',
  EQUIPO: 'Equipo Polygraph',
}
const ORDEN_TIPOS = ['CLIENTE', 'CANDIDATO', 'SERVICIO', 'EQUIPO']

function IconoBuscar({ className }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
    </svg>
  )
}

/** Paleta de comandos (estilo Cmd+K) para saltar a cualquier sección que el rol actual pueda ver
 *  — `secciones` ya viene filtrada por rol desde cada layout, así que esto nunca muestra nada a
 *  lo que el usuario no tenga acceso. Cada item puede traer `palabrasClave` (array de sinónimos)
 *  además de `etiqueta`, para que "empleados" también encuentre "Equipo Polygraph", por ejemplo.
 *  Búsqueda por varias palabras, insensible a tildes, agrupada por categoría, navegable con
 *  teclado (↑↓ + Enter).
 *
 *  Con `busquedaEntidades` (solo ADMIN_POLYGRAPH y GESTOR, ver BusquedaGlobalService en el
 *  backend) también busca DATOS reales — clientes, candidatos, servicios en curso, equipo —, no
 *  solo nombres de pantalla, y agrupa esos resultados aparte, arriba de los de navegación. */
export default function BuscadorGlobal({ secciones, busquedaEntidades = false }) {
  const [abierto, setAbierto] = useState(false)
  const [query, setQuery] = useState('')
  const [activo, setActivo] = useState(0)
  const [resultadosEntidades, setResultadosEntidades] = useState([])
  const [buscandoEntidades, setBuscandoEntidades] = useState(false)
  const inputRef = useRef(null)
  const navigate = useNavigate()

  const gruposNavegacion = useMemo(() => {
    const terminos = normalizar(query).split(/\s+/).filter(Boolean)
    return secciones
      .map(s => ({
        titulo: s.titulo,
        items: s.items.filter(item => {
          if (terminos.length === 0) return true
          const texto = normalizar([item.etiqueta, ...(item.palabrasClave ?? [])].join(' '))
          return terminos.every(t => texto.includes(t))
        }),
      }))
      .filter(g => g.items.length > 0)
  }, [secciones, query])

  const gruposEntidades = useMemo(() => {
    if (resultadosEntidades.length === 0) return []
    return ORDEN_TIPOS
      .map(tipo => ({
        titulo: TITULO_TIPO[tipo],
        items: resultadosEntidades
          .filter(r => r.tipo === tipo)
          .map((r, i) => ({ ...r, ruta: r.ruta, etiqueta: r.titulo, icono: r.icono, key: `${tipo}-${i}` })),
      }))
      .filter(g => g.items.length > 0)
  }, [resultadosEntidades])

  // Los de datos reales van primero — ver comentario del componente.
  const gruposFiltrados = useMemo(() => [...gruposEntidades, ...gruposNavegacion], [gruposEntidades, gruposNavegacion])
  const resultadosPlanos = useMemo(() => gruposFiltrados.flatMap(g => g.items), [gruposFiltrados])

  useEffect(() => setActivo(0), [query])

  // Buscar datos reales — con debounce, para no pegarle al backend en cada tecla.
  useEffect(() => {
    if (!busquedaEntidades) return
    const texto = query.trim()
    if (texto.length < 2) { setResultadosEntidades([]); setBuscandoEntidades(false); return }
    setBuscandoEntidades(true)
    const idTimer = setTimeout(() => {
      api.get('/busqueda-global', { params: { q: texto } })
        .then(r => setResultadosEntidades(r.data))
        .catch(() => setResultadosEntidades([]))
        .finally(() => setBuscandoEntidades(false))
    }, 300)
    return () => clearTimeout(idTimer)
  }, [query, busquedaEntidades])

  useEffect(() => {
    if (abierto) setTimeout(() => inputRef.current?.focus(), 50)
    else { setQuery(''); setActivo(0); setResultadosEntidades([]) }
  }, [abierto])

  useEffect(() => {
    const h = (e) => {
      if (e.key === 'Escape') setAbierto(false)
      // Atajo global: Ctrl/Cmd+K abre el buscador desde cualquier parte de la pantalla.
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setAbierto(true)
      }
    }
    document.addEventListener('keydown', h)
    return () => document.removeEventListener('keydown', h)
  }, [])

  const ir = (ruta) => { navigate(ruta); setAbierto(false) }

  const manejarTeclaLista = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActivo(i => Math.min(i + 1, resultadosPlanos.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActivo(i => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && resultadosPlanos[activo]) {
      e.preventDefault()
      ir(resultadosPlanos[activo].ruta)
    }
  }

  return (
    <>
      <button onClick={() => setAbierto(true)} title="Buscar (Ctrl+K)"
        className="p-2 text-gray-500 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors">
        <IconoBuscar className="h-5 w-5" />
      </button>

      {abierto && (
        <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-black/40 backdrop-blur-sm animate-fade-in"
          onClick={() => setAbierto(false)}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden ring-1 ring-black/5 slide-up"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-100">
              <IconoBuscar className="h-5 w-5 text-gray-400 flex-shrink-0" />
              <input
                aria-label="Buscar en la plataforma"
                ref={inputRef}
                value={query}
                onChange={e => setQuery(e.target.value)}
                onKeyDown={manejarTeclaLista}
                placeholder={busquedaEntidades ? 'Buscar clientes, candidatos, servicios, equipo…' : 'Buscar una sección…'}
                className="flex-1 text-sm text-gray-900 placeholder-gray-400 focus:outline-none"
              />
              {buscandoEntidades && (
                <span className="h-3.5 w-3.5 border-2 border-primary-300 border-t-transparent rounded-full animate-spin flex-shrink-0" />
              )}
              <kbd className="hidden sm:inline text-xs text-gray-400 border border-gray-200 rounded-md px-1.5 py-0.5 font-sans">Esc</kbd>
            </div>

            <div className="max-h-80 overflow-y-auto py-2 scrollbar-thin">
              {resultadosPlanos.length === 0 ? (
                <div className="text-center py-8 px-4">
                  <p className="text-sm text-gray-400">
                    {query.trim() ? `Sin resultados para "${query}"` : 'Escribe para buscar…'}
                  </p>
                </div>
              ) : (
                gruposFiltrados.map(grupo => (
                  <div key={grupo.titulo} className="mb-1 last:mb-0">
                    <p className="px-4 pt-2 pb-1 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                      {grupo.titulo}
                    </p>
                    {grupo.items.map(item => {
                      const idx = resultadosPlanos.indexOf(item)
                      const seleccionado = idx === activo
                      return (
                        <button key={item.key ?? item.ruta} onClick={() => ir(item.ruta)}
                          onMouseEnter={() => setActivo(idx)}
                          className={`w-full flex items-center gap-3 px-4 py-2.5 text-left transition-colors ${
                            seleccionado ? 'bg-primary-50' : 'hover:bg-gray-50'}`}>
                          <span className="text-lg w-6 text-center flex-shrink-0">{item.icono}</span>
                          <span className="min-w-0 flex-1">
                            <span className={`block text-sm font-medium truncate ${seleccionado ? 'text-primary-700' : 'text-gray-700'}`}>
                              {item.etiqueta}
                            </span>
                            {item.subtitulo && (
                              <span className="block text-xs text-gray-400 truncate">{item.subtitulo}</span>
                            )}
                          </span>
                          {seleccionado && (
                            <kbd className="hidden sm:inline text-[10px] text-primary-400 border border-primary-200 rounded px-1.5 py-0.5 font-sans flex-shrink-0">
                              ↵
                            </kbd>
                          )}
                        </button>
                      )
                    })}
                  </div>
                ))
              )}
            </div>

            <div className="flex items-center gap-4 px-4 py-2 border-t border-gray-100 bg-gray-50/60">
              <span className="flex items-center gap-1 text-[11px] text-gray-400">
                <kbd className="border border-gray-200 rounded px-1 py-0.5 font-sans">↑↓</kbd> navegar
              </span>
              <span className="flex items-center gap-1 text-[11px] text-gray-400">
                <kbd className="border border-gray-200 rounded px-1 py-0.5 font-sans">↵</kbd> abrir
              </span>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
