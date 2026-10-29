import { useState, useEffect, useCallback, Fragment } from 'react'
import trabajoService from '../../services/trabajoService'
import Toast from '../../components/Toast'
import { ConfirmModal } from '../../components/ui/Modal'
import { useIntervaloVisible } from '../../hooks/useIntervaloVisible'
import {
  EncabezadoPagina, Etiqueta, Indicador, TarjetaSeccion, AlertaError, EsqueletoPagina, EstadoVacio,
  botonSecundario, botonPeligro,
} from '../../components/admin/PaginaAdmin'

// Panel de la cola de trabajos pesados (backend: módulo trabajos). Muestra el estado de la cola,
// cómo están los recursos que protege (hilos, LibreOffice, conexiones de BD, memoria) y los
// trabajos FALLIDOS — la "dead letter queue" — con su error técnico y el botón Reintentar.

const ETIQUETA_TIPO = {
  CARGA_MASIVA: 'Carga masiva',
  INFORME_PDF: 'Informe PDF (cliente)',
  FORMULARIO_PDF: 'PDF de formulario (interno)',
}
const ESTADOS = [
  { valor: '', etiqueta: 'Todos' },
  { valor: 'EN_COLA', etiqueta: 'En cola' },
  { valor: 'PROCESANDO', etiqueta: 'Procesando' },
  { valor: 'COMPLETADO', etiqueta: 'Completados' },
  { valor: 'FALLIDO', etiqueta: 'Fallidos' },
]
const ESTILO_ESTADO = {
  EN_COLA:    { etiqueta: 'En cola',    chip: 'bg-gray-100 text-gray-700 ring-gray-500/10',         punto: 'bg-gray-400' },
  PROCESANDO: { etiqueta: 'Procesando', chip: 'bg-blue-50 text-blue-700 ring-blue-600/20',          punto: 'bg-blue-500 animate-pulse' },
  COMPLETADO: { etiqueta: 'Completado', chip: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', punto: 'bg-emerald-500' },
  FALLIDO:    { etiqueta: 'Fallido',    chip: 'bg-red-50 text-red-700 ring-red-600/20',             punto: 'bg-red-500' },
}
const REFRESCO_MS = 5000

const Icono = {
  cola: <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M6.429 9.75L2.25 12l4.179 2.25m0-4.5l5.571 3 5.571-3m-11.142 0L2.25 7.5 12 2.25l9.75 5.25-4.179 2.25m0 0L21.75 12l-4.179 2.25m0 0l4.179 2.25L12 21.75 2.25 16.5l4.179-2.25m11.142 0l-5.571 3-5.571-3" /></svg>,
  reloj: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>,
  rayo: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" /></svg>,
  check: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>,
  alerta: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" /></svg>,
  cpu: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M8.25 3v1.5M4.5 8.25H3m18 0h-1.5M4.5 12H3m18 0h-1.5m-15 3.75H3m18 0h-1.5M8.25 19.5V21M12 3v1.5m0 15V21m3.75-18v1.5m0 15V21m-9-1.5h10.5a2.25 2.25 0 002.25-2.25V6.75a2.25 2.25 0 00-2.25-2.25H6.75A2.25 2.25 0 004.5 6.75v10.5a2.25 2.25 0 002.25 2.25zm.75-12h9v9h-9v-9z" /></svg>,
  servidor: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M5.25 14.25h13.5m-13.5 0a3 3 0 01-3-3m3 3a3 3 0 100 6h13.5a3 3 0 100-6m-16.5-3a3 3 0 013-3h13.5a3 3 0 013 3m-19.5 0a4.5 4.5 0 01.9-2.7L5.737 5.1a3.375 3.375 0 012.7-1.35h7.126c1.062 0 2.062.5 2.7 1.35l2.587 3.45a4.5 4.5 0 01.9 2.7m0 0a3 3 0 01-3 3m0 3h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008zm-3 6h.008v.008h-.008v-.008zm0-6h.008v.008h-.008v-.008z" /></svg>,
  lista: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM3.75 12h.007v.008H3.75V12zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm-.375 5.25h.007v.008H3.75v-.008zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z" /></svg>,
  chevron: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" /></svg>,
  refrescar: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" /></svg>,
  basura: <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" /></svg>,
}

const fecha = (v) => (v ? new Date(v).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—')
function haceCuanto(v) {
  if (!v) return '—'
  const s = Math.round((Date.now() - new Date(v).getTime()) / 1000)
  if (s < 60) return 'hace un momento'
  if (s < 3600) return `hace ${Math.floor(s / 60)} min`
  if (s < 86400) return `hace ${Math.floor(s / 3600)} h`
  return `hace ${Math.floor(s / 86400)} d`
}
function duracion(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} s`
  return `${Math.floor(ms / 60000)} min ${Math.round((ms % 60000) / 1000)} s`
}
const sumar = (conteos, estado) => Object.values(conteos ?? {}).reduce((t, porEstado) => t + (porEstado[estado] ?? 0), 0)

// Barra de uso: verde < 70%, ámbar < 100%, rojo lleno.
function Uso({ etiqueta, usado, maximo, nota, icono }) {
  const pct = maximo > 0 ? Math.min(100, Math.round((usado / maximo) * 100)) : 0
  const color = pct >= 100 ? 'bg-red-500' : pct >= 70 ? 'bg-amber-400' : 'bg-emerald-500'
  return (
    <div>
      <div className="flex items-center justify-between gap-3 text-sm mb-1.5">
        <span className="flex items-center gap-2 text-gray-700">
          {icono && <span className="text-gray-400">{icono}</span>}
          {etiqueta}
        </span>
        <span className="font-semibold tabular-nums text-gray-900">{usado}<span className="font-normal text-gray-400"> / {maximo}</span></span>
      </div>
      <div className="h-2 rounded-full bg-gray-100 overflow-hidden">
        <div className={`h-full rounded-full ${color} transition-all duration-500`} style={{ width: `${Math.max(pct, usado > 0 ? 3 : 0)}%` }} />
      </div>
      {nota && <p className="text-[11px] text-gray-500 mt-1">{nota}</p>}
    </div>
  )
}

function ChipEstado({ estado, progreso }) {
  const e = ESTILO_ESTADO[estado] ?? ESTILO_ESTADO.EN_COLA
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${e.chip}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${e.punto}`} />
      {e.etiqueta}{estado === 'PROCESANDO' && progreso > 0 ? ` · ${progreso}%` : ''}
    </span>
  )
}

