import { useState, useEffect, useCallback } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import bandejaService from '../../services/bandejaService'
import { useCambios } from '../../hooks/useCambios'
import { useEstadosSubproceso } from '../../hooks/useEstadosSubproceso'
import EstadoTiempo from '../../components/EstadoTiempo'
import ChipRiesgo from '../../components/ChipRiesgo'
import AnillosRiesgo from '../../components/graficos/AnillosRiesgo'
import { saludo, fechaLarga } from '../../components/tablero/Tablero'

// «Mi bandeja del día» del gestor (PB-05). No calcula nada: todo viene de GET /gestor/bandeja (el mismo semáforo que Solicitudes, la publicación por
// subproceso, la pasta y el enlace del candidato) con el alcance de sus clientes. Los cinco pasos filtran Solicitudes por los estados de SE-01 y no
// crean estados nuevos. El periodo de «Riesgo detectado» queda en la URL (?periodo=).

const nf = new Intl.NumberFormat('es-CO')
const PERIODOS = [['HOY', 'Hoy'], ['SEMANA', 'Semana'], ['MES', 'Mes'], ['ANIO', 'Año']]
const ESTADO_DEL_PASO = { 1: 'PENDIENTE', 2: 'PROGRAMANDO', 3: 'EN_EJECUCION', 4: 'FINALIZADO', 5: 'PUBLICADO' }
const foco = 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600'
const botonPrimario = `inline-flex min-h-[44px] items-center justify-center rounded-lg bg-primary-600 px-4 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-primary-700 ${foco}`
const botonSecundario = `inline-flex min-h-[44px] items-center justify-center rounded-lg border border-gray-300 bg-white px-4 text-sm font-semibold text-gray-900 shadow-sm transition-colors hover:bg-gray-50 ${foco}`

function Seccion({ numero, titulo, descripcion, derecha, children, className = '', tono = 'blanco' }) {
  return (
    <section aria-labelledby={`bloque-${numero}`}
      className={`rounded-2xl border p-5 shadow-sm ${tono === 'ambar' ? 'border-amber-200 bg-amber-50' : 'border-gray-200 bg-white'} ${className}`}>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id={`bloque-${numero}`} className="text-lg font-bold text-gray-900">{numero}. {titulo}</h2>
          {descripcion && <p className={`text-sm ${tono === 'ambar' ? 'text-amber-900' : 'text-gray-700'}`}>{descripcion}</p>}
        </div>
        {derecha}
      </div>
      {children}
    </section>
  )
}

function Punto({ color }) {
  return <span aria-hidden="true" className="inline-block h-[9px] w-[9px] shrink-0 rounded-full" style={{ backgroundColor: color }} />
}

/* ── Tu trabajo en 5 pasos ── */
function Pasos({ datos, onIr }) {
  const { de } = useEstadosSubproceso()
  const cancelado = de('CANCELADO')
  return (
    <section aria-labelledby="pasos-titulo" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="pasos-titulo" className="text-sm font-bold text-gray-900">Tu trabajo en 5 pasos · cada paso filtra por los estados unificados (SE-01)</h2>
        <p className="text-xs text-gray-700">{nf.format(datos.estudios)} servicios · un servicio cuenta en cada estado donde tenga un subproceso · sin %</p>
      </div>
      <ol className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
        {datos.pasos.map(p => (
          <li key={p.numero}>
            <button type="button" onClick={() => onIr(`/gestor/solicitudes?estado=${p.estados}`)}
              aria-label={`Paso ${p.numero}, ${p.titulo}: ${nf.format(p.servicios)} servicios. Abre Solicitudes filtrada.`}
              className={`h-full w-full min-h-[44px] rounded-xl border p-4 text-left transition-shadow hover:shadow-md ${foco} ${p.resaltado ? 'border-amber-300 bg-amber-50' : 'border-gray-200 bg-white'}`}>
              <span className="flex items-center gap-2">
                <span aria-hidden="true" className="grid h-6 w-6 place-items-center rounded-full bg-slate-900 text-xs font-bold text-white">{p.numero}</span>
                <span className="text-sm font-bold text-gray-900">{p.titulo}</span>
              </span>
              <span className="mt-2 block text-3xl font-bold tabular-nums text-gray-900">{nf.format(p.servicios)}</span>
              <span className="mt-1 flex items-start gap-1.5 text-xs font-semibold text-gray-900">
                <span className="mt-1"><Punto color={de(ESTADO_DEL_PASO[p.numero])?.color ?? '#9CA3AF'} /></span>{p.etiqueta}
              </span>
              <span className="mt-1 block text-xs text-gray-700">{p.detalle}</span>
            </button>
          </li>
        ))}
      </ol>
      <p className="mt-3 flex flex-wrap items-center gap-1.5 text-xs text-gray-700">
        Fuera del flujo:
        <button type="button" onClick={() => onIr('/gestor/solicitudes?estado=CANCELADO')} className={`inline-flex items-center gap-1.5 rounded font-semibold text-gray-900 underline-offset-2 hover:underline ${foco}`}>
          <Punto color={cancelado?.color ?? '#9CA3AF'} /> Cancelado {nf.format(datos.cancelado.servicios)}
        </button>
        ({nf.format(datos.cancelado.subprocesos)} subprocesos). Los pasos son acciones del gestor; los estados siguen siendo los 7 de SE-01.
      </p>
    </section>
  )
}

