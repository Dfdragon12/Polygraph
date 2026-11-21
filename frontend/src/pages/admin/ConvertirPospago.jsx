import { useCallback, useEffect, useId, useState } from 'react'
import api from '../../services/api'
import { Modal } from '../../components/ui/Modal'

/**
 * TP-13 · «Convertir a pospago» (cliente prepago) y «Requisitos pospago» (cliente que ya es pospago, solo consulta).
 * Los documentos legales (RUT, Cámara de Comercio, documento del representante) NO se cargan aquí: se leen de «Documentos legales» y cumplen solo si
 * están aprobados allá; cada faltante tiene un enlace que abre esa ventana. Aquí se adjuntan soportes de empleados y procesos, estudio de crédito
 * y contrato, y se define cupo y día de corte. Qué falta, y si se puede aprobar, lo dice el servidor.
 */
const mensajeDe = (err, defecto) => err.response?.data?.mensaje ?? defecto

function Marca({ cumple }) {
  return cumple
    ? <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-xs font-bold" aria-label="Cumple">✓</span>
    : <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-red-100 text-red-700 text-xs font-bold" aria-label="Falta">✕</span>
}

function Archivo({ etiqueta, nombre, onElegir, onBajar, deshabilitado }) {
  const id = useId()
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      <label htmlFor={id} className={`cursor-pointer rounded-lg border border-gray-200 px-2.5 py-1 font-medium text-gray-700 hover:bg-gray-50 ${deshabilitado ? 'pointer-events-none opacity-50' : ''}`}>
        {nombre ? 'Reemplazar' : 'Adjuntar'} {etiqueta}
      </label>
      <input id={id} type="file" className="sr-only" disabled={deshabilitado} onChange={(e) => { const f = e.target.files?.[0]; if (f) onElegir(f); e.target.value = '' }} />
      {nombre ? (
        <button type="button" onClick={onBajar} className="text-primary-700 underline">{nombre}</button>
      ) : <span className="text-gray-500">Sin adjuntar</span>}
    </div>
  )
}

