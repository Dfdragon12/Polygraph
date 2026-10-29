import { useEffect, useState, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import dashboardService from '../../services/dashboardService'
import pospagoService from '../../services/pospagoService'
import solicitudService from '../../services/solicitudService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'
import { useCambios } from '../../hooks/useCambios'
import { EncabezadoPagina, EsqueletoPagina, botonPrimario, botonSecundario } from '../../components/admin/PaginaAdmin'
import EstadoTiempo, { Reprogramada } from '../../components/EstadoTiempo'
import { TarjetaEnCurso, TarjetaCumplimiento, AvisoVencidas } from '../../components/TarjetasTiempo'
import tiempoService from '../../services/tiempoService'
import { Atencion, Grafica, Enlace, Icono, BarraEstados, Dona, C, saludo, fechaLarga } from '../../components/tablero/Tablero'

// Tablero del cliente (ADMIN_CLIENTE y ANALISTA_CLIENTE): cómo van sus solicitudes (indicadores y
// barra por estado), lo último que pidió con su fecha de entrega, su bolsa prepago o cupo de
// crédito (solo lo que su rol puede ver) y el contacto de su gestor. Se actualiza solo (tiempo real).

const DIAS_CUMPLIMIENTO = 90

const formatearCOP = (valor) =>
  new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor ?? 0)

const fechaCorta = (f) => f ? new Date(`${String(f).slice(0, 10)}T12:00:00`).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }).replace('.', '') : '—'

const ESTADOS = [
  { clave: 'pendientes',    estado: 'PENDIENTE',    label: 'Pendiente',    color: C.ambar },
  { clave: 'programando',   estado: 'PROGRAMANDO',  label: 'Programando',  color: C.cielo },
  { clave: 'enEjecucion',   estado: 'EN_EJECUCION', label: 'En ejecución', color: C.indigo },
  { clave: 'reprogramados', estado: 'REPROGRAMADO', label: 'Reprogramado', color: C.naranja },
  { clave: 'finalizados',   estado: 'FINALIZADO',   label: 'Finalizado',   color: C.verde },
  { clave: 'publicados',    estado: 'PUBLICADO',    label: 'Entregado',    color: C.teal },
  { clave: 'cancelados',    estado: 'CANCELADO',    label: 'Cancelado',    color: C.gris },
]

const svg = (d, c = 'h-5 w-5') => (
  <svg className={c} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
    {[].concat(d).map((p, i) => <path key={i} strokeLinecap="round" strokeLinejoin="round" d={p} />)}
  </svg>
)
const I = {
  rayo: (c) => svg('M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z', c),
  reloj: (c) => svg('M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z', c),
  check: (c) => svg('M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z', c),
  carpeta: (c) => svg('M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z', c),
  mas: (c) => svg('M12 4.5v15m7.5-7.5h-15', c),
  subir: (c) => svg('M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5', c),
  alerta: (c) => svg('M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z', c),
  bolsa: (c) => svg('M15.75 10.5V6a3.75 3.75 0 10-7.5 0v4.5m11.356-1.993l1.263 12c.07.665-.45 1.243-1.119 1.243H4.25a1.125 1.125 0 01-1.12-1.243l1.264-12A1.125 1.125 0 015.513 7.5h12.974c.576 0 1.059.435 1.119 1.007z', c),
  correo: (c) => svg('M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75', c),
  telefono: (c) => svg('M2.25 6.75c0 8.284 6.716 15 15 15h2.25a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106c-.44-.11-.902.055-1.173.417l-.97 1.293c-.282.376-.769.542-1.21.38a12.035 12.035 0 01-7.143-7.143c-.162-.441.004-.928.38-1.21l1.293-.97c.363-.271.527-.734.417-1.173L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z', c),
  vacio: (c) => svg('M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z', c),
}

/** Estado de tiempo (lo calcula el servidor en días hábiles) y la fecha de compromiso; si se reprogramó, dice cuál era antes. */
function Entrega({ s }) {
  if (!s.tiempo) return <span className="block text-right text-xs text-gray-500">{s.fechaEntregaEstimada ? fechaCorta(s.fechaEntregaEstimada) : '—'}</span>
  return (
    <div className="text-right">
      <EstadoTiempo tiempo={s.tiempo} className="!text-[11px]" />
      <p className="mt-0.5 text-[11px] tabular-nums text-gray-600">Compromiso {fechaCorta(s.fechaEntregaEstimada)}</p>
      <Reprogramada tiempo={s.tiempo} className="block" />
    </div>
  )
}

