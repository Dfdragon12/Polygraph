import { useEffect, useState, useCallback } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import dashboardService from '../../services/dashboardService'
import pospagoService from '../../services/pospagoService'
import EtiquetaEstado from '../../components/semaforo/EtiquetaEstado'
import { useCambios } from '../../hooks/useCambios'
import { EncabezadoPagina, EsqueletoPagina, botonPrimario, botonSecundario } from '../../components/admin/PaginaAdmin'
import { textoCompromiso } from '../../components/CompromisoFecha'
import solicitudService from '../../services/solicitudService'
import { guardarBlob } from '../../services/trabajoService'
import AnillosRiesgo from '../../components/graficos/AnillosRiesgo'
import SemaforoEstados from '../../components/semaforo/SemaforoEstados'
import { useConfigTienda } from '../../hooks/useConfigTienda'
import { Grafica, Enlace, Icono, Dona, C, saludo, fechaLarga } from '../../components/tablero/Tablero'
import { fechaCorta, fechaDiaMes, fechaConDia, horaCorta, mesAnio, fechaLarga as fechaConAnio } from '../../utils/fechas'

// Tablero del cliente (ADMIN_CLIENTE y ANALISTA_CLIENTE): cómo van sus solicitudes (indicadores y
// barra por estado), lo último que pidió con su fecha de entrega, su bolsa prepago o cupo de
// crédito (solo lo que su rol puede ver) y el contacto de su gestor. Se actualiza solo (tiempo real).

const nf = new Intl.NumberFormat('es-CO')
// « · resultado emitido el 24 nov · a tiempo»: sin PDF o sin fecha de emisión no se escribe nada.
const fraseEmision = (i) => {
  const fecha = fechaDiaMes(i.emitidoEn, '')
  if (!i.pdfDisponible || !fecha) return ''
  return ` · resultado emitido el ${fecha}${i.cumplimiento === 'A_TIEMPO' ? ' · a tiempo' : i.cumplimiento === 'TARDE' ? ' · con retraso' : ''}`
}
const PERIODOS = [['HOY', 'Hoy'], ['SEMANA', 'Última semana'], ['MES', 'Este mes'], ['ANIO', 'Último año'], ['TOTAL', 'Total']]
const foco = 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600'
const NIVEL_CHIP = { BAJO: 'bg-green-50 text-green-800 ring-green-700/30', MEDIO: 'bg-amber-50 text-amber-900 ring-amber-700/30', ALTO: 'bg-red-50 text-red-800 ring-red-700/30' }
const CLAVE_VISITA = 'polygraph.inicio.ultimaVisita'

// Fecha/hora local sin zona (así la entiende el servidor, que guarda las fechas en hora de Colombia)
const isoLocal = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
const mismoDia = (f) => f && new Date(f).toDateString() === new Date().toDateString()
const diaHora = (f) => f ? `${fechaConDia(f)}, ${horaCorta(f)}` : ''

const formatearCOP = (valor) =>
  new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor ?? 0)

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

/** PC-01: lo que el cliente lee mientras el PDF del estudio no está listo (el mismo texto en el Dashboard y en el detalle). */
const textoPreparandoPdf = (pdfEstado) => pdfEstado === 'ERROR'
  ? 'Estamos resolviendo un problema con el PDF de tu estudio; tu gestor ya lo está revisando.'
  : pdfEstado === 'GENERANDO'
    ? 'Preparando el PDF de tu estudio… puede tardar unos minutos. Te avisaremos cuando esté listo.'
    : 'Tu gestor está preparando el cierre del estudio. Te avisaremos cuando el PDF esté listo.'