export default function ConvertirPospago({ cliente, onClose, onAbrirDocumentos, onAprobado }) {
  const idCampos = useId()
  const consulta = cliente.tipoCliente === 'POSPAGO'
  const [v, setV] = useState(null)
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [f, setF] = useState({ empleados: '', procesos: '', estudioFecha: '', estudioResultado: '', estudioPor: '', cupo: '', corte: '' })
  const [excepcion, setExcepcion] = useState(false)
  const [motivo, setMotivo] = useState('')

  const aplicar = useCallback((verif) => {
    setV(verif)
    const e = verif.expediente
    setF({
      empleados: e.empleadosDeclarados ?? '', procesos: e.procesosProyectados ?? '', estudioFecha: e.estudioCreditoFecha ?? '',
      estudioResultado: e.estudioCreditoResultado ?? '', estudioPor: e.estudioCreditoPor ?? '', cupo: e.cupoSolicitado ?? '', corte: e.diaCorte ?? '',
    })
  }, [])

  const cargar = useCallback(() => {
    setError(null)
    api.get(`/clientes/${cliente.idCliente}/pospago/requisitos`).then((r) => aplicar(r.data)).catch((e) => setError(mensajeDe(e, 'No se pudo cargar la lista de verificación.')))
  }, [cliente.idCliente, aplicar])
  useEffect(cargar, [cargar])

  const guardar = async () => {
    setOcupado(true); setError(null)
    try {
      const num = (x) => (x === '' || x == null ? null : Number(x))
      const r = await api.put(`/clientes/${cliente.idCliente}/pospago/expediente`, {
        empleadosDeclarados: num(f.empleados), procesosProyectados: num(f.procesos), estudioCreditoFecha: f.estudioFecha || null,
        estudioCreditoResultado: f.estudioResultado || null, estudioCreditoPor: f.estudioPor || null, cupoSolicitado: num(f.cupo), diaCorte: num(f.corte),
      })
      aplicar(r.data)
    } catch (e) { setError(mensajeDe(e, 'No se pudieron guardar los datos.')) } finally { setOcupado(false) }
  }

  const subir = async (tipo, archivo) => {
    setOcupado(true); setError(null)
    try {
      const fd = new FormData(); fd.append('archivo', archivo)
      const r = await api.post(`/clientes/${cliente.idCliente}/pospago/soportes/${tipo}`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
      setV(r.data)
    } catch (e) { setError(mensajeDe(e, 'No se pudo adjuntar el archivo.')) } finally { setOcupado(false) }
  }

  const bajar = async (tipo, nombre) => {
    try {
      const r = await api.get(`/clientes/${cliente.idCliente}/pospago/soportes/${tipo}`, { responseType: 'blob' })
      const url = URL.createObjectURL(r.data)
      const a = document.createElement('a'); a.href = url; a.download = nombre || tipo; a.click(); URL.revokeObjectURL(url)
    } catch { setError('No se pudo descargar el archivo.') }
  }

  const aprobar = async () => {
    setOcupado(true); setError(null)
    try {
      await api.post(`/clientes/${cliente.idCliente}/pospago/aprobar`, { excepcion, motivo })
      onAprobado('Pospago aprobado')
      onClose()
    } catch (e) { setError(mensajeDe(e, 'No se pudo aprobar.')) } finally { setOcupado(false) }
  }

  const e = v?.expediente
  const exceptuables = (v?.requisitos ?? []).filter((r) => !r.cumple && r.exceptuable)
  const necesitaExcepcion = v && !v.sinFaltantes && v.requiereExcepcion
  const puedeAprobar = v && !consulta && (v.sinFaltantes || (necesitaExcepcion && excepcion && motivo.trim().length > 0))
  const razonBloqueo = !v ? '' : v.sinFaltantes ? '' : !v.aprobable ? `No se puede aprobar: ${v.mensajeBloqueo}`
    : excepcion ? (motivo.trim() ? '' : 'Escribe el motivo de la excepción.') : `Falta: ${v.mensajeBloqueo}. Solo empleados y procesos admiten excepción.`
  const campo = 'w-full border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

  return (
    <Modal titulo={consulta ? 'Requisitos pospago' : 'Convertir a pospago'} subtitulo={v?.nombre ?? cliente.nombreDisplay} onClose={onClose} ancho="max-w-3xl">
      <div className="px-6 py-5 space-y-5">
        {error && <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
        {!v && !error && <p className="text-sm text-gray-500">Cargando…</p>}

        {v && (
          <>
            <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200">
              {v.requisitos.map((r) => (
                <li key={r.codigo} className="flex items-start gap-3 px-4 py-2.5">
                  <Marca cumple={r.cumple} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-gray-800">{r.titulo}{r.exceptuable && <span className="ml-2 text-[10px] font-semibold uppercase tracking-wide text-gray-500">admite excepción</span>}</p>
                    <p className={`text-xs ${r.cumple ? 'text-gray-600' : 'text-red-700'}`}>{r.detalle}</p>
                  </div>
                  {r.enlace === 'DOCUMENTOS' && (
                    <button type="button" onClick={() => onAbrirDocumentos(cliente)} className="shrink-0 text-xs font-semibold text-primary-700 underline">
                      Abrir Documentos legales
                    </button>
                  )}
                </li>
              ))}
            </ul>

            {!consulta && (
              <section className="space-y-4">
                <p className="text-xs font-semibold uppercase tracking-wider text-gray-600">Datos y soportes (los documentos legales se cargan en Documentos)</p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1.5">
                    <label htmlFor={`${idCampos}-e`} className="block text-xs font-medium text-gray-700">Empleados declarados (más de {e.minimoEmpleados})</label>
                    <input id={`${idCampos}-e`} type="number" min="0" className={campo} value={f.empleados} onChange={(x) => setF({ ...f, empleados: x.target.value })} />
                    <Archivo etiqueta="soporte (PILA o revisor fiscal)" nombre={e.soporteEmpleados} deshabilitado={ocupado}
                      onElegir={(a) => subir('EMPLEADOS', a)} onBajar={() => bajar('EMPLEADOS', e.soporteEmpleados)} />
                  </div>
                  <div className="space-y-1.5">
                    <label htmlFor={`${idCampos}-p`} className="block text-xs font-medium text-gray-700">Procesos proyectados al mes (más de {e.minimoProcesos})</label>
                    <input id={`${idCampos}-p`} type="number" min="0" className={campo} value={f.procesos} onChange={(x) => setF({ ...f, procesos: x.target.value })} />
                    <Archivo etiqueta="soporte (propuesta o proyección)" nombre={e.soporteProcesos} deshabilitado={ocupado}
                      onElegir={(a) => subir('PROCESOS', a)} onBajar={() => bajar('PROCESOS', e.soporteProcesos)} />
                  </div>
                </div>

                <fieldset className="rounded-xl border border-gray-200 p-3 space-y-2">
                  <legend className="px-1 text-xs font-semibold text-gray-700">Estudio de crédito</legend>
                  <Archivo etiqueta="estudio" nombre={e.estudioCreditoArchivo} deshabilitado={ocupado}
                    onElegir={(a) => subir('ESTUDIO_CREDITO', a)} onBajar={() => bajar('ESTUDIO_CREDITO', e.estudioCreditoArchivo)} />
                  <div className="grid gap-3 sm:grid-cols-3">
                    <div>
                      <label htmlFor={`${idCampos}-ef`} className="block text-xs font-medium text-gray-700 mb-1">Fecha</label>
                      <input id={`${idCampos}-ef`} type="date" className={campo} value={f.estudioFecha} onChange={(x) => setF({ ...f, estudioFecha: x.target.value })} />
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-er`} className="block text-xs font-medium text-gray-700 mb-1">Resultado</label>
                      <select id={`${idCampos}-er`} className={`${campo} bg-white`} value={f.estudioResultado} onChange={(x) => setF({ ...f, estudioResultado: x.target.value })}>
                        <option value="">Elige…</option><option value="APROBADO">Aprobado</option><option value="RECHAZADO">Rechazado</option>
                      </select>
                    </div>
                    <div>
                      <label htmlFor={`${idCampos}-ep`} className="block text-xs font-medium text-gray-700 mb-1">Hecho por</label>
                      <input id={`${idCampos}-ep`} className={campo} value={f.estudioPor} onChange={(x) => setF({ ...f, estudioPor: x.target.value })} />
                    </div>
                  </div>
                </fieldset>

                <div className="space-y-1.5">
                  <p className="text-xs font-medium text-gray-700">Contrato o propuesta aceptada</p>
                  <Archivo etiqueta="contrato" nombre={e.contratoArchivo} deshabilitado={ocupado}
                    onElegir={(a) => subir('CONTRATO', a)} onBajar={() => bajar('CONTRATO', e.contratoArchivo)} />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor={`${idCampos}-c`} className="block text-xs font-medium text-gray-700 mb-1">Cupo de crédito (COP)</label>
                    <input id={`${idCampos}-c`} type="number" min="0" step="1000" className={campo} value={f.cupo} onChange={(x) => setF({ ...f, cupo: x.target.value })} />
                  </div>
                  <div>
                    <label htmlFor={`${idCampos}-d`} className="block text-xs font-medium text-gray-700 mb-1">Día de corte de facturación (1 a 28)</label>
                    <input id={`${idCampos}-d`} type="number" min="1" max="28" className={campo} value={f.corte} onChange={(x) => setF({ ...f, corte: x.target.value })} />
                  </div>
                </div>
                <div className="flex justify-end">
                  <button type="button" onClick={guardar} disabled={ocupado}
                    className="border border-gray-300 text-gray-800 hover:bg-gray-50 disabled:opacity-60 text-sm font-medium px-4 py-2 rounded-lg">Guardar datos</button>
                </div>
              </section>
            )}

            {!consulta && necesitaExcepcion && (
              <section className="rounded-xl border border-amber-200 bg-amber-50 p-3 space-y-2">
                <label className="flex items-start gap-2 text-sm text-amber-900">
                  <input type="checkbox" checked={excepcion} onChange={(x) => setExcepcion(x.target.checked)} className="mt-0.5" />
                  <span>Aprobar como <strong>excepción</strong> (cliente estratégico): {exceptuables.map((x) => x.titulo).join(' y ')}. Solo el administrador principal.</span>
                </label>
                {excepcion && (
                  <div>
                    <label htmlFor={`${idCampos}-m`} className="block text-xs font-medium text-amber-900 mb-1">Motivo de la excepción (obligatorio)</label>
                    <textarea id={`${idCampos}-m`} rows={3} className={campo} value={motivo} onChange={(x) => setMotivo(x.target.value)} />
                  </div>
                )}
              </section>
            )}

            {consulta ? (
              <p className="text-xs text-gray-600">Este cliente ya es pospago. Aquí solo se consulta lo que le falta de la lista; su cupo no se modifica.</p>
            ) : (
              <div className="flex flex-col items-end gap-1.5">
                <button type="button" onClick={aprobar} disabled={!puedeAprobar || ocupado} aria-describedby={`${idCampos}-razon`}
                  className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-semibold px-5 py-2.5 rounded-lg">
                  {ocupado ? 'Procesando…' : 'Aprobar pospago'}
                </button>
                {razonBloqueo && <p id={`${idCampos}-razon`} className="max-w-xl text-right text-xs text-red-700">{razonBloqueo}</p>}
              </div>
            )}
          </>
        )}
      </div>
    </Modal>
  )
}
