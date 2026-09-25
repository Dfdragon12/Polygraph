import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import dashboardService from '../../services/dashboardService'
import pospagoService from '../../services/pospagoService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'

const formatearCOP = (valor) =>
  new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor ?? 0)

function iniciales(nombre) {
  return (nombre?.[0] ?? '?').toUpperCase()
}

function formatFechaCorta(fecha) {
  if (!fecha) return '—'
  return new Date(fecha).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })
}

const ICONO_ACTIVOS = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
)
const ICONO_PENDIENTES = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
)
const ICONO_FINALIZADOS = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
)
const ICONO_BOLSA = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M20 7h-3V6a4 4 0 00-8 0v1H6a1 1 0 00-1 1v11a2 2 0 002 2h10a2 2 0 002-2V8a1 1 0 00-1-1zM9 6a3 3 0 016 0v1H9V6zm1 6a1 1 0 11-2 0 1 1 0 012 0zm6 0a1 1 0 11-2 0 1 1 0 012 0z" />
  </svg>
)
const ICONO_CREDITO = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3M3.75 6h16.5a1.5 1.5 0 011.5 1.5v9a1.5 1.5 0 01-1.5 1.5H3.75a1.5 1.5 0 01-1.5-1.5v-9a1.5 1.5 0 011.5-1.5z" />
  </svg>
)
const ICONO_CARRITO = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 1.907-4.723 2.269-7.234.061-.422-.256-.766-.681-.766H5.106M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm12.75 0a.75.75 0 11-1.5 0 .75.75 0 011.5 0z" />
  </svg>
)
const ICONO_MAS = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
  </svg>
)
const ICONO_CHEVRON = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
  </svg>
)
const ICONO_ALERTA = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
  </svg>
)
const ICONO_VACIO = (
  <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
  </svg>
)

