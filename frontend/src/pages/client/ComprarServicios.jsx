import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import catalogoService from '../../services/catalogoService'
import pagosService from '../../services/pagosService'
import dashboardService from '../../services/dashboardService'
import { useCarrito } from '../../hooks/useCarrito'
import { useAuth } from '../../hooks/useAuth'
import { useFavoritos } from '../../hooks/useFavoritos'
import { metaCategoria, ordenarCategorias, formatearPrecio } from '../../utils/catalogoDisplay'
import { recomendarPorTexto, coincideTexto } from '../../utils/busquedaInteligente'
import { Modal } from '../../components/ui/Modal'
import Toast from '../../components/Toast'

const OPCIONES_ORDEN = [
  { valor: 'relevancia',   etiqueta: 'Más relevantes' },
  { valor: 'precio-asc',   etiqueta: 'Precio: menor a mayor' },
  { valor: 'precio-desc',  etiqueta: 'Precio: mayor a menor' },
  { valor: 'nombre',       etiqueta: 'Nombre A-Z' },
]

function ordenarServicios(lista, orden) {
  const copia = [...lista]
  switch (orden) {
    case 'precio-asc':  return copia.sort((a, b) => (a.valor ?? Infinity) - (b.valor ?? Infinity))
    case 'precio-desc': return copia.sort((a, b) => (b.valor ?? -Infinity) - (a.valor ?? -Infinity))
    case 'nombre':       return copia.sort((a, b) => a.nombreProceso.localeCompare(b.nombreProceso, 'es'))
    default:             return copia
  }
}

function IconoOjo({ className = 'h-4 w-4' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
    </svg>
  )
}