export default function TrabajosSegundoPlano() {
  const [metricas, setMetricas] = useState(null)
  const [pagina, setPagina] = useState(null)
  const [estado, setEstado] = useState('')
  const [tipo, setTipo] = useState('')
  const [numPagina, setNumPagina] = useState(0)
  const [abierto, setAbierto] = useState(null)
  const [reintentando, setReintentando] = useState(null)
  const [error, setError] = useState(null)
  const [actualizado, setActualizado] = useState(null)
  const [toast, setToast] = useState(null)
  const [confirmarBorrado, setConfirmarBorrado] = useState(false)
  const [borrando, setBorrando] = useState(false)

  const cargar = useCallback(async () => {
    try {
      const [m, p] = await Promise.all([
        trabajoService.metricas(),
        trabajoService.listar({ estado, tipo, pagina: numPagina }),
      ])
      setMetricas(m)
      setPagina(p)
      setError(null)
      setActualizado(new Date())
    } catch (e) {
      setError(e.response?.data?.mensaje ?? 'No se pudo cargar el estado de la cola.')
    }
  }, [estado, tipo, numPagina])

  useEffect(() => { cargar() }, [cargar])
  // Refresco automático, en pausa con la pestaña oculta.
  useIntervaloVisible(cargar, REFRESCO_MS)

  const reintentar = async (t) => {
    setReintentando(t.id)
    try {
      await trabajoService.reintentar(t.id)
      setToast({ mensaje: 'El trabajo volvió a la cola.', tipo: 'exito' })
      await cargar()
    } catch (e) {
      setToast({ mensaje: e.response?.data?.mensaje ?? 'No se pudo reintentar.', tipo: 'error' })
    } finally {
      setReintentando(null)
    }
  }

  const borrarCompletados = async () => {
    setConfirmarBorrado(false)
    setBorrando(true)
    try {
      const { borrados, minutosProtegidos } = await trabajoService.borrarCompletados()
      setToast({
        mensaje: borrados > 0
          ? `Se borraron ${borrados} trabajo(s) completado(s). Los de los últimos ${minutosProtegidos} min se conservan por si se están descargando.`
          : `No había completados para borrar (los de los últimos ${minutosProtegidos} min se conservan).`,
        tipo: 'exito',
      })
      await cargar()
    } catch (e) {
      setToast({ mensaje: e.response?.data?.mensaje ?? 'No se pudieron borrar los completados.', tipo: 'error' })
    } finally {
      setBorrando(false)
    }
  }

  const conteos = metricas?.conteos
  const enCola = sumar(conteos, 'EN_COLA')
  const procesando = sumar(conteos, 'PROCESANDO')
  const completadosTotal = sumar(conteos, 'COMPLETADO')
  const fallidosTotal = sumar(conteos, 'FALLIDO')

  return (
    <div className="max-w-6xl">
      <EncabezadoPagina
        seccion="Sistema"
        titulo="Trabajos en segundo plano"
        icono={Icono.cola}
        descripcion="Cargas masivas y PDF (informes del cliente y formularios internos) se procesan en esta cola en lugar de dentro de la petición, para que un pico de uso no tumbe el servidor. Los fallidos quedan aquí para revisarlos y reintentarlos."
        meta={actualizado && (
          <Etiqueta tono="verde">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
            En vivo · actualizado {actualizado.toLocaleTimeString('es-CO')}
          </Etiqueta>
        )}
        acciones={<>
          <button type="button" onClick={cargar} className={botonSecundario}>{Icono.refrescar} Actualizar</button>
          <button type="button" onClick={() => setConfirmarBorrado(true)} disabled={borrando || !completadosTotal} className={botonPeligro}>
            {Icono.basura} {borrando ? 'Borrando…' : 'Borrar completados'}
          </button>
        </>}
      />

      <AlertaError mensaje={error} onCerrar={() => setError(null)} />

      {!metricas ? (
        <EsqueletoPagina indicadores={4} bloques={2} />
      ) : (
        <div className="space-y-5">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <Indicador etiqueta="En cola" valor={enCola} detalle="Esperando un hilo libre" icono={Icono.reloj} tono="gris" />
            <Indicador etiqueta="Procesando" valor={procesando} detalle="Corriendo ahora" icono={Icono.rayo} tono="primario" />
            <Indicador etiqueta="Completados (24 h)" valor={metricas.completadosUltimas24h} detalle="Se borran solos a las 24 h" icono={Icono.check} tono="verde" />
            <Indicador etiqueta="Fallidos (24 h)" valor={metricas.fallidosUltimas24h}
              detalle={fallidosTotal > 0 ? `${fallidosTotal} por revisar en total` : 'Sin fallas'}
              icono={Icono.alerta} tono="ambar" alerta={metricas.fallidosUltimas24h > 0} />
          </div>

          <div className="grid md:grid-cols-2 gap-5">
            <TarjetaSeccion icono={Icono.cpu} titulo="Hilos de trabajo" descripcion="Cada tipo tiene su propio grupo de hilos: uno saturado no frena a los demás.">
              <div className="space-y-4">
                {metricas.pools.map(p => (
                  <Uso key={p.nombre} etiqueta={ETIQUETA_TIPO[p.nombre] ?? p.nombre} usado={p.enCurso} maximo={p.hilos}
                    nota={p.rechazados > 0 ? `${p.rechazados} devuelto(s) a la cola por pool lleno desde el arranque` : null} />
                ))}
                {metricas.asincronoGeneral && (
                  <Uso etiqueta="Correos, notificaciones y pagos" usado={metricas.asincronoGeneral.enCola} maximo={metricas.asincronoGeneral.capacidadCola}
                    nota={`Cola en memoria · ${metricas.asincronoGeneral.enCurso} de ${metricas.asincronoGeneral.hilos} hilos trabajando`} />
                )}
              </div>
            </TarjetaSeccion>
            <TarjetaSeccion icono={Icono.servidor} titulo="Recursos del servidor" descripcion="Lo que la cola protege. En rojo = saturado.">
              <div className="space-y-4">
                <Uso etiqueta="Conversiones LibreOffice" usado={metricas.libreOffice.enUso} maximo={metricas.libreOffice.maximo}
                  nota={metricas.libreOffice.esperando > 0 ? `${metricas.libreOffice.esperando} esperando turno` : null} />
                {metricas.conexionesBd && (
                  <Uso etiqueta="Conexiones de base de datos" usado={metricas.conexionesBd.enUso} maximo={metricas.conexionesBd.maximo}
                    nota={metricas.conexionesBd.esperando > 0 ? `${metricas.conexionesBd.esperando} petición(es) esperando conexión` : null} />
                )}
                <Uso etiqueta="Memoria (heap de Java, MB)" usado={metricas.memoria.heapUsadoMb} maximo={metricas.memoria.heapMaximoMb} />
              </div>
            </TarjetaSeccion>
          </div>

          <TarjetaSeccion
            icono={Icono.lista}
            titulo="Historial de trabajos"
            descripcion="Haz clic en una fila para ver el detalle y el error técnico."
            sinRelleno
            acciones={
              <select aria-label="Todos los tipos" value={tipo} onChange={e => { setTipo(e.target.value); setNumPagina(0) }}
                className="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-primary-500">
                <option value="">Todos los tipos</option>
                {Object.entries(ETIQUETA_TIPO).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            }>
            <div className="border-b border-gray-100 px-5 py-3">
              <div className="inline-flex flex-wrap rounded-lg bg-gray-100 p-0.5 text-xs">
                {ESTADOS.map(e => {
                  const n = e.valor ? sumar(conteos, e.valor) : null
                  return (
                    <button key={e.valor} type="button" onClick={() => { setEstado(e.valor); setNumPagina(0) }}
                      className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                        estado === e.valor ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>
                      {e.etiqueta}{n != null && <span className="ml-1 tabular-nums text-gray-400">{n}</span>}
                    </button>
                  )
                })}
              </div>
            </div>

            {pagina?.content?.length === 0 ? (
              <EstadoVacio icono={Icono.lista} titulo="No hay trabajos con este filtro"
                descripcion="Cuando alguien haga una carga masiva o descargue un PDF, aparecerá aquí." />
            ) : (
              <div className="overflow-x-auto">
                <table className="table-stack w-full text-sm">
                  <thead className="bg-gray-50/80 text-[11px] font-semibold uppercase tracking-wider text-gray-500">
                    <tr>
                      <th className="text-left px-5 py-2.5">Trabajo</th>
                      <th className="text-left px-4 py-2.5">Estado</th>
                      <th className="text-left px-4 py-2.5">Pedido por</th>
                      <th className="text-right px-4 py-2.5">Intentos</th>
                      <th className="text-right px-4 py-2.5">Duración</th>
                      <th className="px-5 py-2.5" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {pagina?.content?.map(t => (
                      <Fragment key={t.id}>
                        <tr className={`cursor-pointer transition-colors ${abierto === t.id ? 'bg-primary-50/40' : 'hover:bg-gray-50'}`}
                          onClick={() => setAbierto(abierto === t.id ? null : t.id)}>
                          <td className="px-5 py-3">
                            <p className="font-medium text-gray-900">{ETIQUETA_TIPO[t.tipo] ?? t.tipo}</p>
                            <p className="text-xs text-gray-400" title={fecha(t.fechaCreacion)}>{haceCuanto(t.fechaCreacion)}</p>
                          </td>
                          <td className="px-4 py-3"><ChipEstado estado={t.estado} progreso={t.progreso} /></td>
                          <td className="px-4 py-3 text-gray-600 truncate max-w-[220px]">{t.usuario}</td>
                          <td className="px-4 py-3 text-right tabular-nums text-gray-600">{t.intentos}/{t.maxIntentos}</td>
                          <td className="px-4 py-3 text-right tabular-nums text-gray-600">{duracion(t.duracionMs)}</td>
                          <td className="px-5 py-3 text-right">
                            <div className="flex items-center justify-end gap-2">
                              {t.reintentable && (
                                <button type="button" disabled={reintentando === t.id}
                                  onClick={(e) => { e.stopPropagation(); reintentar(t) }}
                                  className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-60">
                                  {reintentando === t.id ? 'Reintentando…' : 'Reintentar'}
                                </button>
                              )}
                              <span className={`text-gray-400 transition-transform ${abierto === t.id ? 'rotate-180' : ''}`}>{Icono.chevron}</span>
                            </div>
                          </td>
                        </tr>
                        {abierto === t.id && (
                          <tr className="bg-gray-50/60">
                            <td colSpan={6} className="px-5 py-4">
                              <dl className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-2 text-xs">
                                <div><dt className="text-gray-400">Creado</dt><dd className="text-gray-700">{fecha(t.fechaCreacion)}</dd></div>
                                <div><dt className="text-gray-400">Inicio</dt><dd className="text-gray-700">{fecha(t.fechaInicio)}</dd></div>
                                <div><dt className="text-gray-400">Fin</dt><dd className="text-gray-700">{fecha(t.fechaFin)}</dd></div>
                                <div><dt className="text-gray-400">Id</dt><dd className="font-mono text-gray-700 truncate" title={t.id}>{t.id.slice(0, 8)}…</dd></div>
                                {t.estado === 'EN_COLA' && (
                                  <div className="col-span-2"><dt className="text-gray-400">Disponible desde</dt><dd className="text-gray-700">{fecha(t.disponibleDesde)}</dd></div>
                                )}
                              </dl>
                              {t.mensajeUsuario && (
                                <p className="mt-3 text-xs text-gray-700"><span className="font-semibold">Lo que vio el usuario: </span>{t.mensajeUsuario}</p>
                              )}
                              {t.errorTecnico && (
                                <pre className="mt-3 whitespace-pre-wrap break-all rounded-xl border border-red-100 bg-white p-3 text-[11px] leading-relaxed text-red-700 max-h-64 overflow-auto">{t.errorTecnico}</pre>
                              )}
                              {!t.mensajeUsuario && !t.errorTecnico && <p className="mt-3 text-xs text-gray-400">Sin errores registrados.</p>}
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {pagina && pagina.totalPages > 1 && (
              <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100 text-xs text-gray-500">
                <span>{pagina.totalElements} trabajo(s)</span>
                <div className="flex items-center gap-2">
                  <button type="button" disabled={pagina.first} onClick={() => setNumPagina(n => n - 1)}
                    className="px-3 py-1.5 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40">Anterior</button>
                  <span className="tabular-nums">Página {pagina.number + 1} de {pagina.totalPages}</span>
                  <button type="button" disabled={pagina.last} onClick={() => setNumPagina(n => n + 1)}
                    className="px-3 py-1.5 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40">Siguiente</button>
                </div>
              </div>
            )}
          </TarjetaSeccion>

          <p className="text-center text-xs text-gray-400">
            Los completados se borran solos a las 24 horas y los fallidos a los 30 días. Se actualiza cada {REFRESCO_MS / 1000} s mientras la pestaña está visible.
          </p>
        </div>
      )}

      {confirmarBorrado && (
        <ConfirmModal
          titulo="Borrar trabajos completados"
          danger
          confirmLabel="Borrar"
          onConfirmar={borrarCompletados}
          onCancelar={() => setConfirmarBorrado(false)}>
          <p className="text-sm text-gray-500">
            Se borrarán los trabajos completados y sus archivos temporales. Los de los últimos 10 minutos se conservan
            por si alguien los está descargando. Los que están en cola, procesando o fallidos no se tocan.
          </p>
        </ConfirmModal>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
