import { useState, useEffect, useCallback, useMemo } from 'react'
import ciudadService from '../../services/ciudadService'
import Toast from '../../components/Toast'
import { ConfirmModal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'

const POR_PAGINA = 10

const NIVELES_CIUDAD = [
  { valor: 'PRINCIPAL',            etiqueta: 'Principal',                        color: 'bg-emerald-50 text-emerald-700 border-emerald-200', punto: 'bg-emerald-500' },
  { valor: 'INTERMEDIA',           etiqueta: 'Intermedia / municipio principal',  color: 'bg-amber-50 text-amber-700 border-amber-200',  punto: 'bg-amber-500' },
  { valor: 'MUNICIPIO_SECUNDARIO', etiqueta: 'Municipio secundario',              color: 'bg-rose-50 text-rose-700 border-rose-200',   punto: 'bg-rose-500' },
]

function badgeNivel(nivel) {
  return NIVELES_CIUDAD.find(n => n.valor === nivel) ?? { etiqueta: nivel ?? '—', color: 'bg-gray-100 text-gray-500 border-gray-200' }
}

const ICONO_LAPIZ = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 6.75l2.25 2.25" />
  </svg>
)

const ICONO_CHECK = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
  </svg>
)

const ICONO_BASURA = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
  </svg>
)

const ICONO_X = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
  </svg>
)

function BotonIcono({ onClick, title, tono, disabled = false, children }) {
  const tonos = {
    primario: 'text-primary-600 hover:bg-primary-50',
    exito:    'text-emerald-600 hover:bg-emerald-50',
    peligro:  'text-red-600 hover:bg-red-50',
    neutro:   'text-gray-400 hover:bg-gray-100',
  }
  return (
    <button type="button" onClick={onClick} title={title} disabled={disabled}
      className={`inline-flex items-center justify-center w-8 h-8 rounded-full transition-colors ${
        disabled ? 'text-gray-300 cursor-not-allowed' : (tonos[tono] ?? tonos.neutro)
      }`}>
      {children}
    </button>
  )
}