function KpiCard({ titulo, valor, subtitulo, color, icono, retraso = 0 }) {
  return (
    <div
      style={{ animationDelay: `${retraso}ms` }}
      className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 opacity-0 animate-fade-in [animation-fill-mode:forwards] hover:shadow-md hover:-translate-y-0.5 transition-[transform,box-shadow] duration-200">
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${color}`}>
          {icono}
        </div>
        <p className="text-sm text-gray-500">{titulo}</p>
      </div>
      <p className="text-3xl font-bold text-gray-800 mt-3 tabular-nums">{valor}</p>
      {subtitulo && <p className="text-xs text-gray-400 mt-1">{subtitulo}</p>}
    </div>
  )
}

// Fila tipo "feed" para el listado reciente — reemplaza la tabla anterior: nada de scroll
// horizontal en celular, y cada fila entra con una animación escalonada.
function FilaSolicitud({ s, idx, onClick }) {
  return (
    <button
      onClick={onClick}
      style={{ animationDelay: `${idx * 45}ms` }}
      className="w-full flex items-center gap-3 px-4 sm:px-5 py-3.5 text-left opacity-0 animate-fade-in [animation-fill-mode:forwards] hover:bg-primary-50/50 active:bg-primary-50 transition-colors duration-150">
      <span className="w-9 h-9 rounded-full bg-slate-100 text-slate-600 text-xs font-semibold flex items-center justify-center flex-shrink-0">
        {iniciales(s.nombresEvaluado)}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-gray-800 truncate">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
        <p className="text-xs text-gray-400 truncate">
          {s.proceso || 'Servicio'} · Sol. {formatFechaCorta(s.fechaSolicitud)}
        </p>
      </div>
      <ServiceStatusBadge status={s.estado} />
      <span className="text-gray-300 flex-shrink-0">{ICONO_CHEVRON}</span>
    </button>
  )
}

export default function Dashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
  const [datos, setDatos] = useState(null)
  const [bolsa, setBolsa] = useState(null)
  const [credito, setCredito] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  // Error de la llamada secundaria (bolsa/cupo) — solo para fallas reales (red, etc). El caso ya
  // conocido de "ANALISTA_CLIENTE sin acceso al cupo pospago" ni se intenta pedir (ver abajo), así
  // que esto ya no dispara por eso.
  const [errorSecundario, setErrorSecundario] = useState(null)
  // Se deriva del dashboard recién cargado (tipoCliente en vivo desde BD), nunca del `usuario`
  // cacheado en localStorage al login: si un admin cambia el tipo de cliente (ej. tras aprobar
  // crédito pospago) mientras el JWT del cliente sigue vigente (hasta 24h), ese caché queda
  // desactualizado y el dashboard mostraba el panel equivocado (carrito/bolsa vs. cupo de crédito).
  const esPospago = datos?.tipoCliente === 'POSPAGO'
  // El cupo de crédito pospago solo lo puede consultar ADMIN_CLIENTE (ver FacturaPospagoController
  // en el backend) — un ANALISTA_CLIENTE de un cliente pospago no tiene nada que ver ahí, así que
  // esa sección del dashboard se omite entera para él en vez de mostrarle un error.
  const esAdminCliente = usuario?.rol === 'ADMIN_CLIENTE'
  const puedeVerCupo = esPospago && esAdminCliente

  useEffect(() => {
    let activo = true
    dashboardService.obtenerDashboard()
      .then((dashData) => {
        if (!activo) return
        setDatos(dashData)
        const esPospagoLocal = dashData.tipoCliente === 'POSPAGO'
        if (esPospagoLocal && usuario?.rol !== 'ADMIN_CLIENTE') return
        const segundo = esPospagoLocal
          ? pospagoService.obtenerEstadoCuenta()
          : dashboardService.obtenerBolsaServicios()
        return segundo
          .then((segundoDato) => {
            if (!activo) return
            if (esPospagoLocal) setCredito(segundoDato)
            else setBolsa(Array.isArray(segundoDato) ? segundoDato : [])
          })
          .catch((err) => { if (activo) setErrorSecundario(err.response?.data?.mensaje ?? 'No se pudo cargar tu información de cupo/bolsa.') })
      })
      .catch(() => { if (activo) setError('No se pudo cargar el dashboard. Intenta nuevamente.') })
      .finally(() => { if (activo) setCargando(false) })
    return () => { activo = false }
  }, [usuario?.rol])

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

  const totalBolsa = bolsa?.reduce((acc, b) => acc + (b.cantidad || 0), 0) ?? 0
  const disponiblesBolsa = datos?.serviciosDisponibles ?? 0
  const pctBolsa = totalBolsa > 0 ? Math.round((disponiblesBolsa / totalBolsa) * 100) : 0

  const limiteCredito = credito?.limiteCredito ?? 0
  const creditoDisponible = credito?.creditoDisponible ?? 0
  const pctCreditoUsado = limiteCredito > 0 ? Math.round(((limiteCredito - creditoDisponible) / limiteCredito) * 100) : 0
  const enMora = puedeVerCupo && credito?.estadoMora && credito.estadoMora !== 'NORMAL'

  // Cuarta tarjeta de KPI (bolsa o cupo) solo se muestra cuando hay algo real que mostrar ahí —
  // para un ANALISTA_CLIENTE de un cliente pospago no hay nada, así que la grilla se acomoda a 3.
  const mostrarCuartoKpi = !esPospago || puedeVerCupo

  return (
    <div className="space-y-5">
      {enMora && (
        <div className="flex items-center gap-3 bg-red-50 border border-red-200 text-red-700 rounded-xl px-4 py-3 text-sm opacity-0 animate-fade-in [animation-fill-mode:forwards]">
          <span className="flex-shrink-0">{ICONO_ALERTA}</span>
          <p>
            Tu cuenta está <strong>{credito.estadoMora === 'SUSPENDIDO' ? 'suspendida' : 'en mora'}</strong> — ponte al día para poder seguir solicitando servicios.
          </p>
          <Link to="/cliente/pospago" className="ml-auto text-xs font-semibold whitespace-nowrap hover:underline">
            Ver mi crédito →
          </Link>
        </div>
      )}

      {errorSecundario && (
        <div className="bg-amber-50 border border-amber-200 text-amber-700 text-sm px-4 py-3 rounded-lg">
          {errorSecundario}
        </div>
      )}

      {/* Encabezado */}
      <div className="flex items-center justify-between flex-wrap gap-4 bg-white rounded-2xl border border-gray-100 shadow-sm px-6 py-5">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-primary-600 text-white flex items-center justify-center text-lg font-semibold flex-shrink-0">
            {iniciales(usuario?.nombre)}
          </div>
          <div>
            <h2 className="text-xl font-bold text-gray-900">Hola, {usuario?.nombre ?? 'bienvenido'}</h2>
            <p className="text-sm text-gray-500 capitalize mt-0.5">
              {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          </div>
        </div>
        <Link to="/cliente/nueva-solicitud"
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors shadow-sm">
          {ICONO_MAS}
          Nueva Solicitud
        </Link>
      </div>

      {/* KPIs */}
      <div className={`grid grid-cols-1 sm:grid-cols-2 gap-4 ${mostrarCuartoKpi ? 'lg:grid-cols-4' : 'lg:grid-cols-3'}`}>
        <KpiCard
          titulo="Servicios activos"
          valor={datos?.serviciosActivos ?? 0}
          subtitulo="En proceso ahora mismo"
          color="bg-blue-50 text-blue-600"
          icono={ICONO_ACTIVOS}
          retraso={0}
        />
        <KpiCard
          titulo="Pendientes"
          valor={datos?.serviciosPendientes ?? 0}
          subtitulo="Por programar"
          color="bg-amber-50 text-amber-600"
          icono={ICONO_PENDIENTES}
          retraso={40}
        />
        <KpiCard
          titulo="Finalizados este mes"
          valor={datos?.serviciosFinalizadosMes ?? 0}
          subtitulo="Completados en el mes actual"
          color="bg-green-50 text-green-600"
          icono={ICONO_FINALIZADOS}
          retraso={80}
        />
        {mostrarCuartoKpi && (
          puedeVerCupo ? (
            <KpiCard
              titulo="Cupo de crédito"
              valor={formatearCOP(creditoDisponible)}
              subtitulo={limiteCredito > 0 ? `De ${formatearCOP(limiteCredito)} totales` : 'Disponible'}
              color="bg-purple-50 text-purple-600"
              icono={ICONO_CREDITO}
              retraso={120}
            />
          ) : (
            <KpiCard
              titulo="Disponibles en bolsa"
              valor={disponiblesBolsa}
              subtitulo={totalBolsa > 0 ? `De ${totalBolsa} comprados` : 'Bolsa prepago'}
              color="bg-purple-50 text-purple-600"
              icono={ICONO_BOLSA}
              retraso={120}
            />
          )
        )}
      </div>

      {/* Indicador bolsa / cupo de crédito — solo si hay algo que este usuario pueda ver ahí */}
      {mostrarCuartoKpi && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm px-5 py-4">
          {puedeVerCupo ? (
            <div className="flex items-center gap-3 flex-wrap">
              <div className="w-10 h-10 rounded-lg bg-primary-50 text-primary-600 flex items-center justify-center flex-shrink-0">
                {ICONO_CREDITO}
              </div>
              <div className="min-w-0">
                <p className="text-sm text-gray-700">
                  Tienes <strong className="text-primary-700">{formatearCOP(creditoDisponible)}</strong> de cupo disponible
                  {limiteCredito > 0 ? ` de ${formatearCOP(limiteCredito)} totales` : ''}
                </p>
              </div>
              <Link to="/cliente/pospago"
                className="ml-auto text-xs font-semibold text-primary-600 hover:text-primary-800 border border-primary-200 px-3 py-1.5 rounded-lg hover:bg-primary-50 transition-colors whitespace-nowrap">
                Ver mi crédito →
              </Link>
            </div>
          ) : (
            <div className="flex items-center gap-3 flex-wrap">
              <div className="w-10 h-10 rounded-lg bg-primary-50 text-primary-600 flex items-center justify-center flex-shrink-0">
                {ICONO_CARRITO}
              </div>
              <div className="min-w-0">
                <p className="text-sm text-gray-700">
                  Tienes <strong className="text-primary-700">{disponiblesBolsa}</strong> servicio{disponiblesBolsa !== 1 ? 's' : ''} disponible{disponiblesBolsa !== 1 ? 's' : ''}
                  {totalBolsa > 0 ? ` de ${totalBolsa} comprados` : ' en tu bolsa prepago'}
                </p>
              </div>
              <Link to="/cliente/comprar-servicios"
                className="ml-auto text-xs font-semibold text-primary-600 hover:text-primary-800 border border-primary-200 px-3 py-1.5 rounded-lg hover:bg-primary-50 transition-colors whitespace-nowrap">
                Comprar más →
              </Link>
            </div>
          )}
          {puedeVerCupo ? (
            limiteCredito > 0 && (
              <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden mt-3">
                <div className="h-full bg-primary-500 rounded-full transition-all duration-700 ease-out" style={{ width: `${pctCreditoUsado}%` }} />
              </div>
            )
          ) : totalBolsa > 0 && (
            <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden mt-3">
              <div className="h-full bg-primary-500 rounded-full transition-all duration-700 ease-out" style={{ width: `${pctBolsa}%` }} />
            </div>
          )}
        </div>
      )}

      {/* Actividad reciente — lista tipo feed, sin tabla ni scroll horizontal */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-base font-semibold text-gray-800">Actividad reciente</h3>
          <Link to="/cliente/solicitudes" className="text-xs font-medium text-primary-600 hover:text-primary-700">
            Ver todas →
          </Link>
        </div>

        {!datos?.ultimasSolicitudes?.length ? (
          <div className="flex flex-col items-center justify-center py-14 gap-3 text-gray-300">
            {ICONO_VACIO}
            <p className="text-gray-400 text-sm">Sin solicitudes registradas todavía.</p>
            <Link to="/cliente/nueva-solicitud" className="text-xs text-primary-600 hover:underline font-medium">
              Crear la primera →
            </Link>
          </div>
        ) : (
          <div className="divide-y divide-gray-50">
            {datos.ultimasSolicitudes.map((s, idx) => (
              <FilaSolicitud key={s.idServicio} s={s} idx={idx} onClick={() => navigate(`/cliente/solicitudes/${s.idServicio}`)} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
