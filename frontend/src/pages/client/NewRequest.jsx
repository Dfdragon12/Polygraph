import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import catalogoService from '../../services/catalogoService'
import solicitudService from '../../services/solicitudService'
import dashboardService from '../../services/dashboardService'
import { useAuth } from '../../hooks/useAuth'
import CiudadSelect from '../../components/CiudadSelect'

function formatearPrecio(valor) {
  if (!valor) return 'Precio a convenir'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0 }).format(valor)
}

/* ─── Paleta por clasificación — misma familia de colores que el Catálogo de Servicios ─── */
const PALETA_CLASIFICACION = [
  { icono: '🔍', tab: 'border-blue-500 text-blue-600',     texto: 'text-blue-600',    punto: 'bg-blue-500',    tarjeta: 'border-blue-300 bg-blue-50/60',     badgeNum: 'bg-blue-100 text-blue-700' },
  { icono: '🛡️', tab: 'border-primary-500 text-primary-600', texto: 'text-primary-600', punto: 'bg-primary-500', tarjeta: 'border-primary-300 bg-primary-50/60', badgeNum: 'bg-primary-100 text-primary-700' },
  { icono: '📄', tab: 'border-green-500 text-green-600',   texto: 'text-green-600',   punto: 'bg-green-500',   tarjeta: 'border-green-300 bg-green-50/60',   badgeNum: 'bg-green-100 text-green-700' },
  { icono: '✨', tab: 'border-purple-500 text-purple-600', texto: 'text-purple-600', punto: 'bg-purple-500', tarjeta: 'border-purple-300 bg-purple-50/60', badgeNum: 'bg-purple-100 text-purple-700' },
  { icono: '📋', tab: 'border-cyan-500 text-cyan-600',     texto: 'text-cyan-600',    punto: 'bg-cyan-500',    tarjeta: 'border-cyan-300 bg-cyan-50/60',     badgeNum: 'bg-cyan-100 text-cyan-700' },
  { icono: '🧭', tab: 'border-amber-500 text-amber-600',   texto: 'text-amber-600',   punto: 'bg-amber-500',   tarjeta: 'border-amber-300 bg-amber-50/60',   badgeNum: 'bg-amber-100 text-amber-700' },
]

function metaClasificacion(index) {
  return PALETA_CLASIFICACION[index % PALETA_CLASIFICACION.length]
}

const PASOS = ['Datos del evaluado', 'Servicios', 'Confirmar y enviar']

function calcularFechaEntrega(serviciosIds, catalogo) {
  const seleccionados = catalogo.filter(s => serviciosIds.includes(s.idProceso))
  if (!seleccionados.length) return null
  const maxDias = Math.max(...seleccionados.map(s => s.diasHabilesEntrega || 5))

  const ahora = new Date()
  const inicio = new Date(ahora)
  if (ahora.getHours() >= 16) {
    inicio.setDate(inicio.getDate() + 1)
  }

  let diasContados = 0
  const fecha = new Date(inicio)
  while (diasContados < maxDias) {
    fecha.setDate(fecha.getDate() + 1)
    if (fecha.getDay() !== 0) diasContados++ // domingo no cuenta
  }
  return fecha
}

function formatFechaEspanol(fecha) {
  if (!fecha) return '—'
  return fecha.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' })
}

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none'

