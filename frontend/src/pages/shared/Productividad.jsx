import { useCallback, useEffect, useId, useState } from 'react'
import productividadService from '../../services/productividadService'
import { useAuth } from '../../hooks/useAuth'
import { parseFecha } from '../../utils/fechas'
import Paginacion, { usePaginacion } from '../../components/ui/Paginacion'

// Reporte diario de productividad (SE-11). Se calcula en el servidor desde el registro de cambios, no desde el estado actual, y el día corta a las
// 11:59 p. m. hora de Colombia. Administrador: todo; gestor: sus clientes; cada ejecutor y el programador, lo suyo.
const hoyIso = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date())
const PERFILES = [
  { valor: '', etiqueta: 'Todos los perfiles' }, { valor: 'ANALISTA_INTERNO', etiqueta: 'Analista' }, { valor: 'POLIGRAFISTA', etiqueta: 'Poligrafista' },
  { valor: 'VISITADOR', etiqueta: 'Visitador' }, { valor: 'PROGRAMADOR', etiqueta: 'Programador' }, { valor: 'GESTOR', etiqueta: 'Gestor' },
]
const ETIQUETA_ROL = Object.fromEntries(PERFILES.filter(p => p.valor).map(p => [p.valor, p.etiqueta]))
const fechaLarga = (iso) => parseFecha(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' })
const inputCls = 'h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

function Campo({ etiqueta, children }) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-gray-700">{etiqueta}</label>
      {children(id)}
    </div>
  )
}

function Cabecera({ columnas }) {
  return (
    <thead>
      <tr className="border-b border-gray-100">
        {columnas.map(c => <th key={c} className="px-3 py-3 text-left text-[11px] font-semibold text-gray-500 uppercase tracking-wider bg-gray-50/60">{c}</th>)}
      </tr>
    </thead>
  )
}