/* ── 1. Control de calidad ── */
function ControlCalidad({ cc, onIr }) {
  return (
    <Seccion numero={1} titulo="Control de calidad"
      descripcion="Subprocesos en Finalizado que debes revisar y publicar, y estudios con la pasta final pendiente. El riesgo más alto va primero.">
      <p className="mb-3 text-sm text-gray-700">
        <strong className="text-gray-900">{nf.format(cc.finalizadosPorPublicar)}</strong> finalizados por publicar · <strong className="text-gray-900">{nf.format(cc.pastasPendientes)}</strong> pasta pendiente
      </p>
      {cc.filas.length === 0 ? (
        <p className="rounded-xl border border-dashed border-gray-300 bg-gray-50 px-4 py-8 text-center text-sm text-gray-700">No tienes informes por revisar ni pastas por emitir.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-[11px] font-semibold uppercase tracking-wider text-gray-700">
                <th scope="col" className="px-2 py-2">N.º</th><th scope="col" className="px-2 py-2">Evaluado · cliente</th><th scope="col" className="px-2 py-2">Qué llegó</th>
                <th scope="col" className="px-2 py-2">Responsable · tiempo</th><th scope="col" className="px-2 py-2">Riesgo</th><th scope="col" className="px-2 py-2">Acción</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {cc.filas.map(f => (
                <tr key={`${f.tipo}-${f.idServicio}-${f.idSubproceso ?? 'p'}`} className="align-top">
                  <td className="px-2 py-3 font-semibold tabular-nums text-gray-700">#{f.idServicio}</td>
                  <td className="px-2 py-3"><p className="font-semibold text-gray-900">{f.evaluado}</p><p className="text-xs text-gray-700">{f.cliente}</p></td>
                  <td className="px-2 py-3"><p className="text-gray-900">{f.queLlego}</p><p className="text-xs text-gray-700">{f.detalle}</p></td>
                  <td className="px-2 py-3">
                    <p className="text-gray-900">{f.responsable}</p>
                    {f.tipo === 'PASTA' ? <p className="text-xs font-semibold text-amber-900">El cliente no ve el resultado</p> : <EstadoTiempo tiempo={f.tiempo} />}
                  </td>
                  <td className="px-2 py-3"><ChipRiesgo nivel={f.riesgoNivel} puntaje={f.riesgoPuntaje} /></td>
                  <td className="px-2 py-3">
                    <button type="button" onClick={() => onIr(f.tipo === 'PASTA' ? `/gestor/solicitudes?emitir=${f.idServicio}` : `/gestor/solicitudes?estado=FINALIZADO&expandir=${f.idServicio}`)}
                      className={f.tipo === 'PASTA' ? `${botonSecundario} !border-primary-600 !text-primary-700` : botonPrimario}>
                      {f.accionEtiqueta}<span className="sr-only"> del estudio {f.idServicio}</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Seccion>
  )
}

/* ── 2. Requiere tu atención ── */
function TarjetaAtencion({ cantidad, titulo, detalle, muestras, accion }) {
  return (
    <div className="rounded-xl border border-amber-200 bg-white p-4">
      <p className="flex items-baseline gap-2"><span className="text-3xl font-bold tabular-nums text-amber-900">{nf.format(cantidad)}</span><span className="text-sm font-bold text-gray-900">{titulo}</span></p>
      <p className="mt-1 border-b border-gray-100 pb-2 text-xs text-gray-700">{detalle}</p>
      <ul className="mt-2 space-y-1 text-sm text-gray-900">{muestras.map((m, i) => <li key={i}>{m.texto}</li>)}</ul>
      <button type="button" onClick={accion.onClick} className={`mt-2 inline-flex min-h-[44px] items-center rounded text-sm font-semibold text-primary-700 underline underline-offset-2 hover:text-primary-800 ${foco}`}>{accion.texto}</button>
    </div>
  )
}

function Atencion({ a, onIr, onAvisar, aviso }) {
  const tarjetas = [
    a.novedades.cantidad > 0 && { clave: 'nov', cantidad: a.novedades.cantidad, titulo: 'Novedades abiertas', b: a.novedades,
      accion: { texto: 'Responder novedades', onClick: () => { const m = a.novedades.muestras[0]; onIr(m?.idSubproceso ? `/gestor/formulario/${m.idSubproceso}` : '/gestor/agenda') } } },
    a.sinAsignar.cantidad > 0 && { clave: 'sin', cantidad: a.sinAsignar.cantidad, titulo: 'Subprocesos sin asignar', b: a.sinAsignar, accion: { texto: 'Asignar', onClick: () => onIr('/gestor/solicitudes?estado=PENDIENTE') } },
    a.candidatosSinHojaDeVida.cantidad > 0 && { clave: 'hv', cantidad: a.candidatosSinHojaDeVida.cantidad, titulo: 'Candidatos sin hoja de vida', b: a.candidatosSinHojaDeVida,
      accion: { texto: 'Reenviar enlace', onClick: () => onIr(`/gestor/solicitudes?enlace=${a.candidatosSinHojaDeVida.muestras[0]?.idServicio ?? ''}`) } },
    a.citasSinAgendar.cantidad > 0 && { clave: 'cit', cantidad: a.citasSinAgendar.cantidad, titulo: 'Citas sin agendar', b: a.citasSinAgendar,
      accion: { texto: 'Avisar al programador', onClick: onAvisar } },
  ].filter(Boolean)
  return (
    <Seccion numero={2} titulo="Requiere tu atención" descripcion="Lo que está detenido y depende de una acción tuya." tono="ambar">
      <div role="status" aria-live="polite" className="empty:hidden mb-3">
        {aviso && (
          <p className={`rounded-lg border px-3 py-2 text-sm ${aviso.tipo === 'error' ? 'border-red-300 bg-red-50 text-red-800' : 'border-green-300 bg-green-50 text-green-900'}`}>
            {aviso.texto}{aviso.tipo === 'error' && <button type="button" onClick={onAvisar} className={`ml-2 font-semibold underline ${foco}`}>Reintentar</button>}
          </p>
        )}
      </div>
      {tarjetas.length === 0 ? (
        <p className="rounded-xl border border-dashed border-amber-300 bg-white px-4 py-8 text-center text-sm text-gray-800">Nada está detenido por ti: no hay novedades, subprocesos sin asignar, candidatos pendientes ni citas sin agendar.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          {tarjetas.map(t => <TarjetaAtencion key={t.clave} cantidad={t.cantidad} titulo={t.titulo} detalle={t.b.detalle} muestras={t.b.muestras} accion={t.accion} />)}
        </div>
      )}
    </Seccion>
  )
}

/* ── 3. Tiempos ── */
const TIEMPOS = [
  ['VENCIDAS', 'Vencidas', 'bg-red-50 border-red-200 text-red-800'],
  ['VENCE_HOY', 'Vence hoy', 'bg-white border-gray-200 text-gray-900'],
  ['VENCE_MANANA', 'Vence mañana', 'bg-amber-50 border-amber-200 text-amber-900'],
  ['A_TIEMPO', 'A tiempo', 'bg-green-50 border-green-200 text-green-900'],
  ['EN_PAUSA', 'En pausa', 'bg-slate-100 border-slate-200 text-slate-800'],
]
const RETRASO = [['CANDIDATO', 'Candidato (sin hoja de vida o no asistió)'], ['CAMPO', 'Campo (sin asignar o sin cita)'], ['REVISION_GESTOR', 'Revisión del gestor']]

function Tiempos({ t, onIr }) {
  const maximo = Math.max(1, ...RETRASO.map(([k]) => t.vencidasPorResponsable[k] ?? 0))
  return (
    <Seccion numero={3} titulo="Tiempos" descripcion="Un solo semáforo, contado por estudio, en días hábiles." className="lg:col-span-3">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
        {TIEMPOS.map(([k, nombre, clase]) => (
          <button key={k} type="button" onClick={() => onIr(`/gestor/solicitudes?tiempo=${k}`)} aria-label={`${nombre}: ${t.conteo[k] ?? 0}. Abre Solicitudes filtrada.`}
            className={`min-h-[44px] rounded-xl border p-3 text-left transition-shadow hover:shadow-md ${foco} ${clase}`}>
            <span className="block text-2xl font-bold tabular-nums">{nf.format(t.conteo[k] ?? 0)}</span>
            <span className="block text-sm font-semibold text-gray-900">{nombre}</span>
          </button>
        ))}
      </div>
      <h3 className="mt-5 text-sm font-bold text-gray-900">Las vencidas, por responsable del retraso</h3>
      <ul className="mt-2 space-y-2">
        {RETRASO.map(([k, nombre]) => {
          const n = t.vencidasPorResponsable[k] ?? 0
          return (
            <li key={k} className="grid grid-cols-[minmax(0,12rem)_1fr_2.5rem] items-center gap-3 text-sm text-gray-900">
              <span>{nombre}</span>
              <span className="h-2.5 overflow-hidden rounded-full bg-gray-100" aria-hidden="true"><span className="block h-full rounded-full bg-red-700" style={{ width: `${(n / maximo) * 100}%` }} /></span>
              <span className="text-right font-semibold tabular-nums">{nf.format(n)}</span>
            </li>
          )
        })}
      </ul>
    </Seccion>
  )
}

/* ── 4. Mi equipo ── */
function Equipo({ equipo }) {
  return (
    <Seccion numero={4} titulo="Mi equipo" descripcion="Carga de quienes ejecutan los subprocesos." className="lg:col-span-2">
      {equipo.length === 0 ? (
        <p className="rounded-xl border border-dashed border-gray-300 bg-gray-50 px-4 py-8 text-center text-sm text-gray-700">Nadie tiene subprocesos en curso en este momento.</p>
      ) : (
        <ul className="divide-y divide-gray-100">
          {equipo.map(p => (
            <li key={p.idUsuario} className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-4 py-3">
              <span><span className="block text-sm font-bold text-gray-900">{p.nombre}</span><span className="block text-xs text-gray-700">{p.rol}</span></span>
              <span className="text-center"><span className="block text-lg font-bold tabular-nums text-gray-900">{nf.format(p.asignados)}</span><span className="block text-[11px] text-gray-700">asignados</span></span>
              <span className="text-center"><span className="block text-lg font-bold tabular-nums text-red-800">{nf.format(p.vencidos)}</span><span className="block text-[11px] text-gray-700">vencidos</span></span>
              <span className="text-center"><span className="block text-lg font-bold tabular-nums text-blue-800">{nf.format(p.enRevision)}</span><span className="block text-[11px] text-gray-700">en revisión</span></span>
            </li>
          ))}
        </ul>
      )}
    </Seccion>
  )
}

/* ── 5. Riesgo detectado ── */
function Riesgo({ r, periodo, onPeriodo, error, onReintentar }) {
  return (
    <Seccion numero={5} titulo="Riesgo detectado" descripcion="Estudios cerrados y su nivel de riesgo, para todos tus clientes."
      derecha={
        <div role="group" aria-label="Periodo" className="flex flex-wrap gap-2">
          {PERIODOS.map(([k, nombre]) => (
            <button key={k} type="button" aria-pressed={periodo === k} onClick={() => onPeriodo(k)}
              className={`min-h-[44px] rounded-full border px-4 text-sm font-semibold ${foco} ${periodo === k ? 'border-slate-900 bg-slate-900 text-white' : 'border-gray-300 bg-white text-gray-900 hover:bg-gray-50'}`}>{nombre}</button>
          ))}
        </div>
      }>
      {error ? (
        <p role="alert" className="rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800">No se pudo cargar el riesgo detectado. <button type="button" onClick={onReintentar} className={`font-semibold underline ${foco}`}>Reintentar</button></p>
      ) : !r ? <div className="h-40 animate-pulse rounded-xl bg-gray-100" /> : (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <p className="rounded-xl bg-gray-50 px-4 py-3"><span className="block text-2xl font-bold tabular-nums text-gray-900">{nf.format(r.cerrados)}</span><span className="text-sm text-gray-700">estudios cerrados</span></p>
            <p className="rounded-xl bg-gray-50 px-4 py-3"><span className="block text-2xl font-bold tabular-nums text-red-800">{nf.format(r.riesgoAlto)} de {nf.format(r.cerrados)}</span><span className="text-sm text-gray-700">con riesgo alto</span></p>
            <p className="rounded-xl bg-gray-50 px-4 py-3"><span className="block text-2xl font-bold tabular-nums text-red-800">{nf.format(r.conCritico)}</span><span className="text-sm text-gray-700">con hallazgo crítico</span></p>
          </div>
          <AnillosRiesgo datos={r.riesgo} className="mt-3" />
          <p className="mt-3 text-xs text-gray-700">Se cuenta sobre los estudios cerrados (con la pasta final emitida) en el periodo.</p>
        </>
      )}
    </Seccion>
  )
}

export default function GestorDashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const periodo = PERIODOS.some(([k]) => k === params.get('periodo')) ? params.get('periodo') : 'MES'
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(false)
  const [riesgo, setRiesgo] = useState(null)
  const [errorRiesgo, setErrorRiesgo] = useState(false)
  const [aviso, setAviso] = useState(null)

  const cargar = useCallback(() => {
    bandejaService.bandeja().then(d => { setDatos(d); setError(false) }).catch(() => setError(true))
  }, [])
  const cargarRiesgo = useCallback(() => {
    bandejaService.riesgo(periodo).then(r => { setRiesgo(r); setErrorRiesgo(false) }).catch(() => setErrorRiesgo(true))
  }, [periodo])

  useEffect(() => { cargar() }, [cargar])
  useEffect(() => { setRiesgo(null); cargarRiesgo() }, [cargarRiesgo])
  useCambios(() => { cargar(); cargarRiesgo() }, { esperaMs: 1500 })

  const avisar = () => {
    setAviso(null)
    bandejaService.avisarProgramador()
      .then(r => { setAviso({ tipo: 'ok', texto: `Aviso enviado · ${r.citas} ${r.citas === 1 ? 'cita' : 'citas'} para ${r.programadores} ${r.programadores === 1 ? 'programador' : 'programadores'}. Queda registrado.` }); cargar() })
      .catch(e => setAviso({ tipo: 'error', texto: e.response?.data?.mensaje ?? 'No se pudo avisar al programador.' }))
  }

  const nombre = usuario?.nombre?.trim() || 'gestor'

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-primary-700">Mi bandeja del día</p>
          <h1 className="text-3xl font-bold text-gray-900">{saludo()}, {nombre}</h1>
          <p className="text-sm text-gray-700"><span className="inline-block first-letter:uppercase">{fechaLarga()}</span>{datos && <> · {nf.format(datos.estudios)} estudios a tu cargo</>}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => window.dispatchEvent(new Event('abrir-mensajes-gestor'))} className={botonSecundario}>
            Mensajes de clientes{datos ? ` · ${nf.format(datos.mensajesSinLeer)}` : ''}
          </button>
          <button type="button" onClick={() => navigate('/gestor/solicitudes')} className={botonPrimario}>Ver todas las solicitudes</button>
        </div>
      </header>

      {error && !datos ? (
        <p role="alert" className="rounded-xl border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-800">No se pudo cargar tu bandeja. <button type="button" onClick={cargar} className={`font-semibold underline ${foco}`}>Reintentar</button></p>
      ) : !datos ? (
        <div className="space-y-5" aria-busy="true"><div className="h-56 animate-pulse rounded-2xl bg-gray-100" /><div className="h-72 animate-pulse rounded-2xl bg-gray-100" /></div>
      ) : (
        <>
          <Pasos datos={datos} onIr={navigate} />
          <ControlCalidad cc={datos.controlCalidad} onIr={navigate} />
          <Atencion a={datos.atencion} onIr={navigate} onAvisar={avisar} aviso={aviso} />
          <div className="grid grid-cols-1 gap-5 lg:grid-cols-5">
            <Tiempos t={datos.tiempos} onIr={navigate} />
            <Equipo equipo={datos.equipo} />
          </div>
        </>
      )}
      <Riesgo r={riesgo} periodo={periodo} error={errorRiesgo} onReintentar={cargarRiesgo}
        onPeriodo={(k) => setParams(prev => { const n = new URLSearchParams(prev); n.set('periodo', k); return n }, { replace: true })} />
    </div>
  )
}