function NewRequest() {
  const navigate = useNavigate()
  const { usuario } = useAuth()
  const esPospago = usuario?.tipoCliente === 'POSPAGO'
  const [paso, setPaso] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [catalogo, setCatalogo] = useState([])
  const [saldos, setSaldos] = useState([])
  const [cargandoCatalogo, setCargandoCatalogo] = useState(true)

  const [evaluado, setEvaluado] = useState({
    cedula: '', nombres: '', apellidos: '', celular: '', email: '', idCiudad: null, cargo: '',
  })
  const [ciudades, setCiudades] = useState([])
  const [serviciosSeleccionados, setServiciosSeleccionados] = useState([])
  const [notas, setNotas] = useState('')
  const [clasificacionActiva, setClasificacionActiva] = useState(null)
  const [subprocesosPorProceso, setSubprocesosPorProceso] = useState({})
  const [descripcionesExpandidas, setDescripcionesExpandidas] = useState({})
  const [infoSubproceso, setInfoSubproceso] = useState(null)

  useEffect(() => {
    Promise.allSettled([
      catalogoService.listarServicios().then((data) => setCatalogo(data)),
      dashboardService.obtenerBolsaServicios().then((data) => setSaldos(data)),
      catalogoService.listarCiudades().then((data) => setCiudades(data)),
    ]).finally(() => setCargandoCatalogo(false))
  }, [])

  // Prepago: solo se puede solicitar lo que ya se compró (saldo disponible > 0).
  // Pospago: acceso directo por cupo de crédito, sin restricción de saldo.
  const catalogoDisponible = useMemo(() => {
    if (esPospago) return catalogo
    return catalogo.filter((s) => (saldos.find((sd) => sd.idProceso === s.idProceso)?.cantidadDisponible ?? 0) > 0)
  }, [catalogo, saldos, esPospago])

  const camposValidos = () => {
    if (paso === 0) return evaluado.cedula && evaluado.nombres && evaluado.apellidos
    if (paso === 1) return serviciosSeleccionados.length > 0
    return true
  }

  // Solo se puede elegir un servicio en total, sin importar la clasificación
  const toggleServicio = (id) =>
    setServiciosSeleccionados((prev) => (prev.includes(id) ? [] : [id]))

  const enviar = async () => {
    setEnviando(true)
    setError(null)
    try {
      await solicitudService.crear({ ...evaluado, procesosIds: serviciosSeleccionados, notas })
      navigate('/cliente/solicitudes', { state: { exito: true } })
    } catch (e) {
      setError(e.response?.data?.mensaje || 'Error al crear la solicitud')
    } finally {
      setEnviando(false)
    }
  }

  const categorias = [...new Set(catalogoDisponible.map((s) => s.clasificacion))]

  useEffect(() => {
    if (categorias.length && !clasificacionActiva) setClasificacionActiva(categorias[0])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categorias.join('|')])

  const serviciosClasificacionActiva = useMemo(
    () => catalogoDisponible.filter((s) => s.clasificacion === clasificacionActiva),
    [catalogoDisponible, clasificacionActiva]
  )

  useEffect(() => {
    const pendientes = serviciosClasificacionActiva.filter((s) => !(s.idProceso in subprocesosPorProceso))
    if (!pendientes.length) return
    Promise.all(pendientes.map((s) =>
      catalogoService.obtenerSubprocesos(s.idProceso)
        .then((data) => [s.idProceso, data])
        .catch(() => [s.idProceso, []])
    )).then((entries) => {
      setSubprocesosPorProceso((prev) => ({ ...prev, ...Object.fromEntries(entries) }))
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serviciosClasificacionActiva])

  const serviciosSeleccionadosData = catalogo.filter(s => serviciosSeleccionados.includes(s.idProceso))
  const maxDiasEntrega = useMemo(
    () => serviciosSeleccionadosData.length
      ? Math.max(...serviciosSeleccionadosData.map(s => s.diasHabilesEntrega || 5))
      : 0,
    [serviciosSeleccionadosData]
  )
  const fechaEntrega = useMemo(
    () => calcularFechaEntrega(serviciosSeleccionados, catalogo),
    [serviciosSeleccionados, catalogo]
  )
  const solicitudDespuesde4pm = new Date().getHours() >= 16
  const ciudadSeleccionada = ciudades.find((c) => c.idCiudad === evaluado.idCiudad)

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <h1 className="text-xl font-bold text-gray-800 mb-6">Nueva Solicitud de Servicio</h1>

      {/* Stepper */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm px-6 sm:px-10 py-6 mb-8">
        <div className="flex items-start">
          {PASOS.map((nombre, i) => (
            <div key={i} className={`flex items-center ${i < PASOS.length - 1 ? 'flex-1' : ''}`}>
              <div className="flex flex-col items-center gap-2 flex-shrink-0">
                <div className={`flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold transition-all duration-300 ${
                  i < paso
                    ? 'bg-primary-600 text-white'
                    : i === paso
                      ? 'bg-primary-600 text-white ring-4 ring-primary-100'
                      : 'bg-white border-2 border-gray-200 text-gray-400'
                }`}>
                  {i < paso ? (
                    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  ) : i + 1}
                </div>
                <span className={`text-xs font-medium text-center whitespace-nowrap transition-colors ${
                  i === paso ? 'text-primary-600' : i < paso ? 'text-gray-600' : 'text-gray-400'
                }`}>
                  {nombre}
                </span>
              </div>
              {i < PASOS.length - 1 && (
                <div className="flex-1 h-1 mx-3 mt-5 rounded-full bg-gray-100 overflow-hidden">
                  <div className={`h-full bg-primary-600 rounded-full transition-all duration-500 ${i < paso ? 'w-full' : 'w-0'}`} />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Paso 0: Datos del evaluado */}
      {paso === 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6 space-y-4">
          <h2 className="text-lg font-semibold text-gray-800">Datos del evaluado</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Cédula *</label>
              <input className={inputCls} value={evaluado.cedula}
                onChange={(e) => setEvaluado({ ...evaluado, cedula: e.target.value })} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Cargo</label>
              <input className={inputCls} value={evaluado.cargo}
                onChange={(e) => setEvaluado({ ...evaluado, cargo: e.target.value })} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Nombres *</label>
              <input className={inputCls} value={evaluado.nombres}
                onChange={(e) => setEvaluado({ ...evaluado, nombres: e.target.value })} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Apellidos *</label>
              <input className={inputCls} value={evaluado.apellidos}
                onChange={(e) => setEvaluado({ ...evaluado, apellidos: e.target.value })} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Celular</label>
              <input className={inputCls} value={evaluado.celular}
                onChange={(e) => setEvaluado({ ...evaluado, celular: e.target.value })} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Ciudad</label>
              <CiudadSelect value={evaluado.idCiudad}
                onChange={(idCiudad) => setEvaluado({ ...evaluado, idCiudad })} className={inputCls} />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input type="email" className={inputCls} value={evaluado.email}
                onChange={(e) => setEvaluado({ ...evaluado, email: e.target.value })} />
            </div>
          </div>
        </div>
      )}

      {/* Paso 1: Selección de servicios */}
      {paso === 1 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="px-6 pt-5 flex items-center justify-between flex-wrap gap-2">
            <div>
              <h2 className="text-lg font-semibold text-gray-800">Selecciona el servicio</h2>
              <p className="text-xs text-gray-400 mt-0.5">Solo puedes elegir un servicio por solicitud</p>
            </div>
            {serviciosSeleccionados.length > 0 && (
              <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-primary-100 text-primary-700">
                1 seleccionado
              </span>
            )}
          </div>

          {cargandoCatalogo ? (
            <div className="flex items-center justify-center h-48">
              <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
            </div>
          ) : catalogoDisponible.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-56 gap-2 text-center px-6">
              <span className="text-2xl">🛒</span>
              <p className="text-sm font-medium text-gray-600">No tienes servicios disponibles en tu bolsa prepago</p>
              <p className="text-xs text-gray-400">Compra el servicio que necesitas y vuelve aquí para solicitarlo.</p>
              <a href="/cliente/comprar-servicios"
                className="mt-2 text-xs font-semibold text-primary-600 hover:text-primary-800 underline">
                Ir a Comprar Servicios →
              </a>
            </div>
          ) : (
          <>
          {/* Tabs de clasificación — segmenta en vez de mostrar todo de una vez */}
          <div className="px-6 mt-4 flex gap-1 overflow-x-auto border-b border-gray-100">
            {categorias.map((cat, idx) => {
              const meta = metaClasificacion(idx)
              const cantidad = catalogoDisponible.filter((s) => s.clasificacion === cat).length
              const seleccionadosEnCat = catalogoDisponible.filter(
                (s) => s.clasificacion === cat && serviciosSeleccionados.includes(s.idProceso)
              ).length
              const activa = clasificacionActiva === cat
              return (
                <button key={cat} onClick={() => setClasificacionActiva(cat)}
                  className={`px-3 py-2.5 text-xs font-medium transition-colors border-b-2 -mb-px whitespace-nowrap flex items-center gap-1.5 ${
                    activa ? meta.tab : 'border-transparent text-gray-500 hover:text-gray-700'
                  }`}>
                  <span>{meta.icono}</span>
                  {cat}
                  <span className={`text-[10px] px-1.5 rounded-full ${activa ? meta.badgeNum : 'text-gray-400'}`}>{cantidad}</span>
                  {seleccionadosEnCat > 0 && (
                    <span className={`w-1.5 h-1.5 rounded-full ${meta.punto}`} />
                  )}
                </button>
              )
            })}
          </div>

          <div className="p-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              {serviciosClasificacionActiva.map((s) => {
                const meta = metaClasificacion(categorias.indexOf(clasificacionActiva))
                const seleccionado = serviciosSeleccionados.includes(s.idProceso)
                const subprocesos = subprocesosPorProceso[s.idProceso]
                const expandida = !!descripcionesExpandidas[s.idProceso]
                const descripcionLarga = (s.descripcion?.length ?? 0) > 110
                return (
                  <div key={s.idProceso} onClick={() => toggleServicio(s.idProceso)}
                    className={`rounded-xl border p-5 cursor-pointer transition-colors ${
                      seleccionado ? meta.tarjeta : 'border-gray-200 hover:border-gray-300'
                    }`}>
                    <div className="flex items-start gap-3">
                      <span className={`mt-0.5 flex-shrink-0 w-[18px] h-[18px] rounded-full border-2 flex items-center justify-center ${
                        seleccionado ? 'border-primary-600' : 'border-gray-300'
                      }`}>
                        {seleccionado && <span className="w-2 h-2 rounded-full bg-primary-600" />}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-base font-semibold text-gray-800">{s.nombreProceso}</p>
                        {s.descripcion && (
                          <p className={`text-sm text-gray-500 mt-1 ${expandida ? '' : 'line-clamp-2'}`}>
                            {s.descripcion}
                          </p>
                        )}
                        {s.descripcion && descripcionLarga && (
                          <button type="button"
                            onClick={(e) => {
                              e.stopPropagation()
                              setDescripcionesExpandidas((prev) => ({ ...prev, [s.idProceso]: !expandida }))
                            }}
                            className={`text-xs font-medium mt-1 hover:underline ${meta.texto}`}>
                            {expandida ? 'Ver menos' : 'Ver más'}
                          </button>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center justify-between gap-2 mt-4 pt-3 border-t border-gray-100">
                      <p className="text-xs text-gray-400">Valor base</p>
                      <p className="text-base font-semibold text-primary-700">{formatearPrecio(s.valor)}</p>
                    </div>

                    {/* Subprocesos incluidos — siempre visibles, sin botón */}
                    <div className="mt-3 pt-3 border-t border-gray-100">
                      <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide mb-2">Incluye</p>
                      {!subprocesos ? (
                        <div className="flex items-center gap-2 py-1">
                          <div className="animate-spin rounded-full h-3.5 w-3.5 border-2 border-primary-300 border-t-transparent" />
                          <span className="text-xs text-gray-400">Cargando...</span>
                        </div>
                      ) : subprocesos.length === 0 ? (
                        <p className="text-xs text-gray-400">Sin subprocesos configurados.</p>
                      ) : (
                        <ul className="space-y-1.5">
                          {subprocesos.map((sp, i) => (
                            <li key={i} className="flex items-center gap-2.5 text-sm text-gray-600">
                              <span className={`w-4 h-4 rounded-full text-[10px] font-bold flex items-center justify-center flex-shrink-0 ${meta.badgeNum}`}>
                                {i + 1}
                              </span>
                              <span className="flex-1 min-w-0 truncate">{sp.nombreProgreso}</span>
                              {sp.descripcion && (
                                <button type="button"
                                  onClick={(e) => { e.stopPropagation(); setInfoSubproceso(sp) }}
                                  title="Ver información"
                                  className="flex-shrink-0 w-5 h-5 rounded-full border border-gray-300 text-gray-400 hover:border-primary-400 hover:text-primary-600 hover:bg-primary-50 transition-colors flex items-center justify-center">
                                  <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                                  </svg>
                                </button>
                              )}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
            {serviciosSeleccionados.length === 0 && (
              <p className="text-sm text-red-500 mt-4">Selecciona un servicio para continuar.</p>
            )}
          </div>
          </>
          )}
        </div>
      )}

      {/* Paso 2: Confirmar */}
      {paso === 2 && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-800">Resumen de la solicitud</h2>

          <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
            {/* Datos del evaluado */}
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Datos del evaluado</h3>
              <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm">
                <div>
                  <span className="text-gray-500">Nombre: </span>
                  <span className="font-medium text-gray-800">{evaluado.nombres} {evaluado.apellidos}</span>
                </div>
                <div>
                  <span className="text-gray-500">Cédula: </span>
                  <span className="font-medium text-gray-800">{evaluado.cedula}</span>
                </div>
                {evaluado.cargo && (
                  <div>
                    <span className="text-gray-500">Cargo: </span>
                    <span className="text-gray-800">{evaluado.cargo}</span>
                  </div>
                )}
                {ciudadSeleccionada && (
                  <div>
                    <span className="text-gray-500">Ciudad: </span>
                    <span className="text-gray-800">{ciudadSeleccionada.nombreCiudad} — {ciudadSeleccionada.departamento}</span>
                  </div>
                )}
                {evaluado.celular && (
                  <div>
                    <span className="text-gray-500">Teléfono: </span>
                    <span className="text-gray-800">{evaluado.celular}</span>
                  </div>
                )}
              </div>
            </div>

            {/* Servicios */}
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Servicio solicitado</h3>
              <ul className="space-y-1.5">
                {serviciosSeleccionadosData.map((s) => (
                  <li key={s.idProceso} className="flex items-center gap-2 text-sm text-gray-800">
                    <span className="text-green-500 font-bold text-base">✓</span>
                    {s.nombreProceso}
                  </li>
                ))}
              </ul>
            </div>

            {/* Tiempo estimado */}
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Tiempo estimado de entrega</h3>
              <p className="text-sm text-gray-800">
                <span className="font-semibold text-primary-600">
                  {maxDiasEntrega} día{maxDiasEntrega !== 1 ? 's' : ''} hábil{maxDiasEntrega !== 1 ? 'es' : ''}
                </span>
              </p>
              {fechaEntrega && (
                <p className="text-sm text-gray-500 mt-0.5">
                  Fecha estimada:{' '}
                  <span className="font-medium text-gray-700 capitalize">{formatFechaEspanol(fechaEntrega)}</span>
                </p>
              )}
              {solicitudDespuesde4pm && (
                <p className="text-xs text-amber-600 mt-1.5">
                  Solicitud después de las 4pm — el conteo inicia el siguiente día hábil.
                </p>
              )}
            </div>

            {/* Costo */}
            <div className="px-5 py-4">
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Costo estimado</h3>
              {(() => {
                if (esPospago) {
                  return (
                    <div className="flex items-start gap-3 rounded-lg border border-sky-200 bg-sky-50 px-3.5 py-3">
                      <span className="text-lg leading-none flex-shrink-0">🏦</span>
                      <p className="text-sm text-sky-800">
                        Tu cuenta opera con crédito pospago — este servicio se solicita directamente y se factura
                        según tu ciclo de facturación, sin necesidad de comprarlo por adelantado.
                      </p>
                    </div>
                  )
                }
                const proceso = serviciosSeleccionadosData[0]
                const saldo = proceso ? saldos.find((s) => s.idProceso === proceso.idProceso) : null
                if (saldo && saldo.cantidadDisponible > 0) {
                  return (
                    <div className="flex items-start gap-3 rounded-lg border border-green-200 bg-green-50 px-3.5 py-3">
                      <span className="text-lg leading-none flex-shrink-0">✅</span>
                      <p className="text-sm text-green-800">
                        Ya tienes <span className="font-bold">{saldo.cantidadDisponible}</span> unidad{saldo.cantidadDisponible !== 1 ? 'es' : ''} comprada{saldo.cantidadDisponible !== 1 ? 's' : ''} de "{proceso.nombreProceso}" — se descontará 1 de tu bolsa de servicios prepago al enviar esta solicitud.
                      </p>
                    </div>
                  )
                }
                return (
                  <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-3.5 py-3">
                    <span className="text-lg leading-none flex-shrink-0">⚠️</span>
                    <p className="text-sm text-amber-800">
                      No tienes saldo disponible{proceso ? ` de "${proceso.nombreProceso}"` : ''} en tu bolsa prepago.{' '}
                      <a href="/cliente/comprar-servicios" className="font-semibold underline hover:text-amber-900">
                        Cómpralo en Comprar Servicios
                      </a>{' '}
                      antes de enviar esta solicitud.
                    </p>
                  </div>
                )
              })()}
            </div>
          </div>

          {/* Notas */}
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
            <label className="block text-sm font-medium text-gray-700 mb-1">Notas adicionales</label>
            <textarea rows={3}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none"
              value={notas} onChange={(e) => setNotas(e.target.value)}
              placeholder="Instrucciones especiales, contexto del cargo, etc." />
          </div>

          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
          )}
        </div>
      )}

      {/* Navegación */}
      <div className="flex justify-between mt-6">
        <button onClick={() => setPaso((p) => p - 1)} disabled={paso === 0}
          className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
          Volver
        </button>
        {paso < 2 ? (
          <button onClick={() => setPaso((p) => p + 1)} disabled={!camposValidos()}
            className="px-5 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg disabled:opacity-40 hover:bg-primary-700">
            Siguiente
          </button>
        ) : (
          <button onClick={enviar} disabled={enviando}
            className="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-lg disabled:opacity-40 hover:bg-green-700">
            {enviando ? 'Enviando...' : 'Confirmar y enviar'}
          </button>
        )}
      </div>

      {/* Modal pequeño: info de un subproceso */}
      {infoSubproceso && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
          onClick={() => setInfoSubproceso(null)}>
          <div onClick={(e) => e.stopPropagation()}
            className="bg-white rounded-2xl shadow-xl w-full max-w-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100 flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-gray-900">{infoSubproceso.nombreProgreso}</p>
                <span className={`inline-block mt-1 text-[10px] px-1.5 py-0.5 rounded-full ${
                  infoSubproceso.obligatorio === false ? 'bg-gray-100 text-gray-500' : 'bg-primary-100 text-primary-700'
                }`}>
                  {infoSubproceso.obligatorio === false ? 'Opcional' : 'Obligatorio'}
                </span>
              </div>
              <button onClick={() => setInfoSubproceso(null)}
                className="text-gray-400 hover:text-gray-600 text-xl leading-none flex-shrink-0">×</button>
            </div>
            <div className="px-5 py-4">
              <p className="text-sm text-gray-600">{infoSubproceso.descripcion}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default NewRequest
