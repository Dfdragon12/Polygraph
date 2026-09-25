import { useState, useEffect, useCallback, useMemo } from 'react'
import ciudadService from '../../services/ciudadService'
import barrioService from '../../services/barrioService'
import catalogoService from '../../services/catalogoService'
import Toast from '../../components/Toast'
import { Modal, ConfirmModal } from '../../components/ui/Modal'
import Paginacion from '../../components/ui/Paginacion'
import MenuAcciones from '../../components/ui/MenuAcciones'

const POR_PAGINA = 10

const NIVELES_CIUDAD = [
  { valor: 'PRINCIPAL',            etiqueta: 'Principal',                        color: 'bg-emerald-50 text-emerald-700 border-emerald-200', punto: 'bg-emerald-500' },
  { valor: 'INTERMEDIA',           etiqueta: 'Intermedia / municipio principal',  color: 'bg-amber-50 text-amber-700 border-amber-200',  punto: 'bg-amber-500' },
  { valor: 'MUNICIPIO_SECUNDARIO', etiqueta: 'Municipio secundario',              color: 'bg-rose-50 text-rose-700 border-rose-200',   punto: 'bg-rose-500' },
]

/** El código DANE del departamento ya no se pide acá — se deriva solo, del departamento que ya
 * tiene guardado la ciudad, contra la lista fija de los 33 departamentos oficiales (ver
 * DepartamentosColombiaUtil en el backend). Solo falta el de la ciudad/municipio, que sigue siendo
 * texto libre. Si por algún caso viejo no se puede derivar, el error del backend lo dice. */