export default function Dashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
  const [datos, setDatos] = useState(null)
  const [conteo, setConteo] = useState(null)
  const [resumen, setResumen] = useState(null)
  const [cumplimiento, setCumplimiento] = useState(null)
  const [bolsa, setBolsa] = useState(null)
  const [credito, setCredito] = useState(null)
  const [gestor, setGestor] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [errorSecundario, setErrorSecundario] = useState(null)

  // El tipo de cliente se toma del dashboard recién cargado (en vivo desde BD), nunca del usuario
  // cacheado al login: si un admin lo cambia mientras el JWT sigue vigente, el caché queda viejo.
  const esPospago = datos?.tipoCliente === 'POSPAGO'
  // El cupo pospago solo lo consulta ADMIN_CLIENTE (FacturaPospagoController); al analista del
  // cliente no se le muestra esa sección.
  const esAdminCliente = usuario?.rol === 'ADMIN_CLIENTE'
  const puedeVerCupo = esPospago && esAdminCliente
  const muestraSaldo = !esPospago || puedeVerCupo

  // Carga el tablero; también se llama sola cuando cambia una solicitud del cliente (tiempo real).
  const cargarDatos = useCallback(() => {
    solicitudService.conteo().then(setConteo).catch(() => setConteo(null))
    // El resumen ÚNICO de tiempos (el mismo que usan los demás tableros): el cliente solo recibe sus propias solicitudes.
    tiempoService.resumen({ top: 1 }).then(setResumen).catch(() => setResumen(null))
    const desde = new Date(); desde.setDate(desde.getDate() - (DIAS_CUMPLIMIENTO - 1))
    tiempoService.resumen({ desde: desde.toISOString().slice(0, 10), top: 1 }).then(r => setCumplimiento(r.cumplimiento)).catch(() => setCumplimiento(null))
    return dashboardService.obtenerDashboard()
      .then((dashData) => {
        setDatos(dashData)
        const esPospagoLocal = dashData.tipoCliente === 'POSPAGO'
        if (esPospagoLocal && usuario?.rol !== 'ADMIN_CLIENTE') return
        const segundo = esPospagoLocal ? pospagoService.obtenerEstadoCuenta() : dashboardService.obtenerBolsaServicios()
        return segundo
          .then((dato) => {
            if (esPospagoLocal) setCredito(dato)
            else setBolsa(Array.isArray(dato) ? dato : [])
            setErrorSecundario(null)
          })
          .catch((err) => setErrorSecundario(err.response?.data?.mensaje ?? 'No se pudo cargar tu información de cupo/bolsa.'))
      })
      .catch(() => setError('No se pudo cargar el tablero. Intenta nuevamente.'))
      .finally(() => setCargando(false))
  }, [usuario?.rol])

  useEffect(() => { cargarDatos() }, [cargarDatos])
  useEffect(() => { dashboardService.obtenerGestor().then(setGestor).catch(() => setGestor(null)) }, [])
  useCambios(() => cargarDatos(), { esperaMs: 1200 })

  const irA = (ruta) => navigate(ruta)

  /* ── Series ── */
  const estados = ESTADOS.map(e => ({ name: e.label, value: conteo?.[e.clave] ?? 0, color: e.color, clave: e.estado }))
  const enCurso = (conteo?.pendientes ?? 0) + (conteo?.programando ?? 0) + (conteo?.enEjecucion ?? 0) + (conteo?.reprogramados ?? 0)

  const totalComprado = (bolsa ?? []).reduce((a, b) => a + (b.cantidadComprada ?? 0), 0)
  const disponibles = datos?.serviciosDisponibles ?? 0
  const bolsaDona = [
    { name: 'Disponibles', value: disponibles, color: C.verde },
    { name: 'Usados', value: Math.max(0, totalComprado - disponibles), color: C.gris },
  ]

  const limite = credito?.limiteCredito ?? 0
  const creditoDisponible = credito?.creditoDisponible ?? 0
  const pctUsado = limite > 0 ? Math.round(((limite - creditoDisponible) / limite) * 100) : 0
  const enMora = puedeVerCupo && credito?.estadoMora && credito.estadoMora !== 'NORMAL'

  return (
    <div className="mx-auto max-w-7xl">
      <EncabezadoPagina
        seccion="Panel del cliente"
        titulo={`${saludo()}, ${usuario?.nombre?.trim() || 'bienvenido'}`}
        descripcion={<span className="inline-block first-letter:uppercase">{fechaLarga()}</span>}
        icono={Icono.panel('h-6 w-6')}
        acciones={<>
          {esAdminCliente && <Link to="/cliente/carga-masiva" className={botonSecundario}>{I.subir('h-4 w-4')} Carga masiva</Link>}
          <Link to="/cliente/nueva-solicitud" className={botonPrimario}>{I.mas('h-4 w-4')} Nueva solicitud</Link>
        </>}
      />

      {cargando ? <EsqueletoPagina indicadores={4} bloques={2} /> : error ? (
        <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">{error}</div>
      ) : (
        <div className="space-y-5">
          {enMora && (
            <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              <span className="shrink-0">{I.alerta('h-5 w-5')}</span>
              <p>Tu cuenta está <strong>{credito.estadoMora === 'SUSPENDIDO' ? 'suspendida' : 'en mora'}</strong> — ponte al día para poder seguir solicitando servicios.</p>
              <Link to="/cliente/pospago" className="ml-auto whitespace-nowrap text-xs font-semibold hover:underline">Ver mi crédito →</Link>
            </div>
          )}
          {errorSecundario && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">{errorSecundario}</div>
          )}

          <AvisoVencidas cantidad={resumen?.porEstadoTiempo?.VENCIDA ?? 0} onVer={() => irA('/cliente/solicitudes?filtro=vencidas')} />

          {/* ── Indicadores ── */}
          <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
            <TarjetaEnCurso resumen={resumen} className="lg:col-span-2"
              onVer={(estado) => irA(`/cliente/solicitudes?tiempo=${estado}`)} />
            <TarjetaCumplimiento cumplimiento={cumplimiento} dias={DIAS_CUMPLIMIENTO} />
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Atencion titulo="Por programar" valor={datos?.serviciosPendientes ?? 0} detalle="Esperan fecha" vacio="Nada por programar"
              icono={I.reloj('h-4 w-4')} tono="ambar" onClick={() => irA('/cliente/solicitudes')} />
            <Atencion titulo="Entregados este mes" valor={datos?.serviciosFinalizadosMes ?? 0} detalle="Resultados listos" vacio="Aún ninguno este mes"
              icono={I.check('h-4 w-4')} tono="primario" onClick={() => irA('/cliente/solicitudes')} />
            <Atencion titulo="Solicitudes en total" valor={conteo?.total ?? 0} detalle="Desde que empezaste" vacio="Aún no hay solicitudes"
              icono={I.carpeta('h-4 w-4')} tono="primario" onClick={() => irA('/cliente/solicitudes')} />
          </div>

          {/* ── Estado de sus solicitudes ── */}
          <BarraEstados titulo="Estado de mis solicitudes" descripcion="Todas tus solicitudes por etapa · clic para verlas"
            estados={estados} destacado={{ valor: enCurso, etiqueta: 'en curso' }}
            onClick={() => irA('/cliente/solicitudes')} />

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
            {/* ── Actividad reciente ── */}
            <Grafica className="xl:col-span-2" titulo="Actividad reciente" descripcion="Tus últimas solicitudes y su tiempo de entrega"
              accion={<Enlace onClick={() => irA('/cliente/solicitudes')}>Ver todas</Enlace>}>
              {!datos?.ultimasSolicitudes?.length ? (
                <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
                  <span className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-50 text-gray-300">{I.vacio('h-6 w-6')}</span>
                  <p className="text-sm text-gray-500">Aún no has hecho solicitudes.</p>
                  <Link to="/cliente/nueva-solicitud" className={botonPrimario}>{I.mas('h-4 w-4')} Crear la primera</Link>
                </div>
              ) : (
                <div className="-mx-5 -mb-5 border-t border-gray-100">
                  <div className="hidden grid-cols-[1fr_8rem_12rem] gap-4 bg-gray-50/60 px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-gray-600 sm:grid">
                    <span>Candidato</span><span>Estado</span><span className="text-right">Tiempo</span>
                  </div>
                  <ul className="divide-y divide-gray-50">
                    {datos.ultimasSolicitudes.map(s => (
                      <li key={s.idServicio}>
                        <button type="button" onClick={() => irA(`/cliente/solicitudes/${s.idServicio}`)}
                          className="grid w-full grid-cols-[1fr_11rem] items-center gap-4 px-5 py-3 text-left transition-colors hover:bg-gray-50 sm:grid-cols-[1fr_8rem_12rem]">
                          <span className="flex min-w-0 items-center gap-3">
                            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-50 text-xs font-semibold text-primary-700">
                              {`${(s.nombresEvaluado ?? '').trim()[0] ?? ''}${(s.apellidosEvaluado ?? '').trim()[0] ?? ''}`.toUpperCase() || '—'}
                            </span>
                            <span className="min-w-0">
                              <span className="block truncate text-sm font-medium text-gray-900">{`${s.nombresEvaluado ?? ''} ${s.apellidosEvaluado ?? ''}`.trim()}</span>
                              <span className="block truncate text-xs text-gray-500">#{s.idServicio} · {s.proceso || 'Servicio'}{s.cargo ? ` · ${s.cargo}` : ''}</span>
                            </span>
                          </span>
                          <span className="hidden sm:block"><ServiceStatusBadge status={s.estado} /></span>
                          <Entrega s={s} />
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </Grafica>

            <div className="space-y-5">
              {/* ── Bolsa prepago o cupo pospago (solo lo que su rol puede ver) ── */}
              {muestraSaldo && (puedeVerCupo ? (
                <Grafica titulo="Cupo de crédito" descripcion="Lo que puedes seguir solicitando a crédito"
                  accion={<Enlace onClick={() => irA('/cliente/pospago')}>Mi crédito</Enlace>}>
                  <p className="text-2xl font-bold tabular-nums tracking-tight text-gray-900">{formatearCOP(creditoDisponible)}</p>
                  <p className="text-xs text-gray-500">disponible{limite > 0 ? ` de ${formatearCOP(limite)}` : ''}</p>
                  {limite > 0 && (
                    <div className="mt-4">
                      <div className="h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
                        <div className={`h-full rounded-full transition-all duration-700 ${pctUsado >= 90 ? 'bg-red-500' : pctUsado >= 70 ? 'bg-amber-400' : 'bg-primary-500'}`} style={{ width: `${pctUsado}%` }} />
                      </div>
                      <p className="mt-1.5 flex justify-between text-[11px] text-gray-400"><span>{pctUsado}% en uso</span><span>{100 - pctUsado}% libre</span></p>
                    </div>
                  )}
                </Grafica>
              ) : (
                <Grafica titulo="Mi bolsa de servicios" descripcion="Servicios prepagados disponibles"
                  accion={esAdminCliente && <Enlace onClick={() => irA('/cliente/comprar-servicios')}>Comprar</Enlace>}>
                  {totalComprado === 0 && disponibles === 0 ? (
                    <div className="py-6 text-center">
                      <span className="mx-auto mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-primary-50 text-primary-600">{I.bolsa('h-5 w-5')}</span>
                      <p className="text-sm text-gray-500">Aún no tienes servicios comprados.</p>
                      {esAdminCliente && <Link to="/cliente/comprar-servicios" className="mt-2 inline-block text-xs font-semibold text-primary-600 hover:underline">Comprar servicios →</Link>}
                    </div>
                  ) : (
                    <>
                      <Dona datos={bolsaDona} total={totalComprado || disponibles} etiquetaTotal="comprados" alto={140} />
                      {(bolsa ?? []).length > 0 && (
                        <ul className="mt-4 space-y-2.5 border-t border-gray-100 pt-4">
                          {bolsa.map(b => {
                            const pct = b.cantidadComprada > 0 ? Math.round((b.cantidadDisponible / b.cantidadComprada) * 100) : 0
                            return (
                              <li key={b.idProceso}>
                                <div className="flex items-center justify-between gap-2 text-xs">
                                  <span className="truncate text-gray-700">{b.nombreProceso}</span>
                                  <span className="shrink-0 tabular-nums text-gray-500"><span className="font-semibold text-gray-900">{b.cantidadDisponible}</span> / {b.cantidadComprada}</span>
                                </div>
                                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-gray-100">
                                  <div className={`h-full rounded-full ${pct <= 20 ? 'bg-amber-400' : 'bg-emerald-500'}`} style={{ width: `${pct}%` }} />
                                </div>
                              </li>
                            )
                          })}
                        </ul>
                      )}
                    </>
                  )}
                </Grafica>
              ))}

              {/* ── Su gestor ── */}
              {gestor && (
                <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400">Tu gestor</p>
                  <div className="mt-3 flex items-center gap-3">
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-600 text-sm font-bold text-white">
                      {(gestor.nombreCompleto ?? '').split(' ').filter(Boolean).slice(0, 2).map(p => p[0]).join('').toUpperCase() || 'G'}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-gray-900">{gestor.nombreCompleto}</p>
                      <p className="text-xs text-gray-500">Te acompaña en cada estudio</p>
                    </div>
                  </div>
                  <div className="mt-4 space-y-2">
                    {gestor.email && (
                      <a href={`mailto:${gestor.email}`} className="flex items-center gap-2.5 rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-700 transition-colors hover:bg-gray-50">
                        <span className="text-gray-400">{I.correo('h-4 w-4')}</span><span className="truncate">{gestor.email}</span>
                      </a>
                    )}
                    {gestor.telefono && (
                      <a href={`tel:${gestor.telefono}`} className="flex items-center gap-2.5 rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-700 transition-colors hover:bg-gray-50">
                        <span className="text-gray-400">{I.telefono('h-4 w-4')}</span><span>{gestor.telefono}</span>
                      </a>
                    )}
                  </div>
                </section>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