export default function Productividad() {
  const { usuario } = useAuth()
  const veTodo = usuario?.rol === 'ADMIN_POLYGRAPH' || usuario?.rol === 'GESTOR'
  const [filtros, setFiltros] = useState({ desde: hoyIso(), hasta: hoyIso(), perfil: '', idPersona: '', idTipoProgreso: '', idCliente: '' })
  const [datos, setDatos] = useState(null)
  const [opciones, setOpciones] = useState({ clientes: [], tipos: [], personas: [] })
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [exportando, setExportando] = useState(false)

  const cargar = useCallback(async () => {
    setCargando(true); setError(null)
    try {
      const d = await productividadService.reporte(filtros)
      setDatos(d)
      setOpciones(d.opciones)
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo cargar el reporte. Intenta de nuevo.')
    } finally { setCargando(false) }
  }, [filtros])
  useEffect(() => { cargar() }, [cargar])

  const cambiar = (campo) => (e) => setFiltros(f => ({ ...f, [campo]: e.target.value }))
  const exportar = async () => {
    setExportando(true)
    try { await productividadService.exportar(filtros) } catch { setError('No se pudo descargar el Excel.') } finally { setExportando(false) }
  }

  const personas = datos?.personas ?? []
  const gestores = datos?.gestores ?? []
  const pagPersonas = usePaginacion(personas, JSON.stringify(filtros))
  const pagGestores = usePaginacion(gestores, JSON.stringify(filtros))

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Productividad diaria</h2>
          <p className="text-sm text-gray-700 mt-0.5">
            Se calcula con el registro de cambios, no con el estado de hoy. El día cierra a las 11:59 p. m., hora de Colombia.
          </p>
        </div>
        <button type="button" onClick={exportar} disabled={exportando || cargando}
          className="inline-flex items-center justify-center px-4 py-2.5 text-sm font-semibold text-white bg-primary-600 rounded-lg shadow-sm hover:bg-primary-700 disabled:opacity-50">
          {exportando ? 'Preparando…' : 'Descargar Excel'}
        </button>
      </div>

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-4 grid gap-3 grid-cols-2 md:grid-cols-3 xl:grid-cols-6">
        <Campo etiqueta="Desde">{id => <input id={id} type="date" className={inputCls} value={filtros.desde} max={hoyIso()} onChange={cambiar('desde')} />}</Campo>
        <Campo etiqueta="Hasta">{id => <input id={id} type="date" className={inputCls} value={filtros.hasta} max={hoyIso()} onChange={cambiar('hasta')} />}</Campo>
        {veTodo && (
          <>
            <Campo etiqueta="Perfil">{id => (
              <select id={id} className={inputCls} value={filtros.perfil} onChange={cambiar('perfil')}>
                {PERFILES.map(p => <option key={p.valor} value={p.valor}>{p.etiqueta}</option>)}
              </select>)}
            </Campo>
            <Campo etiqueta="Persona">{id => (
              <select id={id} className={inputCls} value={filtros.idPersona} onChange={cambiar('idPersona')}>
                <option value="">Todas</option>
                {opciones.personas.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>)}
            </Campo>
          </>
        )}
        <Campo etiqueta="Tipo de subproceso">{id => (
          <select id={id} className={inputCls} value={filtros.idTipoProgreso} onChange={cambiar('idTipoProgreso')}>
            <option value="">Todos</option>
            {opciones.tipos.map(t => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>)}
        </Campo>
        <Campo etiqueta="Cliente">{id => (
          <select id={id} className={inputCls} value={filtros.idCliente} onChange={cambiar('idCliente')}>
            <option value="">Todos</option>
            {opciones.clientes.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>)}
        </Campo>
      </div>

      {error && <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-3">{error}</p>}

      {cargando ? (
        <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
      ) : (
        <>
          <section aria-label="Personal" className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
            <h3 className="px-5 py-3.5 border-b border-gray-100 text-sm font-semibold text-gray-800">Personal</h3>
            {personas.length === 0 ? (
              <p className="text-center py-10 text-sm text-gray-700">No hay movimientos en este rango.</p>
            ) : (
              <>
                <table className="table-stack w-full text-sm">
                  <Cabecera columnas={['Fecha', 'Persona', 'Perfil', 'Asignados', 'Finalizados', 'Publicados', 'Devueltos', 'Reprogramados', 'Abiertos al cierre']} />
                  <tbody className="divide-y divide-gray-100">
                    {pagPersonas.visibles.map((p, i) => (
                      <tr key={`${p.fecha}-${p.idPersona}-${i}`}>
                        <td data-label="Fecha" className="px-3 py-3">{fechaLarga(p.fecha)}</td>
                        <td data-label="Persona" className="px-3 py-3 font-medium text-gray-900">{p.nombre}</td>
                        <td data-label="Perfil" className="px-3 py-3">{ETIQUETA_ROL[p.rol] ?? p.rol}</td>
                        <td data-label="Asignados" className="px-3 py-3 tabular-nums">{p.asignados}</td>
                        <td data-label="Finalizados" className="px-3 py-3 tabular-nums">{p.finalizados}</td>
                        <td data-label="Publicados" className="px-3 py-3 tabular-nums">{p.publicados}</td>
                        <td data-label="Devueltos" className="px-3 py-3 tabular-nums">{p.devueltos}</td>
                        <td data-label="Reprogramados" className="px-3 py-3">
                          {p.reprogramados == null ? '—' : Object.keys(p.reprogramados).length === 0 ? '0'
                            : Object.entries(p.reprogramados).map(([m, n]) => <span key={m} className="block">{m}: {n}</span>)}
                        </td>
                        <td data-label="Abiertos al cierre" className="px-3 py-3 tabular-nums">{p.abiertosAlCierre}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Paginacion pagina={pagPersonas.pagina} total={pagPersonas.total} porPagina={pagPersonas.porPagina} onChange={pagPersonas.setPagina} />
              </>
            )}
          </section>

          {veTodo && (
            <section aria-label="Gestores" className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
              <h3 className="px-5 py-3.5 border-b border-gray-100 text-sm font-semibold text-gray-800">Cierre de los gestores</h3>
              {gestores.length === 0 ? (
                <p className="text-center py-10 text-sm text-gray-700">No hay servicios para los gestores en este rango.</p>
              ) : (
                <>
                  <table className="table-stack w-full text-sm">
                    <Cabecera columnas={['Fecha', 'Gestor', 'Por verificar al cierre', 'Servicios por estado al cierre', 'Estudios completos publicados', 'Vencidos al cierre']} />
                    <tbody className="divide-y divide-gray-100">
                      {pagGestores.visibles.map((g, i) => (
                        <tr key={`${g.fecha}-${g.idGestor}-${i}`}>
                          <td data-label="Fecha" className="px-3 py-3">{fechaLarga(g.fecha)}</td>
                          <td data-label="Gestor" className="px-3 py-3 font-medium text-gray-900">{g.nombre}</td>
                          <td data-label="Por verificar al cierre" className="px-3 py-3 tabular-nums">{g.porVerificarAlCierre}</td>
                          <td data-label="Servicios por estado al cierre" className="px-3 py-3">
                            {g.serviciosPorEstado.filter(e => e.servicios > 0).map(e => <span key={e.codigo} className="mr-3 whitespace-nowrap">{e.nombre} {e.servicios}</span>)}
                            <span className="block text-xs text-gray-700">{g.totalServicios} servicios</span>
                          </td>
                          <td data-label="Estudios completos publicados" className="px-3 py-3 tabular-nums">{g.estudiosCompletosPublicados}</td>
                          <td data-label="Vencidos al cierre" className="px-3 py-3 tabular-nums">
                            {g.vencidosAlCierre}
                            {g.vencidosAproximado && <span className="block text-xs text-gray-700">Aproximado: usa la fecha de compromiso actual</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <Paginacion pagina={pagGestores.pagina} total={pagGestores.total} porPagina={pagGestores.porPagina} onChange={pagGestores.setPagina} />
                </>
              )}
            </section>
          )}
        </>
      )}
    </div>
  )
}