function ModalConfirmarConDane({ ciudad, onCerrar, onConfirmado, onError }) {
  const [codigoDaneCiudad, setCodigoDaneCiudad] = useState(ciudad.codigoDaneCiudad ?? '')
  const [guardando, setGuardando] = useState(false)
  const [errorLocal, setErrorLocal] = useState(null)

  const confirmar = async (e) => {
    e.preventDefault()
    if (!codigoDaneCiudad.trim()) {
      setErrorLocal('El código DANE de la ciudad es obligatorio para confirmarla.')
      return
    }
    setGuardando(true)
    setErrorLocal(null)
    try {
      const data = await ciudadService.confirmar(ciudad.idCiudad, {
        codigoDaneCiudad: codigoDaneCiudad.trim(),
      })
      onConfirmado(data)
    } catch (err) {
      setErrorLocal(err.response?.data?.mensaje ?? 'Error al confirmar la ciudad.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-sm p-5">
        <h3 className="text-sm font-semibold text-gray-900 mb-1">Código DANE requerido</h3>
        <p className="text-xs text-gray-500 mb-4">
          Antes de confirmar <span className="font-medium text-gray-700">"{ciudad.nombreCiudad}"</span> ({ciudad.departamento}),
          ingresa su código DANE.
        </p>
        <form onSubmit={confirmar} className="space-y-3">
          {errorLocal && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg">{errorLocal}</div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Código DANE ciudad</label>
            <input type="text" inputMode="numeric" value={codigoDaneCiudad} onChange={e => setCodigoDaneCiudad(e.target.value)}
              placeholder="Ej: 11001" autoFocus
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div className="flex justify-end gap-2 pt-1">
            <button type="button" onClick={onCerrar} className="px-3 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
            <button type="submit" disabled={guardando}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
              {guardando ? 'Confirmando...' : 'Confirmar ciudad'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/** Gestión de barrios de UNA ciudad puntual — los barrios no tienen sentido sin su ciudad (mismo
 *  nombre puede existir en dos ciudades distintas, son registros separados), así que se administran
 *  desde acá en vez de una pantalla propia. Mismo patrón que la tabla de ciudades: los que llegan
 *  por autocompletado de un cliente/candidato entran "pendientes de confirmar" y hay que revisarlos. */
function ModalBarriosDeCiudad({ ciudad, onClose, onToast, onCambioPendientes }) {
  const [barrios, setBarrios] = useState(null)
  const [error, setError] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [nuevoNombre, setNuevoNombre] = useState('')
  const [agregando, setAgregando] = useState(false)
  const [aEliminar, setAEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)

  const cargar = useCallback(() => {
    barrioService.listarPorCiudad(ciudad.idCiudad)
      .then(setBarrios)
      .catch(() => setError('No se pudieron cargar los barrios de esta ciudad.'))
  }, [ciudad.idCiudad])

  useEffect(() => { cargar() }, [cargar])

  const visibles = useMemo(() => {
    if (!barrios) return []
    const q = busqueda.trim().toLowerCase()
    return q ? barrios.filter(b => b.nombre.toLowerCase().includes(q)) : barrios
  }, [barrios, busqueda])

  const agregar = async (e) => {
    e.preventDefault()
    if (!nuevoNombre.trim()) return
    setAgregando(true)
    setError(null)
    try {
      const creado = await catalogoService.crearBarrio({ nombre: nuevoNombre.trim(), idCiudad: ciudad.idCiudad })
      setBarrios(prev => (prev.some(b => b.idBarrio === creado.idBarrio) ? prev : [...prev, creado].sort((a, b) => a.nombre.localeCompare(b.nombre))))
      setNuevoNombre('')
      onCambioPendientes()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo agregar el barrio.')
    } finally {
      setAgregando(false)
    }
  }

  const confirmar = async (barrio) => {
    try {
      const data = await barrioService.confirmar(barrio.idBarrio)
      setBarrios(prev => prev.map(b => b.idBarrio === data.idBarrio ? data : b))
      onToast(`"${barrio.nombre}" confirmado`, 'exito')
      onCambioPendientes()
    } catch {
      setError('No se pudo confirmar el barrio.')
    }
  }

  const eliminar = async () => {
    if (!aEliminar) return
    setEliminando(true)
    try {
      await barrioService.eliminar(aEliminar.idBarrio)
      setBarrios(prev => prev.filter(b => b.idBarrio !== aEliminar.idBarrio))
      onToast(`"${aEliminar.nombre}" eliminado`, 'exito')
      setAEliminar(null)
      onCambioPendientes()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo eliminar el barrio.')
      setAEliminar(null)
    } finally {
      setEliminando(false)
    }
  }

  const pendientes = barrios?.filter(b => b.pendienteConfirmacion).length ?? 0

  return (
    <Modal titulo={`Barrios de ${ciudad.nombreCiudad}`} subtitulo={ciudad.departamento} onClose={onClose} ancho="max-w-lg">
      <div className="px-6 py-5 space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
            {error}
            <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
          </div>
        )}

        <form onSubmit={agregar} className="flex gap-2">
          <input value={nuevoNombre} onChange={e => setNuevoNombre(e.target.value)}
            placeholder="Agregar un barrio a esta ciudad..."
            className="flex-1 border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <button type="submit" disabled={agregando || !nuevoNombre.trim()}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors whitespace-nowrap">
            {agregando ? 'Agregando...' : '+ Agregar'}
          </button>
        </form>

        {barrios !== null && barrios.length > 5 && (
          <input type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
            placeholder="Buscar barrio..."
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        )}

        {pendientes > 0 && (
          <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
            {pendientes} barrio{pendientes !== 1 ? 's' : ''} pendiente{pendientes !== 1 ? 's' : ''} por confirmar —
            se crearon solos cuando alguien escribió un barrio nuevo en un formulario.
          </p>
        )}

        {barrios === null ? (
          <div className="flex justify-center py-10"><div className="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent" /></div>
        ) : visibles.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-8">
            {barrios.length === 0 ? 'Esta ciudad todavía no tiene barrios registrados.' : 'Sin resultados para la búsqueda.'}
          </p>
        ) : (
          <div className="border border-gray-100 rounded-lg divide-y divide-gray-50 max-h-80 overflow-y-auto">
            {visibles.map(b => (
              <div key={b.idBarrio} className={`flex items-center justify-between gap-3 px-3 py-2 ${b.pendienteConfirmacion ? 'bg-amber-50/50' : ''}`}>
                <span className="text-sm text-gray-800 truncate">{b.nombre}</span>
                <div className="flex items-center gap-2 flex-shrink-0">
                  {b.pendienteConfirmacion ? (
                    <button onClick={() => confirmar(b)}
                      className="text-xs font-medium text-emerald-600 hover:text-emerald-800 hover:underline">
                      Confirmar
                    </button>
                  ) : (
                    <span className="text-xs text-gray-400">Confirmado</span>
                  )}
                  <button onClick={() => setAEliminar(b)} title="Eliminar barrio"
                    className="text-gray-300 hover:text-red-500 transition-colors">
                    {ICONO_BASURA}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {aEliminar && (
        <ConfirmModal
          titulo="Eliminar barrio"
          danger
          cargando={eliminando}
          confirmLabel="Eliminar"
          cancelLabel="Cancelar"
          onConfirmar={eliminar}
          onCancelar={() => setAEliminar(null)}
        >
          <p className="text-sm text-gray-500">
            ¿Eliminar <span className="font-semibold text-gray-700">"{aEliminar.nombre}"</span> del catálogo?
            Esta acción no se puede deshacer.
          </p>
        </ConfirmModal>
      )}
    </Modal>
  )
}

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

const ICONO_BARRIO = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
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

function TablaCiudades({ ciudades, cargando, onClasificar, onConfirmar, onEliminar, onVerBarrios }) {
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
            {['Ciudad / Municipio', 'Departamento', 'Cód. DANE ciudad', 'Cód. DANE depto', 'Nivel logístico', 'Estado', '···'].map(h => (
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
                <td className="px-4 py-3 text-center">
                  <MenuAcciones opciones={[
                    c.pendienteConfirmacion && {
                      label: 'Aceptar clasificación', icono: ICONO_CHECK, destacado: 'text-emerald-600',
                      onClick: () => onConfirmar(c),
                    },
                    {
                      label: 'Cambiar nivel logístico', icono: ICONO_LAPIZ,
                      onClick: () => setEditandoId(editando ? null : c.idCiudad),
                    },
                    {
                      label: 'Ver barrios', icono: ICONO_BARRIO, destacado: 'text-cyan-600',
                      onClick: () => onVerBarrios(c),
                    },
                    {
                      label: 'Eliminar ciudad', icono: ICONO_BASURA, destacado: 'text-red-600',
                      disabled: c.enUso,
                      title: c.enUso ? 'No se puede eliminar: está en uso por clientes, candidatos u otros registros. Solo puedes editar su nivel.' : undefined,
                      onClick: () => onEliminar(c),
                    },
                  ]} />
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
  const [confirmandoDane, setConfirmandoDane] = useState(null)
  const [verBarriosDe, setVerBarriosDe] = useState(null)
  const [pendientesBarrios, setPendientesBarrios] = useState(0)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      setCiudades(await ciudadService.listar())
    } catch { setError('Error al cargar ciudades.') }
    finally { setCargando(false) }
  }, [])

  const cargarPendientesBarrios = useCallback(() => {
    barrioService.contarPendientes().then(setPendientesBarrios).catch(() => {})
  }, [])

  useEffect(() => { cargar() }, [cargar])
  useEffect(() => { cargarPendientesBarrios() }, [cargarPendientesBarrios])

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
    if (!ciudad.codigoDaneCiudad || !ciudad.codigoDaneDepto) {
      setConfirmandoDane(ciudad)
      return
    }
    try {
      const data = await ciudadService.confirmar(ciudad.idCiudad)
      setCiudades(prev => prev.map(c => c.idCiudad === ciudad.idCiudad ? data : c))
      setToast({ mensaje: `"${ciudad.nombreCiudad}" confirmada en ${badgeNivel(data.nivelCiudad).etiqueta}`, tipo: 'exito' })
    } catch { setError('Error al confirmar la ciudad.') }
  }

  const confirmarConDane = (data) => {
    setCiudades(prev => prev.map(c => c.idCiudad === data.idCiudad ? data : c))
    setToast({ mensaje: `"${data.nombreCiudad}" confirmada en ${badgeNivel(data.nivelCiudad).etiqueta}`, tipo: 'exito' })
    setConfirmandoDane(null)
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

      {pendientesBarrios > 0 && (
        <div className="mb-4 flex items-start gap-3 bg-cyan-50 border border-cyan-200 rounded-xl px-4 py-3.5">
          <span className="text-cyan-500 flex-shrink-0 mt-0.5">{ICONO_BARRIO}</span>
          <div>
            <p className="text-sm font-semibold text-cyan-800">
              {pendientesBarrios} barrio{pendientesBarrios !== 1 ? 's' : ''} pendiente{pendientesBarrios !== 1 ? 's' : ''} por confirmar
            </p>
            <p className="text-xs text-cyan-700 mt-0.5">
              Se crearon automáticamente cuando alguien escribió un barrio nuevo en un formulario (candidato o
              cliente). Abre "Ver barrios" en la ciudad correspondiente para revisarlos y confirmarlos.
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
        <TablaCiudades ciudades={ciudadesPagina} cargando={cargando} onClasificar={clasificar} onConfirmar={confirmar}
          onEliminar={setAEliminar} onVerBarrios={setVerBarriosDe} />
        {!cargando && ciudadesVisibles.length > 0 && (
          <Paginacion pagina={pagina} total={ciudadesVisibles.length} porPagina={POR_PAGINA} onChange={setPagina} />
        )}
      </div>

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {verBarriosDe && (
        <ModalBarriosDeCiudad
          ciudad={verBarriosDe}
          onClose={() => setVerBarriosDe(null)}
          onToast={(mensaje, tipo = 'exito') => setToast({ mensaje, tipo })}
          onCambioPendientes={cargarPendientesBarrios}
        />
      )}

      {confirmandoDane && (
        <ModalConfirmarConDane
          ciudad={confirmandoDane}
          onCerrar={() => setConfirmandoDane(null)}
          onConfirmado={confirmarConDane}
          onError={setError}
        />
      )}

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