/** «Informes listos»: lo publicado, parcial o completo. Con la pasta emitida: nivel del estudio y descarga del PDF único; si no, sin nivel. */
function InformesListos({ inicio, puedeDescargar, onIr }) {
  const [descargando, setDescargando] = useState(null)
  const [fallo, setFallo] = useState(null)
  const descargar = async (i) => {
    setDescargando(i.idServicio); setFallo(null)
    try { guardarBlob(await solicitudService.descargarEstudioCompleto(i.idServicio), `Estudio_${i.idServicio}.pdf`) }
    catch { setFallo(i.idServicio) }
    finally { setDescargando(null) }
  }
  return (
    <section aria-labelledby="informes-listos" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm xl:col-span-2">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="informes-listos" className="text-lg font-bold text-gray-900">Informes listos</h2>
        {inicio?.informesNuevos > 0 && <p className="text-sm font-semibold text-green-800">{nf.format(inicio.informesNuevos)} {inicio.informesNuevos === 1 ? 'nuevo' : 'nuevos'} desde tu última visita</p>}
      </div>
      {!inicio ? <div className="h-40 animate-pulse rounded-xl bg-gray-100" /> : inicio.informes.length === 0 ? (
        <p className="rounded-xl border border-dashed border-gray-300 bg-gray-50 px-4 py-8 text-center text-sm text-gray-700">Todavía no hay informes publicados. Cuando tu gestor publique uno, lo verás aquí para descargarlo.</p>
      ) : (
        <ul className="divide-y divide-gray-100">
          {inicio.informes.map(i => (
            <li key={i.idServicio} className="flex flex-wrap items-start gap-3 py-3">
              <span className="mt-1.5 w-2 shrink-0">{i.nuevo && <><span aria-hidden="true" className="block h-2 w-2 rounded-full bg-blue-600" /><span className="sr-only">Nuevo</span></>}</span>
              <div className="min-w-0 flex-1">
                <p className="font-bold text-gray-900">{i.evaluado}</p>
                <p className="text-sm text-gray-700">
                  {i.proceso} · #{i.idServicio}{i.cargo ? ` · ${i.cargo}` : ''}
                  {i.completo ? fraseEmision(i)
                    : i.ultimoNombre ? ` · ${mismoDia(i.ultimoFecha) ? 'hoy' : fechaDiaMes(i.ultimoFecha)} se publicó ${i.ultimoNombre}` : ''}
                </p>
                <p className="mt-0.5 flex items-center gap-1.5 text-sm font-semibold text-gray-900"><span aria-hidden="true" className="h-[9px] w-[9px] rounded-full bg-[#0D9488]" />
                  {i.completo ? 'Publicado · estudio completo'
                    : i.vigentes > 0 && i.publicados >= i.vigentes ? `${i.publicados} de ${i.vigentes} informes · Tu gestor está preparando el cierre`
                    : `Publicado parcial · ${i.publicados} de ${i.vigentes} informes`}</p>
                {i.completo && !i.pdfDisponible && <p id={`pdf-${i.idServicio}`} className="mt-1 text-sm text-gray-700">{textoPreparandoPdf(i.pdfEstado)}</p>}
                {fallo === i.idServicio && <p role="alert" className="mt-1 text-xs text-red-700">No se pudo descargar el estudio. <button type="button" onClick={() => descargar(i)} className={`font-semibold underline ${foco}`}>Reintentar</button> o contacta a tu gestor.</p>}
              </div>
              <div className="flex shrink-0 flex-wrap items-center gap-2">
                {i.completo && i.nivel && <span className={`rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${NIVEL_CHIP[i.nivel]}`}>{i.nivel}</span>}
                {i.completo && puedeDescargar && i.pdfDisponible ? (
                  <button type="button" disabled={descargando === i.idServicio} onClick={() => descargar(i)}
                    className={`min-h-[44px] rounded-lg bg-primary-600 px-4 text-sm font-semibold text-white hover:bg-primary-700 disabled:opacity-60 ${foco}`}>
                    {descargando === i.idServicio ? 'Descargando…' : 'Descargar estudio completo'}</button>
                ) : i.completo && puedeDescargar ? (
                  <>
                    <button type="button" disabled aria-describedby={`pdf-${i.idServicio}`}
                      className="min-h-[44px] cursor-not-allowed rounded-lg bg-gray-200 px-4 text-sm font-semibold text-gray-700">Descargar estudio completo</button>
                    <button type="button" onClick={() => onIr(`/cliente/solicitudes/${i.idServicio}`)}
                      className={`min-h-[44px] rounded-lg border border-primary-600 bg-white px-4 text-sm font-semibold text-primary-700 hover:bg-primary-50 ${foco}`}>Ver estudio</button>
                  </>
                ) : (
                  <button type="button" onClick={() => onIr(`/cliente/solicitudes/${i.idServicio}`)}
                    className={`min-h-[44px] rounded-lg border border-primary-600 bg-white px-4 text-sm font-semibold text-primary-700 hover:bg-primary-50 ${foco}`}>
                    {i.completo ? 'Ver estudio' : 'Ver y descargar informes'}</button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <button type="button" onClick={() => onIr('/cliente/solicitudes?estado=PUBLICADO')} className={`mt-2 inline-flex min-h-[44px] items-center rounded text-sm font-semibold text-primary-700 hover:underline ${foco}`}>Ver todos los informes publicados →</button>
    </section>
  )
}

function RequiereAtencion({ inicio }) {
  const vacio = inicio && inicio.candidatos.length === 0 && inicio.citas.length === 0
  return (
    <section aria-labelledby="requiere-atencion" className="rounded-2xl border border-amber-200 bg-amber-50 p-5 shadow-sm">
      <h2 id="requiere-atencion" className="mb-3 text-lg font-bold text-gray-900">Requiere tu atención</h2>
      {!inicio ? <div className="h-32 animate-pulse rounded-xl bg-amber-100" /> : vacio ? (
        <p className="rounded-xl bg-white px-4 py-8 text-center text-sm text-gray-800">No tienes nada pendiente: tus candidatos están al día.</p>
      ) : (
        <div className="space-y-3">
          {inicio.candidatos.map(c => (
            <article key={`c${c.idServicio}`} className="rounded-xl bg-white p-4">
              <p className="text-[11px] font-bold uppercase tracking-wider text-amber-900">Candidato sin terminar su información</p>
              <p className="font-bold text-gray-900">{c.evaluado}</p>
              <p className="flex flex-wrap items-center gap-1.5 text-sm text-gray-700">{c.proceso} · #{c.idServicio} ·
                {c.resumen[0] && <EtiquetaEstado estado={c.resumen[0].codigo} pequena />}
                · compromiso: {textoCompromiso(c.tiempo, fechaCorta(c.fechaEntrega))}</p>
              {/* PC-03: un solo texto por estado del enlace, armado por el servidor; aquí no se juntan frases sueltas. */}
              <p className="mt-2 text-sm text-gray-900">{c.seguimiento.texto}</p>
              <p className="mt-1 text-xs text-gray-700">Si hablas con él, recuérdale revisar su WhatsApp o correo.</p>
            </article>
          ))}
          {inicio.citas.map(c => (
            <article key={`k${c.idServicio}-${c.fecha}`} className="rounded-xl bg-white p-4">
              <p className="text-[11px] font-bold uppercase tracking-wider text-sky-800">Cita programada · avísale al candidato</p>
              <p className="font-bold text-gray-900">{c.evaluado}</p>
              <p className="flex flex-wrap items-center gap-1.5 text-sm text-gray-700">{c.subproceso} · #{c.idServicio} · <EtiquetaEstado estado={c.estado} pequena /> · {diaHora(c.fecha)}{c.lugar ? ` · ${c.lugar}` : ''}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

const TIEMPOS_CLIENTE = [
  ['aTiempo', 'A tiempo', 'A_TIEMPO', 'border-green-200 text-green-900 bg-white', null],
  ['venceHoyOManana', 'Vence hoy o mañana', 'VENCE_HOY,VENCE_MANANA', 'border-amber-200 text-amber-900 bg-amber-50', null],
  ['atrasadas', 'Atrasadas', 'VENCIDAS', 'border-red-200 text-red-800 bg-red-50', 'Tu gestor ya las está gestionando'],
  ['enPausa', 'En pausa', 'EN_PAUSA', 'border-slate-200 text-slate-800 bg-white', 'Esperan al candidato · compromiso por definir'],
]

function TiemposEntrega({ inicio, onIr }) {
  return (
    <section aria-labelledby="tiempos-entrega" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="tiempos-entrega" className="text-lg font-bold text-gray-900">Tiempos de entrega</h2>
        <button type="button" onClick={() => onIr('/cliente/solicitudes')} className={`min-h-[44px] rounded text-sm font-semibold text-primary-700 hover:underline ${foco}`}>Ver todas en Mis solicitudes →</button>
      </div>
      {!inicio ? <div className="h-24 animate-pulse rounded-xl bg-gray-100" /> : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {TIEMPOS_CLIENTE.map(([k, nombre, grupo, clase, nota]) => (
            <button key={k} type="button" onClick={() => onIr(`/cliente/solicitudes?grupo=${grupo}`)} aria-label={`${nombre}: ${inicio.tiempos[k]}. Abre Mis solicitudes filtrada.`}
              className={`min-h-[44px] rounded-xl border p-4 text-left transition-shadow hover:shadow-md ${foco} ${clase}`}>
              <span className="block text-sm font-semibold">{nombre}</span>
              <span className="block text-3xl font-bold tabular-nums text-gray-900">{nf.format(inicio.tiempos[k])}</span>
              {nota && <span className="block text-xs text-gray-700">{nota}</span>}
            </button>
          ))}
        </div>
      )}
    </section>
  )
}

function rangoTexto(e) {
  if (!e) return ''
  if (e.periodo === 'TOTAL') return 'Desde el inicio'
  if (e.periodo === 'HOY') return fechaConAnio(e.hasta)
  if (e.periodo === 'SEMANA') return `${fechaDiaMes(e.desde)} a ${fechaDiaMes(e.hasta)}`
  return `${mesAnio(e.desde)} a ${mesAnio(e.hasta)}`
}

function Estadisticas({ e, error, periodo, onPeriodo, onReintentar, onNivel }) {
  return (
    <section aria-labelledby="estadisticas" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="estadisticas" className="text-lg font-bold text-gray-900">Estadísticas de mi empresa</h2>
          <p className="text-sm text-gray-700">{rangoTexto(e)}{e ? ' · ' : ''}el riesgo se cuenta sobre estudios con resultado emitido</p>
        </div>
        <div role="group" aria-label="Periodo de las estadísticas" className="flex flex-wrap gap-1 rounded-xl bg-gray-100 p-1">
          {PERIODOS.map(([k, nombre]) => (
            <button key={k} type="button" aria-pressed={periodo === k} onClick={() => onPeriodo(k)}
              className={`min-h-[44px] rounded-lg px-3 text-sm font-semibold ${foco} ${periodo === k ? 'bg-white text-gray-900 shadow' : 'text-gray-700 hover:text-gray-900'}`}>{nombre}</button>
          ))}
        </div>
      </div>
      {error ? (
        <p role="alert" className="rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800">No se pudieron cargar las estadísticas. <button type="button" onClick={onReintentar} className={`font-semibold underline ${foco}`}>Reintentar</button></p>
      ) : !e ? <div className="h-48 animate-pulse rounded-xl bg-gray-100" /> : (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <p className="rounded-xl bg-gray-50 px-4 py-3"><span className="block text-sm font-semibold text-gray-700">Servicios solicitados</span><span className="block text-3xl font-bold tabular-nums text-gray-900">{nf.format(e.solicitados)}</span></p>
            <p className="rounded-xl bg-gray-50 px-4 py-3"><span className="block text-sm font-semibold text-gray-700">Con resultado emitido</span>
              <span className="block text-3xl font-bold tabular-nums text-gray-900">{nf.format(e.emitidos)} <span className="text-base font-semibold text-gray-700">de {nf.format(e.solicitados)}</span></span>
              <span className="text-sm text-gray-700">{nf.format(e.enProceso)} todavía en proceso</span></p>
            <p className="rounded-xl bg-gray-50 px-4 py-3"><span className="block text-sm font-semibold text-gray-700">Entregados a tiempo</span>
              <span className="block text-3xl font-bold tabular-nums text-gray-900">{nf.format(e.aTiempo)} de {nf.format(e.emitidos)}</span><span className="text-sm text-gray-700">de los estudios con resultado</span></p>
          </div>
          <h3 className="mb-2 mt-5 text-sm font-bold text-gray-900">Nivel de riesgo de los estudios con resultado{e.riesgo.total > 0 ? ` · ${nf.format(e.riesgo.total)} estudios` : ''}</h3>
          <AnillosRiesgo datos={e.riesgo} onSeleccionar={onNivel} />
          {e.riesgo.total > 0 && <p className="mt-3 text-xs text-gray-700">Al hacer clic en un nivel se abre Mis solicitudes filtrada por ese nivel y periodo.</p>}
        </>
      )}
    </section>
  )
}

export default function Dashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
  const [datos, setDatos] = useState(null)
  const [inicio, setInicio] = useState(null)               // PB-05: informes listos, requiere tu atención y tiempos de entrega
  const [errorInicio, setErrorInicio] = useState(false)
  const [estadisticas, setEstadisticas] = useState(null)
  const [errorEstadisticas, setErrorEstadisticas] = useState(false)
  const [params, setParams] = useSearchParams()
  const periodo = PERIODOS.some(([k]) => k === params.get('periodo')) ? params.get('periodo') : 'ANIO'
  // La última vez que abrió su inicio (solo en este navegador): lo publicado después sale marcado como nuevo. Se lee una vez por visita.
  const [desdeVisita] = useState(() => { try { return localStorage.getItem(CLAVE_VISITA) } catch { return null } })
  const [bolsa, setBolsa] = useState(null)
  const [credito, setCredito] = useState(null)
  const [gestor, setGestor] = useState(null)
  const config = useConfigTienda()
  const [lotesPorVencer, setLotesPorVencer] = useState([])   // TP-07: unidades que vencen pronto
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
    dashboardService.obtenerInicio(desdeVisita)
      .then(d => { setInicio(d); setErrorInicio(false); try { localStorage.setItem(CLAVE_VISITA, isoLocal(new Date())) } catch { /* sin almacenamiento: no hay marcas de «nuevo» */ } })
      .catch(() => setErrorInicio(true))
    return dashboardService.obtenerDashboard()
      .then((dashData) => {
        setDatos(dashData)
        const esPospagoLocal = dashData.tipoCliente === 'POSPAGO'
        if (esPospagoLocal && usuario?.rol !== 'ADMIN_CLIENTE') return
        if (!esPospagoLocal) dashboardService.obtenerLotesPorVencer().then((l) => setLotesPorVencer(Array.isArray(l) ? l : [])).catch(() => setLotesPorVencer([]))
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
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario?.rol])

  const cargarEstadisticas = useCallback(() => {
    dashboardService.obtenerEstadisticas(periodo).then(e => { setEstadisticas(e); setErrorEstadisticas(false) }).catch(() => setErrorEstadisticas(true))
  }, [periodo])

  useEffect(() => { cargarDatos() }, [cargarDatos])
  useEffect(() => { setEstadisticas(null); cargarEstadisticas() }, [cargarEstadisticas])
  useEffect(() => { dashboardService.obtenerGestor().then(setGestor).catch(() => setGestor(null)) }, [])
  useCambios(() => { cargarDatos(); cargarEstadisticas() }, { esperaMs: 1200 })

  const irA = (ruta) => navigate(ruta)

  /* ── Series ── */
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
        descripcion={<span className="inline-block first-letter:uppercase">{fechaLarga()}{inicio && ` · ${nf.format(inicio.informesNuevos)} ${inicio.informesNuevos === 1 ? 'informe nuevo' : 'informes nuevos'} y ${nf.format(inicio.cosasPorAtender)} ${inicio.cosasPorAtender === 1 ? 'cosa que necesita' : 'cosas que necesitan'} tu atención`}</span>}
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
          {lotesPorVencer.length > 0 && (
            <div role="alert" className="flex flex-wrap items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              <span className="shrink-0 mt-0.5">{I.alerta('h-5 w-5')}</span>
              <div className="min-w-0 flex-1">
                <p className="font-semibold">Tienes unidades por vencer</p>
                <ul className="mt-1 space-y-0.5">
                  {lotesPorVencer.map((l) => (
                    <li key={l.idLote}>
                      <strong>{l.unidades}</strong> {l.unidades === 1 ? 'unidad' : 'unidades'} de {l.nombreProceso} vencen el {fechaCorta(l.fechaVencimiento)}
                      {' '}({l.diasRestantes === 0 ? 'hoy' : `en ${l.diasRestantes} ${l.diasRestantes === 1 ? 'día' : 'días'}`}). Úsalas antes de esa fecha.
                    </li>
                  ))}
                </ul>
              </div>
              <Link to="/cliente/nueva-solicitud" className="ml-auto whitespace-nowrap text-xs font-semibold hover:underline">Nueva solicitud →</Link>
            </div>
          )}
          {errorSecundario && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">{errorSecundario}</div>
          )}

          {errorInicio && !inicio && (
            <p role="alert" className="rounded-xl border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">No se pudieron cargar tus informes ni tus pendientes. <button type="button" onClick={cargarDatos} className={`font-semibold underline ${foco}`}>Reintentar</button></p>
          )}

          {/* ── Estado de sus solicitudes (el mismo componente y endpoint que Mis solicitudes) ── */}
          <SemaforoEstados interno={false} textoCliente titulo="Estado de mis solicitudes"
            onSeleccionar={(estado) => irA(estado ? `/cliente/solicitudes?estado=${estado}` : '/cliente/solicitudes')} />

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
            <InformesListos inicio={inicio} puedeDescargar={esAdminCliente} onIr={irA} />
            <RequiereAtencion inicio={inicio} />
          </div>

          <TiemposEntrega inicio={inicio} onIr={irA} />

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
            <div className="xl:col-span-2">
              {/* ── Bolsa prepago o cupo pospago (solo lo que su rol puede ver) ── */}
              {muestraSaldo && (puedeVerCupo ? (
                <Grafica titulo="Cupo de crédito" descripcion="Lo que puedes seguir solicitando a crédito"
                  accion={<Enlace onClick={() => irA('/cliente/pospago')}>Mi crédito</Enlace>}>
                  <p className="text-2xl font-bold tabular-nums tracking-tight text-gray-900">{formatearCOP(creditoDisponible)}</p>
                  <p className="text-xs text-gray-500">disponible{limite > 0 ? ` de ${formatearCOP(limite)}` : ''}</p>
                  {limite > 0 && (
                    <div className="mt-4">
                      <div className="h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
                        <div className={`h-full rounded-full transition-[width] duration-700 ${pctUsado >= 90 ? 'bg-red-500' : pctUsado >= 70 ? 'bg-amber-400' : 'bg-primary-500'}`} style={{ width: `${pctUsado}%` }} />
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
                                {/* Con la cobertura por sede: unidades por sede y nacionales por separado */}
                                {config.coberturaPorSede && b.disponibleNacional > 0 && (
                                  <p className="mt-0.5 text-[11px] text-gray-600">
                                    {b.cantidadDisponible - b.disponibleNacional} por sede · {b.disponibleNacional} nacionales
                                  </p>
                                )}
                                {/* Con la vigencia: el lote que vence primero */}
                                {config.vigenciaPorPlan && b.proximoVencimiento && (
                                  <p className="mt-0.5 text-[11px] text-amber-700">
                                    {b.unidadesQueVencen} {b.unidadesQueVencen === 1 ? 'vence' : 'vencen'} {fechaCorta(b.proximoVencimiento)}
                                  </p>
                                )}
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

            </div>
            <div>
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

          <Estadisticas e={estadisticas} error={errorEstadisticas} periodo={periodo} onReintentar={cargarEstadisticas}
            onPeriodo={(k) => setParams(prev => { const n = new URLSearchParams(prev); n.set('periodo', k); return n }, { replace: true })}
            onNivel={(nivel) => irA(`/cliente/solicitudes?nivel=${nivel}&periodo=${periodo}`)} />
        </div>
      )}
    </div>
  )
}
