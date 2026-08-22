import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import catalogoService from '../../services/catalogoService'
import pagosService from '../../services/pagosService'
import dashboardService from '../../services/dashboardService'
import { useCarrito } from '../../hooks/useCarrito'
import { useAuth } from '../../hooks/useAuth'
import { useFavoritos } from '../../hooks/useFavoritos'
import { metaCategoria, ordenarCategorias, formatearPrecio, resolverPrecioUnitario } from '../../utils/catalogoDisplay'
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

/* ─── Tarjeta de servicio (catálogo, estilo tienda — sin imagen) ─── */
function TarjetaServicioCompra({ servicio, meta, onAgregar, onVistaPrevia, favorito, onToggleFavorito }) {
  const [cantidad, setCantidad] = useState(1)
  const sinPrecio = !servicio.valor
  const tramos = servicio.tramosPrecio ?? []
  const precioVigente = resolverPrecioUnitario(servicio.valor, tramos, cantidad)
  const conDescuento = !sinPrecio && precioVigente < servicio.valor

  return (
    <div className="relative rounded-xl border border-gray-200 bg-white p-5 flex flex-col gap-3 transition-shadow hover:shadow-md">
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

      <div className="pr-16">
        <span className="text-[10px] font-semibold uppercase tracking-wide text-gray-400">{meta.titulo}</span>
        <h4 className="font-semibold text-gray-800 mt-0.5 mb-1">{servicio.nombreProceso}</h4>
        {servicio.descripcion && (
          <p className="text-sm text-gray-600 line-clamp-2">{servicio.descripcion}</p>
        )}
      </div>

      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-baseline gap-2">
          <p className="text-base font-bold text-gray-800">{formatearPrecio(precioVigente)}</p>
          {conDescuento && (
            <p className="text-xs text-gray-400 line-through">{formatearPrecio(servicio.valor)}</p>
          )}
        </div>
        {servicio.diasHabilesEntrega != null && (
          <span className="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 border border-gray-200 text-gray-600">
            {servicio.diasHabilesEntrega} día{servicio.diasHabilesEntrega !== 1 ? 's' : ''} hábil{servicio.diasHabilesEntrega !== 1 ? 'es' : ''}
          </span>
        )}
      </div>

      {tramos.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {[...tramos].sort((a, b) => a.cantidadMinima - b.cantidadMinima).map((t) => (
            <span key={t.idTramo}
              className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${
                cantidad >= t.cantidadMinima
                  ? 'bg-green-100 border-green-200 text-green-700'
                  : 'bg-gray-50 border-gray-200 text-gray-500'
              }`}>
              {t.cantidadMinima}+ und: {formatearPrecio(t.valorUnitario)}
            </span>
          ))}
        </div>
      )}

      {sinPrecio ? (
        <p className="mt-auto text-xs text-gray-400 italic">No disponible para compra en línea</p>
      ) : (
        <div className="mt-auto pt-2 flex items-center gap-2">
          <div className="flex items-center border border-gray-200 rounded-lg bg-white flex-shrink-0">
            <button onClick={() => setCantidad((c) => Math.max(1, c - 1))}
              className="px-2.5 py-1.5 text-gray-500 hover:text-gray-800 transition-colors">−</button>
            <input
              type="text"
              inputMode="numeric"
              value={cantidad}
              onChange={(e) => {
                const valor = e.target.value.replace(/\D/g, '')
                setCantidad(valor === '' ? '' : Number(valor))
              }}
              onBlur={() => setCantidad((c) => (c === '' || c < 1 ? 1 : c))}
              className="w-10 text-center text-sm font-medium text-gray-700 bg-transparent outline-none [appearance:textfield]"
            />
            <button onClick={() => setCantidad((c) => (c === '' ? 1 : c + 1))}
              className="px-2.5 py-1.5 text-gray-500 hover:text-gray-800 transition-colors">+</button>
          </div>
          <button onClick={() => onAgregar(servicio, cantidad === '' || cantidad < 1 ? 1 : cantidad)}
            className="flex-1 text-xs font-semibold px-3 py-2 rounded-lg bg-primary-600 hover:bg-primary-700 text-white transition-colors">
            Agregar
          </button>
        </div>
      )}
    </div>
  )
}

/* ─── Modal de vista previa — qué incluye el servicio ─── */
function ModalVistaPrevia({ servicio, subprocesos, cargando, onClose, favorito, onToggleFavorito }) {
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
          <span className="text-base font-bold text-primary-700">{formatearPrecio(servicio.valor)}</span>
        </div>

        {servicio.tramosPrecio?.length > 0 && (
          <div>
            <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide mb-2">Precio por volumen</p>
            <ul className="space-y-1.5">
              {[...servicio.tramosPrecio].sort((a, b) => a.cantidadMinima - b.cantidadMinima).map((t) => (
                <li key={t.idTramo} className="flex items-center justify-between text-sm">
                  <span className="text-gray-600">Desde {t.cantidadMinima} unidades</span>
                  <span className="font-semibold text-green-700">{formatearPrecio(t.valorUnitario)} c/u</span>
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

/* ─── Pestaña: catálogo ─── */
function TabCatalogo({ onToast }) {
  const [servicios, setServicios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [categoriaActiva, setCategoriaActiva] = useState('Todas')
  const [busqueda, setBusqueda] = useState('')
  const [orden, setOrden] = useState('relevancia')
  const [vistaPrevia, setVistaPrevia] = useState(null)
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

  const soloFavoritos = categoriaActiva === 'FAVORITOS'
  const serviciosFiltrados = servicios
    .filter((s) => !soloFavoritos || esFavorito(s.idProceso))
    .filter((s) => coincideTexto(s, busqueda))
  const porCategoria = serviciosFiltrados.reduce((acc, s) => {
    if (!acc[s.clasificacion]) acc[s.clasificacion] = []
    acc[s.clasificacion].push(s)
    return acc
  }, {})
  const categoriasOrdenadas = ordenarCategorias(
    servicios.reduce((acc, s) => {
      if (!acc[s.clasificacion]) acc[s.clasificacion] = []
      acc[s.clasificacion].push(s)
      return acc
    }, {})
  )
  const categoriasVisibles = soloFavoritos || categoriaActiva === 'Todas'
    ? categoriasOrdenadas
    : categoriasOrdenadas.filter((c) => c === categoriaActiva)
  const totalFavoritos = servicios.filter((s) => esFavorito(s.idProceso)).length
  const totalResultados = serviciosFiltrados.length

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
        <select value={orden} onChange={(e) => setOrden(e.target.value)}
          className="border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none bg-white sm:w-56">
          {OPCIONES_ORDEN.map((o) => (
            <option key={o.valor} value={o.valor}>{o.etiqueta}</option>
          ))}
        </select>
      </div>

      {/* Recomendación inteligente según lo que escribió el cliente */}
      {recomendacion && (
        <div className="flex items-start gap-3 rounded-lg border border-primary-200 bg-primary-50 px-4 py-3">
          <span className="text-lg leading-none flex-shrink-0">💡</span>
          <div className="flex-1 min-w-0">
            <p className="text-sm text-primary-900">{recomendacion.mensaje}</p>
          </div>
          <button onClick={() => { setCategoriaActiva(recomendacion.clasificaciones[0]); setBusqueda('') }}
            className="flex-shrink-0 text-xs font-semibold text-primary-700 hover:text-primary-900 whitespace-nowrap underline">
            Ver recomendados →
          </button>
        </div>
      )}

      {/* Filtro de categorías */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex gap-2 overflow-x-auto pb-1">
          <button onClick={() => setCategoriaActiva('Todas')}
            className={`flex-shrink-0 text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
              categoriaActiva === 'Todas'
                ? 'bg-primary-600 border-primary-600 text-white'
                : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}>
            Todas
          </button>
          {categoriasOrdenadas.map((cat) => {
            const meta = metaCategoria(cat)
            const activa = categoriaActiva === cat
            return (
              <button key={cat} onClick={() => setCategoriaActiva(cat)}
                className={`flex-shrink-0 flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
                  activa ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                }`}>
                <span>{meta.icono}</span>
                {meta.titulo}
              </button>
            )
          })}
          <button onClick={() => setCategoriaActiva('FAVORITOS')}
            className={`flex-shrink-0 flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
              soloFavoritos ? 'bg-red-500 border-red-500 text-white' : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}>
            <IconoCorazon relleno={soloFavoritos} className="h-3.5 w-3.5" />
            Favoritos{totalFavoritos > 0 ? ` (${totalFavoritos})` : ''}
          </button>
        </div>
        {busqueda.trim() && (
          <p className="text-xs text-gray-400 flex-shrink-0">
            {totalResultados} resultado{totalResultados !== 1 ? 's' : ''}
          </p>
        )}
      </div>

      {/* Secciones por categoría */}
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
            const meta = metaCategoria(cat)

            return (
              <section key={cat}>
                <div className="flex items-center gap-2 mb-4">
                  <span className="text-xl">{meta.icono}</span>
                  <h3 className="text-lg font-semibold text-gray-700">{meta.titulo}</h3>
                  <span className="text-xs px-2 py-0.5 rounded-full font-medium bg-gray-100 text-gray-600">
                    {itemsCat.length} {itemsCat.length === 1 ? 'servicio' : 'servicios'}
                  </span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {itemsCat.map((s) => (
                    <TarjetaServicioCompra key={s.idProceso} servicio={s} meta={meta}
                      onAgregar={agregarAlCarrito}
                      onVistaPrevia={abrirVistaPrevia}
                      favorito={esFavorito(s.idProceso)}
                      onToggleFavorito={toggleFavorito} />
                  ))}
                </div>
              </section>
            )
          })}
        </div>
      )}

      {vistaPrevia && (
        <ModalVistaPrevia
          servicio={vistaPrevia}
          subprocesos={subprocesosCache[vistaPrevia.idProceso]}
          cargando={cargandoPrevia && !subprocesosCache[vistaPrevia.idProceso]}
          onClose={() => setVistaPrevia(null)}
          favorito={esFavorito(vistaPrevia.idProceso)}
          onToggleFavorito={toggleFavorito}
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