function IconoCorazon({ relleno, className = 'h-4 w-4' }) {
  return relleno ? (
    <svg className={className} fill="currentColor" viewBox="0 0 24 24">
      <path d="M12 20.727c-.246 0-.489-.06-.71-.174C8.373 19.1 3 15.66 3 10.412 3 7.417 5.373 5 8.3 5c1.542 0 3.01.71 3.7 1.83A4.437 4.437 0 0115.7 5C18.627 5 21 7.417 21 10.412c0 5.248-5.373 8.688-8.29 10.141-.221.114-.464.174-.71.174z" />
    </svg>
  ) : (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
    </svg>
  )
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

const ESTADO_ORDEN_CFG = {
  PENDIENTE: { label: 'Pendiente de pago', badge: 'bg-amber-100 text-amber-700' },
  APROBADA:  { label: 'Aprobada',          badge: 'bg-green-100 text-green-700' },
  RECHAZADA: { label: 'Rechazada',         badge: 'bg-red-100 text-red-600' },
  EXPIRADA:  { label: 'Expirada',          badge: 'bg-gray-100 text-gray-500' },
}

function EstadoOrdenBadge({ estado }) {
  const cfg = ESTADO_ORDEN_CFG[estado] ?? { label: estado, badge: 'bg-gray-100 text-gray-500' }
  return <span className={`flex-shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${cfg.badge}`}>{cfg.label}</span>
}

function IconoCheck({ className = 'h-3.5 w-3.5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
    </svg>
  )
}

/* ─── Tarjeta de servicio, estilo "plan de precios" — título, desde + precio grande, CTA, puntos clave ─── */
function TarjetaServicioCompra({ servicio, meta, onAgregar, onVistaPrevia, onVerOfertas, favorito, onToggleFavorito }) {
  const sinPrecio = !servicio.valor
  const tieneOferta = servicio.tramosPrecio?.length > 0
  const puntos = servicio.puntosClave?.length ? servicio.puntosClave.slice(0, 4) : null

  return (
    <div className="relative rounded-2xl border bg-white pt-8 pb-5 px-5 flex flex-col items-center text-center gap-1 transition-shadow hover:shadow-lg border-gray-200">
      {tieneOferta && (
        <span className="absolute -top-3 left-1/2 -translate-x-1/2 bg-primary-600 text-white text-[10px] font-bold uppercase tracking-wide px-3 py-1 rounded-full shadow-sm z-10 whitespace-nowrap">
          🏷️ Oferta
        </span>
      )}

      <div className="absolute top-3 right-3 flex items-center gap-1.5 z-10">
        <button onClick={() => onVistaPrevia(servicio)} title="Vista rápida — qué incluye"
          className="w-7 h-7 rounded-full bg-white shadow-sm border border-gray-100 text-gray-500 hover:text-primary-600 hover:border-primary-200 flex items-center justify-center transition-colors">
          <IconoOjo className="h-3.5 w-3.5" />
        </button>
        <button onClick={() => onToggleFavorito(servicio.idProceso)}
          title={favorito ? 'Quitar de favoritos' : 'Agregar a favoritos'}
          className={`w-7 h-7 rounded-full bg-white shadow-sm border border-gray-100 flex items-center justify-center transition-colors ${
            favorito ? 'text-red-500' : 'text-gray-400 hover:text-red-400'
          }`}>
          <IconoCorazon relleno={favorito} className="h-3.5 w-3.5" />
        </button>
      </div>

      <span className="text-[10px] font-semibold uppercase tracking-wide text-gray-400">{meta.titulo}</span>
      <h4 className="font-bold text-gray-900 text-lg leading-snug">{servicio.nombreProceso}</h4>
      {!puntos && servicio.descripcion && (
        <p className="text-xs text-gray-500 line-clamp-2">{servicio.descripcion}</p>
      )}
      {servicio.diasHabilesEntrega != null && (
        <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 border border-gray-200 text-gray-600 mt-1">
          {servicio.diasHabilesEntrega} día{servicio.diasHabilesEntrega !== 1 ? 's' : ''} hábil{servicio.diasHabilesEntrega !== 1 ? 'es' : ''}
        </span>
      )}

      {!sinPrecio && <p className="text-xs text-gray-400 mt-3">desde</p>}
      <p className={`font-extrabold text-gray-900 leading-none ${sinPrecio ? 'text-base mt-3' : 'text-4xl'}`}>
        {formatearPrecio(servicio.valor)}
        {!sinPrecio && <span className="block text-xs font-normal text-gray-400 mt-1">por unidad</span>}
      </p>

      {sinPrecio ? (
        <p className="mt-3 text-xs text-gray-400 italic">No disponible para compra en línea</p>
      ) : (
        <button onClick={() => tieneOferta ? onVerOfertas(servicio) : onAgregar(servicio, 1)}
          className="w-full mt-4 text-sm font-semibold px-3 py-3 rounded-xl bg-primary-600 hover:bg-primary-700 text-white transition-colors">
          Agregar
        </button>
      )}

      {puntos && (
        <ul className="w-full text-left mt-4 space-y-2">
          {puntos.map((punto, i) => (
            <li key={i} className="flex items-start gap-2 text-sm text-gray-600">
              <IconoCheck className="h-3.5 w-3.5 text-primary-500 flex-shrink-0 mt-0.5" />
              <span className="line-clamp-2">{punto}</span>
            </li>
          ))}
        </ul>
      )}

      <div className="w-full border-t border-gray-100 pt-2.5 mt-4">
        {servicio.aplicaPrecioCiudad && servicio.preciosCiudad?.length > 0 ? (
          <button onClick={() => onVistaPrevia(servicio)}
            className="w-full flex items-center justify-center gap-1.5 text-[11px] text-gray-500 hover:text-primary-600 transition-colors">
            📍 Precio varía según ciudad
          </button>
        ) : (
          <p className="text-[11px] text-gray-300 text-center">No aplica precio por ciudad</p>
        )}
      </div>
    </div>
  )
}

/* ─── Modal de vista previa — qué incluye el servicio, y desde aquí también las ofertas por volumen ─── */
const ETIQUETA_NIVEL_CIUDAD = {
  PRINCIPAL: 'Ciudad principal',
  INTERMEDIA: 'Intermedia / municipio principal',
  MUNICIPIO_SECUNDARIO: 'Municipio secundario',
}

function ModalVistaPrevia({ servicio, subprocesos, cargando, onClose, favorito, onToggleFavorito, onAgregar }) {
  return (
    <Modal titulo={servicio.nombreProceso} subtitulo="Vista previa del servicio" onClose={onClose} ancho="max-w-md">
      <div className="p-6 space-y-4">
        <div className="flex items-start justify-between gap-3">
          {servicio.descripcion && <p className="text-sm text-gray-600">{servicio.descripcion}</p>}
          <button onClick={() => onToggleFavorito(servicio.idProceso)}
            title={favorito ? 'Quitar de favoritos' : 'Agregar a favoritos'}
            className={`flex-shrink-0 w-8 h-8 rounded-full border flex items-center justify-center transition-colors ${
              favorito ? 'border-red-200 bg-red-50 text-red-500' : 'border-gray-200 text-gray-400 hover:text-red-400'
            }`}>
            <IconoCorazon relleno={favorito} className="h-4 w-4" />
          </button>
        </div>

        <div className="flex items-center justify-between border-y border-gray-100 py-3">
          <span className="text-sm text-gray-500">Precio</span>
          <div className="flex items-center gap-3">
            <span className="text-base font-bold text-primary-700">{formatearPrecio(servicio.valor)}</span>
            {servicio.valor > 0 && (
              <button onClick={() => onAgregar(servicio, 1)}
                className="text-xs font-semibold text-white bg-primary-600 hover:bg-primary-700 px-3 py-1.5 rounded-lg transition-colors">
                Agregar
              </button>
            )}
          </div>
        </div>

        {servicio.aplicaPrecioCiudad && servicio.preciosCiudad?.length > 0 && (
          <div>
            <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide mb-2">📍 Recargo por ciudad</p>
            <p className="text-xs text-gray-400 mb-2">Se suma al precio según la ciudad del evaluado.</p>
            <ul className="space-y-1.5">
              {[...servicio.preciosCiudad].sort((a, b) => a.nivelCiudad.localeCompare(b.nivelCiudad)).map((pc) => (
                <li key={pc.idPrecioCiudad} className="flex items-center justify-between text-sm gap-2">
                  <span className="text-gray-600">{ETIQUETA_NIVEL_CIUDAD[pc.nivelCiudad] ?? pc.nivelCiudad}</span>
                  <span className="font-semibold text-gray-700 flex-shrink-0">
                    {pc.valor > 0 ? `+ ${formatearPrecio(pc.valor)}` : 'Sin recargo'}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div>
          <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide mb-2">Incluye</p>
          {cargando ? (
            <div className="flex items-center gap-2 py-1">
              <div className="animate-spin rounded-full h-3.5 w-3.5 border-2 border-primary-300 border-t-transparent" />
              <span className="text-xs text-gray-400">Cargando...</span>
            </div>
          ) : !subprocesos?.length ? (
            <p className="text-xs text-gray-400">Sin subprocesos configurados.</p>
          ) : (
            <ul className="space-y-2.5">
              {subprocesos.map((sp, i) => (
                <li key={i} className="flex items-start gap-2.5 text-sm text-gray-700">
                  <span className="w-5 h-5 rounded-full bg-primary-100 text-primary-700 text-[10px] font-bold flex items-center justify-center flex-shrink-0 mt-0.5">
                    {i + 1}
                  </span>
                  <div className="min-w-0">
                    <p>{sp.nombreProgreso}</p>
                    {sp.descripcion && <p className="text-xs text-gray-400 mt-0.5">{sp.descripcion}</p>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Modal>
  )
}

/* ─── Modal de ofertas actuales — una tarjetica por cada paquete de volumen disponible ─── */
function ModalOfertas({ servicio, onClose, onAgregar }) {
  const opciones = [
    { cantidad: 1, precio: servicio.valor, esBase: true, key: 'base' },
    ...[...(servicio.tramosPrecio ?? [])]
      .sort((a, b) => a.cantidadMinima - b.cantidadMinima)
      .map((t) => ({ cantidad: t.cantidadMinima, precio: t.valorUnitario, esBase: false, key: t.idTramo })),
  ]

  return (
    <Modal titulo={`🏷️ Ofertas actuales`} subtitulo={servicio.nombreProceso} onClose={onClose} ancho="max-w-lg">
      <div className="p-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {opciones.map((o) => {
            const ahorro = !o.esBase && servicio.valor ? servicio.valor - o.precio : 0
            return (
              <div key={o.key} className={`rounded-xl border p-4 flex flex-col gap-1.5 ${
                o.esBase ? 'border-gray-200' : 'border-primary-300 bg-primary-50/30'
              }`}>
                <p className="text-xs font-medium text-gray-500">
                  {o.esBase ? 'Precio individual' : `Desde ${o.cantidad} unidades`}
                </p>
                <p className="text-xl font-extrabold text-gray-900 leading-none">
                  {formatearPrecio(o.precio)}
                  <span className="text-xs font-normal text-gray-400"> / und</span>
                </p>
                {ahorro > 0 && (
                  <p className="text-xs font-medium text-green-600">Ahorras {formatearPrecio(ahorro)} por unidad</p>
                )}
                <button onClick={() => onAgregar(servicio, o.cantidad)}
                  className="mt-auto pt-2 w-full text-xs font-semibold px-3 py-2 rounded-lg bg-primary-600 hover:bg-primary-700 text-white transition-colors">
                  {o.esBase ? 'Agregar' : `Agregar ${o.cantidad} unidades`}
                </button>
              </div>
            )
          })}
        </div>
      </div>
    </Modal>
  )
}

/* ─── Grilla de clasificaciones — landing del catálogo, mismo patrón que el admin ─── */
function GridClasificaciones({ categorias, porCategoria, totalFavoritos, onSeleccionar }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
      {categorias.map((cat) => {
        const total = porCategoria[cat]?.length ?? 0
        const meta = metaCategoria(cat, porCategoria[cat]?.[0]?.codigoClasificacion)
        return (
          <button key={cat} onClick={() => onSeleccionar(cat)}
            className="group text-left bg-white rounded-2xl border border-gray-200 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 overflow-hidden">
            <div className={`h-2 w-full ${meta.fondo}`} />
            <div className="p-6">
              <div className="flex items-start justify-between gap-4">
                <div className={`w-14 h-14 rounded-2xl flex items-center justify-center text-2xl flex-shrink-0 ${meta.fondo}`}>
                  {meta.icono}
                </div>
                <div className="text-right">
                  <p className="text-3xl font-bold text-gray-900 leading-none">{total}</p>
                  <p className="text-xs text-gray-400 mt-1">servicio{total !== 1 ? 's' : ''}</p>
                </div>
              </div>
              <div className="mt-4">
                <p className="text-sm font-semibold text-gray-800 leading-snug">{meta.titulo}</p>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${meta.badge}`}>
                  {total} servicio{total !== 1 ? 's' : ''}
                </span>
                <span className="text-xs text-gray-400 group-hover:text-primary-600 transition-colors flex items-center gap-1">
                  Ver servicios
                  <svg className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </span>
              </div>
            </div>
          </button>
        )
      })}

      {totalFavoritos > 0 && (
        <button onClick={() => onSeleccionar('FAVORITOS')}
          className="group text-left bg-white rounded-2xl border border-gray-200 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 overflow-hidden">
          <div className="h-2 w-full bg-red-100" />
          <div className="p-6">
            <div className="flex items-start justify-between gap-4">
              <div className="w-14 h-14 rounded-2xl flex items-center justify-center bg-red-50 flex-shrink-0">
                <IconoCorazon relleno className="h-6 w-6 text-red-500" />
              </div>
              <div className="text-right">
                <p className="text-3xl font-bold text-gray-900 leading-none">{totalFavoritos}</p>
                <p className="text-xs text-gray-400 mt-1">favorito{totalFavoritos !== 1 ? 's' : ''}</p>
              </div>
            </div>
            <div className="mt-4">
              <p className="text-sm font-semibold text-gray-800 leading-snug">Favoritos</p>
            </div>
            <div className="mt-4 flex items-center justify-between">
              <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-red-100 text-red-700">
                Guardados por ti
              </span>
              <span className="text-xs text-gray-400 group-hover:text-primary-600 transition-colors flex items-center gap-1">
                Ver servicios
                <svg className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                </svg>
              </span>
            </div>
          </div>
        </button>
      )}
    </div>
  )
}

/* ─── Pestaña: catálogo ─── */
function TabCatalogo({ onToast }) {
  const [servicios, setServicios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  // null = viendo la grilla de clasificaciones (landing); 'FAVORITOS' o un código de clasificación = viendo esa lista
  const [categoriaSeleccionada, setCategoriaSeleccionada] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [orden, setOrden] = useState('relevancia')
  const [vistaPrevia, setVistaPrevia] = useState(null)
  const [ofertasAbiertas, setOfertasAbiertas] = useState(null)
  const [subprocesosCache, setSubprocesosCache] = useState({})
  const [cargandoPrevia, setCargandoPrevia] = useState(false)
  const { agregarItem } = useCarrito()
  const { esFavorito, toggleFavorito } = useFavoritos()

  const recomendacion = useMemo(() => recomendarPorTexto(busqueda), [busqueda])

  useEffect(() => {
    catalogoService.listarServicios()
      .then(setServicios)
      .catch(() => setError('No se pudo cargar el catálogo. Intenta nuevamente.'))
      .finally(() => setCargando(false))
  }, [])

  const agregarAlCarrito = (servicio, cantidad) => {
    agregarItem(servicio, cantidad)
    onToast(`${servicio.nombreProceso} agregado al carrito`)
  }

  const abrirVistaPrevia = (servicio) => {
    setVistaPrevia(servicio)
    if (subprocesosCache[servicio.idProceso]) return
    setCargandoPrevia(true)
    catalogoService.obtenerSubprocesos(servicio.idProceso)
      .then((data) => setSubprocesosCache((prev) => ({ ...prev, [servicio.idProceso]: data })))
      .catch(() => setSubprocesosCache((prev) => ({ ...prev, [servicio.idProceso]: [] })))
      .finally(() => setCargandoPrevia(false))
  }

  if (cargando) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-red-500 text-sm">{error}</p>
      </div>
    )
  }

  const porCategoriaTotal = servicios.reduce((acc, s) => {
    if (!acc[s.clasificacion]) acc[s.clasificacion] = []
    acc[s.clasificacion].push(s)
    return acc
  }, {})
  const categoriasOrdenadas = ordenarCategorias(porCategoriaTotal)
  // nombre -> código: el ícono/color de la categoría se busca por código (estable), no por
  // nombre (lo puede renombrar el admin en cualquier momento y dejaría de encontrar el estilo).
  const codigoPorCategoria = servicios.reduce((acc, s) => {
    if (s.codigoClasificacion) acc[s.clasificacion] = s.codigoClasificacion
    return acc
  }, {})
  const totalFavoritos = servicios.filter((s) => esFavorito(s.idProceso)).length

  const enBusqueda = busqueda.trim().length > 0
  const soloFavoritos = categoriaSeleccionada === 'FAVORITOS'
  const mostrarGrid = !enBusqueda && !categoriaSeleccionada

  const serviciosFiltrados = servicios
    .filter((s) => !soloFavoritos || esFavorito(s.idProceso))
    .filter((s) => enBusqueda || soloFavoritos || !categoriaSeleccionada || s.clasificacion === categoriaSeleccionada)
    .filter((s) => coincideTexto(s, busqueda))
  const porCategoria = serviciosFiltrados.reduce((acc, s) => {
    if (!acc[s.clasificacion]) acc[s.clasificacion] = []
    acc[s.clasificacion].push(s)
    return acc
  }, {})
  const categoriasVisibles = enBusqueda || soloFavoritos
    ? categoriasOrdenadas.filter((c) => porCategoria[c]?.length)
    : categoriaSeleccionada ? [categoriaSeleccionada] : []
  const totalResultados = serviciosFiltrados.length

  const volverAGrid = () => { setCategoriaSeleccionada(null); setBusqueda('') }

  return (
    <div className="space-y-5">
      {/* Buscador + orden */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
          </svg>
          <input type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)}
            placeholder='Busca por nombre o cuéntanos qué necesitas — ej. "validar un robo"'
            className="w-full border border-gray-200 rounded-lg pl-9 pr-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        {!mostrarGrid && (
          <select value={orden} onChange={(e) => setOrden(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none bg-white sm:w-56">
            {OPCIONES_ORDEN.map((o) => (
              <option key={o.valor} value={o.valor}>{o.etiqueta}</option>
            ))}
          </select>
        )}
        {mostrarGrid && totalFavoritos > 0 && (
          <button onClick={() => setCategoriaSeleccionada('FAVORITOS')}
            className="flex-shrink-0 flex items-center justify-center gap-1.5 text-sm font-medium px-4 py-2.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 transition-colors">
            <IconoCorazon relleno className="h-4 w-4 text-red-500" />
            Favoritos ({totalFavoritos})
          </button>
        )}
      </div>

      {/* Recomendación inteligente según lo que escribió el cliente */}
      {recomendacion && (
        <div className="flex items-start gap-3 rounded-lg border border-primary-200 bg-primary-50 px-4 py-3">
          <span className="text-lg leading-none flex-shrink-0">💡</span>
          <div className="flex-1 min-w-0">
            <p className="text-sm text-primary-900">{recomendacion.mensaje}</p>
          </div>
          <button onClick={() => { setCategoriaSeleccionada(recomendacion.clasificaciones[0]); setBusqueda('') }}
            className="flex-shrink-0 text-xs font-semibold text-primary-700 hover:text-primary-900 whitespace-nowrap underline">
            Ver recomendados →
          </button>
        </div>
      )}

      {mostrarGrid ? (
        /* ── Landing: elegir clasificación ── */
        categoriasOrdenadas.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-48 gap-2 text-center">
            <p className="text-sm text-gray-400">Todavía no hay servicios disponibles para comprar.</p>
          </div>
        ) : (
          <div>
            <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">Elige una categoría</h3>
            <GridClasificaciones
              categorias={categoriasOrdenadas}
              porCategoria={porCategoriaTotal}
              totalFavoritos={totalFavoritos}
              onSeleccionar={setCategoriaSeleccionada}
            />
          </div>
        )
      ) : (
        <>
          {/* Encabezado de la vista de detalle */}
          <div className="flex items-center gap-3">
            {!enBusqueda && (
              <button onClick={volverAGrid}
                className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 border border-gray-200 px-3 py-1.5 rounded-lg hover:bg-gray-50 transition-colors flex-shrink-0">
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                </svg>
                Categorías
              </button>
            )}
            <h3 className="text-lg font-semibold text-gray-700 flex items-center gap-2">
              {enBusqueda
                ? 'Resultados de tu búsqueda'
                : soloFavoritos
                ? <><IconoCorazon relleno className="h-4 w-4 text-red-500" /> Favoritos</>
                : <>{metaCategoria(categoriaSeleccionada, codigoPorCategoria[categoriaSeleccionada]).icono} {categoriaSeleccionada}</>}
            </h3>
            {enBusqueda && (
              <p className="text-xs text-gray-400 flex-shrink-0">
                {totalResultados} resultado{totalResultados !== 1 ? 's' : ''}
              </p>
            )}
          </div>

          {/* Secciones */}
          {soloFavoritos && totalFavoritos === 0 ? (
            <div className="flex flex-col items-center justify-center h-48 gap-2 text-center">
              <IconoCorazon relleno={false} className="h-6 w-6 text-gray-300" />
              <p className="text-sm text-gray-400">Aún no tienes servicios favoritos. Marca el corazón en una tarjeta para guardarla aquí.</p>
            </div>
          ) : totalResultados === 0 ? (
            <div className="flex flex-col items-center justify-center h-48 gap-2 text-center">
              <svg className="h-8 w-8 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
              </svg>
              <p className="text-sm text-gray-400">No encontramos servicios que coincidan con "{busqueda}".</p>
              <button onClick={() => setBusqueda('')} className="text-xs text-primary-600 hover:underline">Limpiar búsqueda</button>
            </div>
          ) : (
            <div className="space-y-8">
              {categoriasVisibles.map((cat) => {
                const itemsCat = ordenarServicios(porCategoria[cat] ?? [], orden)
                if (!itemsCat.length) return null
                const meta = metaCategoria(cat, codigoPorCategoria[cat])
                const mostrarEncabezadoSeccion = enBusqueda || soloFavoritos

                return (
                  <section key={cat}>
                    {mostrarEncabezadoSeccion && (
                      <div className="flex items-center gap-2 mb-4">
                        <span className="text-xl">{meta.icono}</span>
                        <h4 className="text-base font-semibold text-gray-700">{meta.titulo}</h4>
                        <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-gray-100 text-gray-600">
                          {itemsCat.length} {itemsCat.length === 1 ? 'servicio' : 'servicios'}
                        </span>
                      </div>
                    )}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                      {itemsCat.map((servicio) => (
                        <TarjetaServicioCompra key={servicio.idProceso} servicio={servicio} meta={meta}
                          onAgregar={agregarAlCarrito}
                          onVistaPrevia={abrirVistaPrevia}
                          onVerOfertas={setOfertasAbiertas}
                          favorito={esFavorito(servicio.idProceso)}
                          onToggleFavorito={toggleFavorito} />
                      ))}
                    </div>
                  </section>
                )
              })}
            </div>
          )}
        </>
      )}

      {vistaPrevia && (
        <ModalVistaPrevia
          servicio={vistaPrevia}
          subprocesos={subprocesosCache[vistaPrevia.idProceso]}
          cargando={cargandoPrevia && !subprocesosCache[vistaPrevia.idProceso]}
          onClose={() => setVistaPrevia(null)}
          favorito={esFavorito(vistaPrevia.idProceso)}
          onToggleFavorito={toggleFavorito}
          onAgregar={agregarAlCarrito}
        />
      )}

      {ofertasAbiertas && (
        <ModalOfertas
          servicio={ofertasAbiertas}
          onClose={() => setOfertasAbiertas(null)}
          onAgregar={agregarAlCarrito}
        />
      )}
    </div>
  )
}

/* ─── Pestaña: historial de compras ─── */
function ModalDetalleOrden({ orden, onClose, onToast }) {
  const [pagando, setPagando] = useState(false)
  const puedeReintentar = orden.estado === 'PENDIENTE' || orden.estado === 'RECHAZADA'

  const pagar = async () => {
    setPagando(true)
    try {
      const pago = await pagosService.iniciarPago(orden.idOrdenCompra)
      window.location.href = pago.urlCheckout
    } catch (err) {
      onToast(err.response?.data?.mensaje ?? 'No se pudo iniciar el pago', 'error')
      setPagando(false)
    }
  }

  return (
    <Modal titulo={orden.referencia} subtitulo={formatFecha(orden.fechaCreacion)}
      onClose={pagando ? undefined : onClose} ancho="max-w-md">
      <div className="p-6 space-y-4">
        <div className="flex items-center justify-between">
          <EstadoOrdenBadge estado={orden.estado} />
          <span className="text-xs text-gray-400">{orden.modoSimulado ? 'Pago simulado' : 'Wompi'}</span>
        </div>

        <ul className="divide-y divide-gray-100">
          {orden.items.map((i) => (
            <li key={i.idProceso} className="py-2 flex items-center justify-between text-sm gap-3">
              <span className="text-gray-700">{i.cantidad} × {i.nombreProceso}</span>
              <span className="font-medium text-gray-800 flex-shrink-0">{formatearPrecio(i.subtotal)}</span>
            </li>
          ))}
        </ul>

        <div className="flex items-center justify-between border-t border-gray-100 pt-3">
          <span className="text-sm font-semibold text-gray-600">Total</span>
          <span className="text-lg font-bold text-primary-700">{formatearPrecio(orden.montoTotal)}</span>
        </div>

        {puedeReintentar && (
          <button onClick={pagar} disabled={pagando}
            className="w-full bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium py-2.5 rounded-lg transition-colors">
            {pagando ? 'Redirigiendo...' : orden.estado === 'PENDIENTE' ? 'Completar pago' : 'Reintentar pago'}
          </button>
        )}
      </div>
    </Modal>
  )
}

function TabHistorial({ onToast }) {
  const [ordenes, setOrdenes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [ordenSeleccionada, setOrdenSeleccionada] = useState(null)

  const cargar = useCallback(() => {
    setCargando(true)
    setError(null)
    pagosService.listarOrdenes()
      .then((data) => setOrdenes(data.content ?? data ?? []))
      .catch(() => setError('No se pudo cargar tu historial de compras.'))
      .finally(() => setCargando(false))
  }, [])

  useEffect(() => { cargar() }, [cargar])

  if (cargando) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-2">
        <p className="text-red-500 text-sm">{error}</p>
        <button onClick={cargar} className="text-xs text-primary-600 hover:underline">Reintentar</button>
      </div>
    )
  }

  if (!ordenes.length) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-2 text-center">
        <p className="text-sm text-gray-400">Todavía no has hecho ninguna compra.</p>
      </div>
    )
  }

  return (
    <>
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="px-4 py-3 text-left">Referencia</th>
                <th className="px-4 py-3 text-left">Fecha</th>
                <th className="px-4 py-3 text-left">Servicios</th>
                <th className="px-4 py-3 text-right">Total</th>
                <th className="px-4 py-3 text-left">Estado</th>
                <th className="px-4 py-3 text-center">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {ordenes.map((o) => (
                <tr key={o.idOrdenCompra} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-medium text-gray-800 whitespace-nowrap">{o.referencia}</td>
                  <td className="px-4 py-3 text-gray-500 whitespace-nowrap">{formatFecha(o.fechaCreacion)}</td>
                  <td className="px-4 py-3 text-gray-600 whitespace-nowrap">
                    {o.items.length} servicio{o.items.length !== 1 ? 's' : ''}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold text-gray-800 whitespace-nowrap">
                    {formatearPrecio(o.montoTotal)}
                  </td>
                  <td className="px-4 py-3"><EstadoOrdenBadge estado={o.estado} /></td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-center">
                      <button onClick={() => setOrdenSeleccionada(o)} title="Ver detalle"
                        className="p-1.5 text-gray-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors">
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {ordenSeleccionada && (
        <ModalDetalleOrden
          orden={ordenSeleccionada}
          onClose={() => setOrdenSeleccionada(null)}
          onToast={onToast}
        />
      )}
    </>
  )
}

/* ─── Pestaña: saldo disponible (lo que ya compraste y te queda) ─── */
function TabSaldo() {
  const [saldos, setSaldos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  const cargar = useCallback(() => {
    setCargando(true)
    setError(null)
    dashboardService.obtenerBolsaServicios()
      .then(setSaldos)
      .catch(() => setError('No se pudo cargar tu saldo disponible.'))
      .finally(() => setCargando(false))
  }, [])

  useEffect(() => { cargar() }, [cargar])

  if (cargando) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-2">
        <p className="text-red-500 text-sm">{error}</p>
        <button onClick={cargar} className="text-xs text-primary-600 hover:underline">Reintentar</button>
      </div>
    )
  }

  if (!saldos.length) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-2 text-center">
        <p className="text-sm text-gray-400">Todavía no tienes servicios comprados en tu bolsa.</p>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">Servicio</th>
              <th className="px-4 py-3 text-right">Comprado</th>
              <th className="px-4 py-3 text-right">Consumido</th>
              <th className="px-4 py-3 text-right">Disponible</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {saldos.map((s) => (
              <tr key={s.idProceso} className="hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 font-medium text-gray-800">{s.nombreProceso}</td>
                <td className="px-4 py-3 text-right text-gray-600">{s.cantidadComprada}</td>
                <td className="px-4 py-3 text-right text-gray-600">{s.cantidadConsumida}</td>
                <td className="px-4 py-3 text-right">
                  <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
                    s.cantidadDisponible > 0 ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                  }`}>
                    {s.cantidadDisponible}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

const TABS = [
  { key: 'catalogo',  label: 'Servicios' },
  { key: 'saldo',     label: 'Saldo disponible' },
  { key: 'historial', label: 'Historial de compras' },
]

export default function ComprarServicios() {
  const { usuario } = useAuth()
  const [tab, setTab] = useState('catalogo')
  const [toast, setToast] = useState(null)

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  if (usuario?.tipoCliente === 'POSPAGO') {
    return (
      <div className="max-w-md mx-auto flex flex-col items-center justify-center h-96 gap-3 text-center">
        <span className="text-3xl">🏦</span>
        <h2 className="text-lg font-bold text-gray-800">No necesitas comprar servicios por adelantado</h2>
        <p className="text-sm text-gray-500">
          Tu cuenta opera con crédito pospago: solicita los servicios que necesites directamente
          y se facturan según tu ciclo de facturación.
        </p>
        <Link to="/cliente/nueva-solicitud"
          className="mt-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-5 py-2.5 rounded-lg transition-colors">
          Ir a Nueva Solicitud
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-gray-800">Comprar Servicios</h2>
        <p className="text-sm text-gray-500 mt-1">
          Compra unidades de cada servicio por adelantado — quedan disponibles en tu bolsa prepago
          para usarlas al crear una solicitud.
        </p>
      </div>

      <nav className="flex gap-1 overflow-x-auto border-b border-gray-200 pb-px">
        {TABS.map((t) => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors ${
              tab === t.key
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}>
            {t.label}
          </button>
        ))}
      </nav>

      {tab === 'catalogo' && (
        <section className="animate-fade-in">
          <TabCatalogo onToast={mostrarToast} />
        </section>
      )}

      {tab === 'saldo' && (
        <section className="animate-fade-in">
          <TabSaldo />
        </section>
      )}

      {tab === 'historial' && (
        <section className="animate-fade-in">
          <TabHistorial onToast={mostrarToast} />
        </section>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