function BarraFiltros({ busqueda, onBusqueda, filtro, onFiltro, total, pendientes }) {
  return (
    <div className="flex flex-wrap items-center gap-3 mb-3">
      <input type="search" value={busqueda} onChange={e => onBusqueda(e.target.value)}
        placeholder="Buscar por ciudad, departamento o código DANE…"
        className="flex-1 min-w-[220px] max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      <div className="flex gap-1">
        {[
          { key: 'todas',      label: 'Todas',                  count: total      },
          { key: 'pendientes', label: 'Pendientes por confirmar', count: pendientes },
        ].map(f => (
          <button key={f.key} onClick={() => onFiltro(f.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              filtro === f.key
                ? f.key === 'pendientes' ? 'bg-amber-100 text-amber-700' : 'bg-primary-100 text-primary-700'
                : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
            }`}>
            {f.label}<span className="ml-1.5 font-normal opacity-70">{f.count}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

function TablaCiudades({ ciudades, cargando, onClasificar, onConfirmar, onEliminar }) {
  const [editandoId, setEditandoId] = useState(null)

  if (cargando) return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  if (!ciudades.length) return <div className="text-center py-16 text-gray-400 text-sm">No hay ciudades que coincidan con el filtro.</div>

  const elegirNivel = (ciudad, nivel) => {
    onClasificar(ciudad, nivel)
    setEditandoId(null)
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-100">
        <thead className="bg-gray-50">
          <tr>
            {['Ciudad / Municipio', 'Departamento', 'Cód. DANE ciudad', 'Cód. DANE depto', 'Nivel logístico', 'Estado', ''].map(h => (
              <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {ciudades.map(c => {
            const badge = badgeNivel(c.nivelCiudad)
            const editando = editandoId === c.idCiudad
            return (
              <tr key={c.idCiudad} className={`hover:bg-gray-50 transition-colors ${c.pendienteConfirmacion ? 'bg-amber-50/40' : ''}`}>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{c.nombreCiudad}</td>
                <td className="px-4 py-3 text-sm text-gray-500">{c.departamento}</td>
                <td className="px-4 py-3 text-sm text-gray-400 font-mono">{c.codigoDaneCiudad ?? '—'}</td>
                <td className="px-4 py-3 text-sm text-gray-400 font-mono">{c.codigoDaneDepto ?? '—'}</td>
                <td className="px-4 py-3">
                  {editando ? (
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {NIVELES_CIUDAD.map(n => (
                        <button key={n.valor} type="button" onClick={() => elegirNivel(c, n.valor)}
                          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                            n.valor === c.nivelCiudad ? n.color : 'bg-white text-gray-500 border-gray-200 hover:bg-gray-50'
                          }`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${n.punto}`} />
                          {n.etiqueta}
                        </button>
                      ))}
                      <BotonIcono onClick={() => setEditandoId(null)} title="Cancelar" tono="neutro">{ICONO_X}</BotonIcono>
                    </div>
                  ) : (
                    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${badge.color}`}>
                      {badge.etiqueta}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3">
                  {c.pendienteConfirmacion ? (
                    <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-700 border border-amber-200">
                      <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                      </svg>
                      Pendiente por confirmar
                    </span>
                  ) : (
                    <span className="text-xs text-gray-400">Confirmada</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-1">
                    {c.pendienteConfirmacion && (
                      <BotonIcono onClick={() => onConfirmar(c)} title="Aceptar clasificación" tono="exito">{ICONO_CHECK}</BotonIcono>
                    )}
                    <BotonIcono onClick={() => setEditandoId(editando ? null : c.idCiudad)} title="Cambiar nivel logístico" tono="primario">{ICONO_LAPIZ}</BotonIcono>
                    <BotonIcono
                      onClick={() => onEliminar(c)}
                      disabled={c.enUso}
                      title={c.enUso ? 'No se puede eliminar: está en uso por clientes, candidatos u otros registros. Solo puedes editar su nivel.' : 'Eliminar ciudad'}
                      tono="peligro">
                      {ICONO_BASURA}
                    </BotonIcono>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default function Ciudades() {
  const [ciudades, setCiudades] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError]       = useState(null)
  const [toast, setToast]       = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [filtro, setFiltro]     = useState('todas')
  const [pagina, setPagina]     = useState(1)
  const [aEliminar, setAEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      setCiudades(await ciudadService.listar())
    } catch { setError('Error al cargar ciudades.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const pendientes = useMemo(() => ciudades.filter(c => c.pendienteConfirmacion).length, [ciudades])

  const ciudadesVisibles = useMemo(() => {
    let lista = ciudades
    if (filtro === 'pendientes') lista = lista.filter(c => c.pendienteConfirmacion)
    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(c =>
        c.nombreCiudad.toLowerCase().includes(q) ||
        c.departamento.toLowerCase().includes(q) ||
        (c.codigoDaneCiudad ?? '').toLowerCase().includes(q) ||
        (c.codigoDaneDepto ?? '').toLowerCase().includes(q))
    }
    return lista
  }, [ciudades, filtro, busqueda])

  useEffect(() => { setPagina(1) }, [filtro, busqueda])

  const ciudadesPagina = ciudadesVisibles.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  const clasificar = async (ciudad, nivel) => {
    if (!nivel || nivel === ciudad.nivelCiudad) return
    try {
      const data = await ciudadService.clasificar(ciudad.idCiudad, nivel)
      setCiudades(prev => prev.map(c => c.idCiudad === ciudad.idCiudad ? data : c))
      setToast({ mensaje: `"${ciudad.nombreCiudad}" reclasificada correctamente`, tipo: 'exito' })
    } catch { setError('Error al clasificar la ciudad.') }
  }

  const confirmar = async (ciudad) => {
    try {
      const data = await ciudadService.confirmar(ciudad.idCiudad)
      setCiudades(prev => prev.map(c => c.idCiudad === ciudad.idCiudad ? data : c))
      setToast({ mensaje: `"${ciudad.nombreCiudad}" confirmada en ${badgeNivel(data.nivelCiudad).etiqueta}`, tipo: 'exito' })
    } catch { setError('Error al confirmar la ciudad.') }
  }

  const eliminar = async () => {
    if (!aEliminar) return
    setEliminando(true)
    try {
      await ciudadService.eliminar(aEliminar.idCiudad)
      setCiudades(prev => prev.filter(c => c.idCiudad !== aEliminar.idCiudad))
      setToast({ mensaje: `"${aEliminar.nombreCiudad}" eliminada del catálogo`, tipo: 'exito' })
      setAEliminar(null)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al eliminar la ciudad.')
      setAEliminar(null)
    } finally {
      setEliminando(false)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Ciudades</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            Clasifica cada ciudad por su nivel logístico para tarifar servicios con desplazamiento
          </p>
        </div>
      </div>

      {pendientes > 0 && (
        <div className="mb-4 flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3.5">
          <svg className="h-5 w-5 text-amber-500 flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
          </svg>
          <div>
            <p className="text-sm font-semibold text-amber-800">
              {pendientes} ciudad{pendientes !== 1 ? 'es' : ''} pendiente{pendientes !== 1 ? 's' : ''} por confirmar
            </p>
            <p className="text-xs text-amber-600 mt-0.5">
              Se crearon automáticamente cuando un cliente o candidato registró una ciudad nueva. Entraron como
              "Municipio secundario" (el nivel más caro, para no perder margen) — revísalas y dale "Aceptar" si
              está bien, o cámbiala si corresponde a otro nivel. Ej: si alguien crea "Soacha", queda en
              Municipio secundario hasta que tú confirmes o la reclasifiques.
            </p>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-3 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      <BarraFiltros
        busqueda={busqueda} onBusqueda={setBusqueda}
        filtro={filtro} onFiltro={setFiltro}
        total={ciudades.length} pendientes={pendientes}
      />

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <TablaCiudades ciudades={ciudadesPagina} cargando={cargando} onClasificar={clasificar} onConfirmar={confirmar} onEliminar={setAEliminar} />
        {!cargando && ciudadesVisibles.length > 0 && (
          <Paginacion pagina={pagina} total={ciudadesVisibles.length} porPagina={POR_PAGINA} onChange={setPagina} />
        )}
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {aEliminar && (
        <ConfirmModal
          titulo="Eliminar ciudad"
          danger
          cargando={eliminando}
          confirmLabel="Eliminar"
          cancelLabel="Cancelar"
          onConfirmar={eliminar}
          onCancelar={() => setAEliminar(null)}
        >
          <p className="text-sm text-gray-500">
            ¿Eliminar <span className="font-semibold text-gray-700">"{aEliminar.nombreCiudad}"</span> ({aEliminar.departamento}) del catálogo?
            Esta acción no se puede deshacer.
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}
