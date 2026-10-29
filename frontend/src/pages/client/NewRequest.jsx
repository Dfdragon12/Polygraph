import { useId, useRef, useState, useEffect, useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import catalogoService from '../../services/catalogoService'
import solicitudService from '../../services/solicitudService'
import dashboardService from '../../services/dashboardService'
import pospagoService from '../../services/pospagoService'
import { useAuth } from '../../hooks/useAuth'
import CiudadSelect from '../../components/CiudadSelect'
import { ETIQUETA_NIVEL_CIUDAD } from '../../utils/recargoCiudad'
import { validarEvaluado, rotuloCiudad, CRITICIDADES } from '../../utils/validarEvaluado'
import FormatoAdicional, { validarFormato } from '../../components/solicitud/FormatoAdicional'
import TemasPoligrafia, { validarTemas, MAX_TEMAS } from '../../components/solicitud/TemasPoligrafia'
import PanelLateral from '../../components/solicitud/PanelLateral'
import SolicitudCliente from '../../components/SolicitudCliente'

function formatearPrecio(valor) {
  if (!valor) return 'Precio a convenir'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0 }).format(valor)
}

// Primero se elige el servicio: de él depende qué más se pide del evaluado (formato de poligrafía, temas).
const PASOS = [
  { titulo: 'Servicio', subtitulo: 'Qué vas a solicitar' },
  { titulo: 'Evaluado', subtitulo: 'Datos de la persona' },
  { titulo: 'Confirmación', subtitulo: 'Revisa y envía' },
]

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none'
const MAX_SUBPROCESOS_VISIBLES = 4

/* ─── Piezas de presentación ─── */

// Tarjeta de una sección con número y título (pasos 2 y 3)
function Seccion({ numero, titulo, descripcion, onEditar, children }) {
  return (
    <section className="bg-white rounded-xl border border-gray-200 shadow-sm">
      <header className="flex items-start justify-between gap-3 px-6 py-4 border-b border-gray-100">
        <div className="flex items-start gap-3 min-w-0">
          {numero != null && (
            <span className="mt-0.5 flex-shrink-0 w-6 h-6 rounded-full bg-gray-900 text-white text-xs font-semibold flex items-center justify-center" aria-hidden="true">{numero}</span>
          )}
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-gray-900">{titulo}</h2>
            {descripcion && <p className="text-xs text-gray-600 mt-0.5">{descripcion}</p>}
          </div>
        </div>
        {onEditar && (
          <button type="button" onClick={onEditar} aria-label={`Editar ${titulo}`}
            className="flex-shrink-0 text-xs font-medium text-primary-700 hover:text-primary-900 hover:underline">Editar</button>
        )}
      </header>
      <div className="px-6 py-5">{children}</div>
    </section>
  )
}

function Dato({ etiqueta, valor }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] font-medium text-gray-500">{etiqueta}</dt>
      <dd className={`mt-0.5 text-sm break-words ${valor ? 'font-medium text-gray-900' : 'text-gray-400'}`}>{valor || 'No indicado'}</dd>
    </div>
  )
}

function Campo({ id, etiqueta, obligatorio, error, children }) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">
        {etiqueta}{obligatorio && <span className="text-red-600"> *</span>}
      </label>
      {children}
      {error && <p className="mt-1 text-[11px] font-medium text-red-600">{typeof error === 'string' ? error : 'Este campo es obligatorio.'}</p>}
    </div>
  )
}

function Lupa() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 10.5a6.5 6.5 0 11-13 0 6.5 6.5 0 0113 0z" />
    </svg>
  )
}

// Costo (NS-11): lo calcula el servidor (ResumenCobroService) con las mismas reglas con las que luego descuenta; aquí solo se muestra.
function BloqueCosto({ proceso, ciudad, resumen, cargando }) {
  if (!proceso) return null
  const linkCompra = `/cliente/comprar-servicios?idProceso=${proceso.idProceso}`
  if (!resumen) return <p className="text-sm text-gray-500">{cargando ? 'Calculando…' : 'No se pudo calcular el costo. Vuelve a intentarlo.'}</p>
  const esPos = resumen.tipoCliente === 'POSPAGO'
  const recargo = Number(resumen.recargoCiudad ?? 0)
  const tono = resumen.alcanza ? (esPos ? 'border-sky-200 bg-sky-50 text-sky-900' : 'border-green-200 bg-green-50 text-green-900') : 'border-amber-200 bg-amber-50 text-amber-900'

  return (
    <div className="space-y-3">
      {esPos ? (
        <dl className="divide-y divide-gray-100 rounded-lg border border-gray-200 text-sm">
          <div className="flex items-center justify-between px-4 py-2.5"><dt className="text-gray-700">Valor del servicio</dt><dd className="font-medium text-gray-900">{formatearPrecio(resumen.valor)}</dd></div>
          {recargo > 0 && (
            <div className="flex items-center justify-between px-4 py-2.5 bg-sky-50/60">
              <dt className="text-sky-900">Recargo por ciudad{ciudad ? ` — ${ciudad.nombreCiudad} (${ETIQUETA_NIVEL_CIUDAD[ciudad.nivelCiudad]})` : ''}</dt>
              <dd className="font-medium text-sky-900">+ {formatearPrecio(recargo)}</dd>
            </div>
          )}
          <div className="flex items-center justify-between px-4 py-2.5"><dt className="text-gray-700">Cupo disponible</dt><dd className="font-medium text-gray-900">{formatearPrecio(resumen.cupoDisponible)}</dd></div>
          <div className="flex items-center justify-between px-4 py-2.5"><dt className="text-gray-700">Quedará</dt><dd className={`font-semibold ${resumen.alcanza ? 'text-gray-900' : 'text-red-700'}`}>{resumen.alcanza ? formatearPrecio(resumen.cupoQuedara) : 'No alcanza'}</dd></div>
        </dl>
      ) : (
        <dl className="divide-y divide-gray-100 rounded-lg border border-gray-200 text-sm">
          <div className="flex items-center justify-between px-4 py-2.5"><dt className="text-gray-700">Se usará de tu bolsa</dt><dd className="font-medium text-gray-900">1 {resumen.servicio}</dd></div>
          <div className="flex items-center justify-between px-4 py-2.5"><dt className="text-gray-700">Disponibles ahora</dt><dd className="font-medium text-gray-900">{resumen.disponiblesAhora}</dd></div>
          <div className="flex items-center justify-between px-4 py-2.5"><dt className="text-gray-700">Quedarán</dt><dd className="font-semibold text-gray-900">{resumen.quedaran}</dd></div>
        </dl>
      )}
      <p className={`rounded-lg border px-4 py-3 text-sm ${tono}`}>
        {resumen.mensaje}
        {!esPos && !resumen.alcanza && <>{' '}<a href={linkCompra} className="font-semibold underline">Cómpralo en Comprar Servicios</a> antes de enviar la solicitud.</>}
      </p>
      {!esPos && recargo > 0 && <p className="text-xs text-gray-600">El recargo por ciudad ({formatearPrecio(recargo)}) no se cobra ahora: se suma automáticamente a tu próxima compra en Comprar Servicios.</p>}
    </div>
  )
}

function NewRequest() {
  const idCampos = useId()
  const navigate = useNavigate()
  const { usuario } = useAuth()
  const esPospago = usuario?.tipoCliente === 'POSPAGO'
  const [paso, setPaso] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const claveEnvio = useRef(crypto.randomUUID())   // una llave por pantalla: un doble clic o un reintento no cobra dos veces
  const [error, setError] = useState(null)
  const [mora, setMora] = useState(null)   // NS-05: la misma regla y el mismo texto con los que el servidor rechaza el envío
  const [catalogo, setCatalogo] = useState([])
  const [saldos, setSaldos] = useState([])
  const [cargandoCatalogo, setCargandoCatalogo] = useState(true)

  const [evaluado, setEvaluado] = useState({
    cedula: '', nombres: '', apellidos: '', celular: '', email: '', idCiudad: null, cargo: '', criticidadCargo: '', direccionVisita: '',
  })
  const [ciudades, setCiudades] = useState([])
  const [serviciosSeleccionados, setServiciosSeleccionados] = useState([])
  const [notas, setNotas] = useState('')
  const [clasificacionActiva, setClasificacionActiva] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [subprocesosPorProceso, setSubprocesosPorProceso] = useState({})
  const [descripcionesExpandidas, setDescripcionesExpandidas] = useState({})
  const [infoSubproceso, setInfoSubproceso] = useState(null)
  // Poligrafías: formato adicional del servicio elegido y temas a evaluar (hasta 4)
  const [formatos, setFormatos] = useState([])
  const [temasCatalogo, setTemasCatalogo] = useState([])
  const [datosAdicionales, setDatosAdicionales] = useState({})
  const [temasElegidos, setTemasElegidos] = useState([])
  const [intentoAvanzar, setIntentoAvanzar] = useState(false)   // resalta en rojo lo obligatorio que falta

  useEffect(() => { solicitudService.mora().then(setMora).catch(() => {}) }, [])

  // NS-10: el cupo sale del mismo estado de cuenta que ve Mi Crédito, así el encabezado nunca difiere.
  const [cupo, setCupo] = useState(null)   // { limiteCredito, creditoDisponible } | { error }
  useEffect(() => {
    if (!esPospago) return
    pospagoService.obtenerEstadoCuenta().then(setCupo)
      .catch((e) => setCupo({ error: e.response?.data?.mensaje || 'No se pudo consultar tu cupo de crédito.' }))
  }, [esPospago])

  useEffect(() => {
    Promise.allSettled([
      catalogoService.listarServicios().then((data) => setCatalogo(data)),
      dashboardService.obtenerBolsaServicios().then((data) => setSaldos(data)),
      catalogoService.listarCiudades().then((data) => setCiudades(data)),
      solicitudService.formatos().then((data) => setFormatos(data)),
      solicitudService.temasPoligrafia().then((data) => setTemasCatalogo(data)),
    ]).finally(() => setCargandoCatalogo(false))
  }, [])

  // Prepago (NS-09): una tarjeta por cada servicio que el cliente haya comprado alguna vez, con su contador. Los agotados se ven pero no se eligen.
  // Pospago: acceso directo por cupo de crédito, sin restricción de saldo.
  // NS-10: pospago ve el precio de cada servicio; si supera el cupo disponible (o no tiene precio) la tarjeta lo dice y no se puede elegir.
  const catalogoDisponible = useMemo(() => {
    if (esPospago) {
      const disponible = cupo?.creditoDisponible
      return catalogo.map((s) => ({
        ...s,
        bloqueo: !s.valor ? 'SIN_PRECIO' : (cupo && (disponible == null || Number(s.valor) > Number(disponible)) ? 'SIN_CUPO' : null),
      }))
    }
    return catalogo.flatMap((s) => {
      const sd = saldos.find((x) => x.idProceso === s.idProceso)
      if (!sd || !(sd.cantidadComprada > 0)) return []
      const disponibles = sd.cantidadDisponible ?? 0
      return [{ ...s, disponibles, comprados: sd.cantidadComprada, consumidos: sd.cantidadConsumida ?? 0, bloqueo: disponibles < 1 ? 'AGOTADO' : null }]
    })
  }, [catalogo, saldos, esPospago, cupo])
  // Mismo cálculo de la bolsa que el resto de la plataforma (BolsaPrepagoService): informativo, el servidor vuelve a validar al enviar.
  const totalDisponible = useMemo(() => saldos.reduce((t, sd) => t + (sd.cantidadDisponible ?? 0), 0), [saldos])

  const procesoElegido = catalogo.find((s) => s.idProceso === serviciosSeleccionados[0])
  const formatoDelServicio = formatos.find((f) => f.formato === procesoElegido?.formatoSolicitud) ?? null
  const admiteTemas = !!procesoElegido?.admitePreguntasPoligrafia && temasCatalogo.length > 0

  const pideDireccion = !!procesoElegido?.requiereDireccion   // NS-08: el servicio incluye una visita
  const rotulo = rotuloCiudad(procesoElegido)
  const erroresEvaluado = validarEvaluado(evaluado, pideDireccion)
  const faltaEnDatos = () => {
    if (Object.keys(erroresEvaluado).length) return 'Revisa los datos marcados en rojo.'
    return validarFormato(formatoDelServicio, datosAdicionales) ?? (admiteTemas ? validarTemas(temasCatalogo, temasElegidos) : null)
  }

  const camposValidos = () => {
    if (paso === 0) return serviciosSeleccionados.length > 0
    if (paso === 1) return !faltaEnDatos()
    return true
  }

  const siguiente = () => {
    if (!camposValidos()) { setIntentoAvanzar(true); return }
    setIntentoAvanzar(false)
    setPaso((p) => p + 1)
  }

  const irAPaso = (n) => { setIntentoAvanzar(false); setPaso(n) }

  // Solo se puede elegir un servicio en total, sin importar la clasificación.
  // Al cambiar de servicio se parte de cero con lo extra de la poligrafía: cada formato pide cosas distintas.
  const toggleServicio = (id) => {
    if (catalogoDisponible.find((s) => s.idProceso === id)?.bloqueo) return   // agotado, sin cupo o sin precio: no se elige
    setDatosAdicionales({})
    setTemasElegidos([])
    setServiciosSeleccionados((prev) => (prev.includes(id) ? [] : [id]))
  }

  const enviar = async () => {
    setEnviando(true)
    setError(null)
    try {
      await solicitudService.crear({
        ...evaluado, procesosIds: serviciosSeleccionados, notas,
        datosAdicionales: formatoDelServicio ? datosAdicionales : undefined,
        temasPoligrafia: admiteTemas ? temasElegidos : undefined,
      }, claveEnvio.current)
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

  // Con texto en el buscador se busca en todo el catálogo; sin texto, en la clasificación activa
  const serviciosVisibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    if (q) return catalogoDisponible.filter((s) => `${s.nombreProceso} ${s.descripcion ?? ''} ${s.clasificacion ?? ''}`.toLowerCase().includes(q))
    return catalogoDisponible.filter((s) => s.clasificacion === clasificacionActiva)
  }, [catalogoDisponible, clasificacionActiva, busqueda])

  useEffect(() => {
    const pendientes = serviciosVisibles.filter((s) => !(s.idProceso in subprocesosPorProceso))
    if (!pendientes.length) return
    Promise.all(pendientes.map((s) =>
      catalogoService.obtenerSubprocesos(s.idProceso)
        .then((data) => [s.idProceso, data])
        .catch(() => [s.idProceso, []])
    )).then((entries) => {
      setSubprocesosPorProceso((prev) => ({ ...prev, ...Object.fromEntries(entries) }))
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serviciosVisibles])

  // NS-06: la fecha estimada la calcula el servidor con el mismo cálculo con el que crea la solicitud (días hábiles, festivos, hora de corte y hora de Colombia).
  // Se vuelve a pedir al elegir otro servicio y al llegar a la confirmación, por si entre tanto pasó la hora de corte.
  const [entrega, setEntrega] = useState(null)
  const enConfirmacion = paso === 2
  useEffect(() => {
    if (!serviciosSeleccionados.length) { setEntrega(null); return }
    let vigente = true
    solicitudService.entregaEstimada(serviciosSeleccionados).then((r) => { if (vigente) setEntrega(r) }).catch(() => { if (vigente) setEntrega(null) })
    return () => { vigente = false }
  }, [serviciosSeleccionados, enConfirmacion])

  // NS-11: qué se usa y qué queda (bolsa o cupo), calculado en el servidor; se vuelve a pedir al entrar a la confirmación y si cambia el servicio o la ciudad.
  const [resumenCobro, setResumenCobro] = useState(null)
  const [cargandoResumen, setCargandoResumen] = useState(false)
  useEffect(() => {
    if (!enConfirmacion || !serviciosSeleccionados.length) return
    let vigente = true
    setCargandoResumen(true)
    solicitudService.resumenCobro(serviciosSeleccionados[0], evaluado.idCiudad)
      .then((r) => { if (vigente) setResumenCobro(r) }).catch(() => { if (vigente) setResumenCobro(null) })
      .finally(() => { if (vigente) setCargandoResumen(false) })
    return () => { vigente = false }
  }, [enConfirmacion, serviciosSeleccionados, evaluado.idCiudad])
  const ciudadSeleccionada = ciudades.find((c) => c.idCiudad === evaluado.idCiudad)

  // Lo extra de la poligrafía tal como lo verá el poligrafista (misma pieza que la ficha del candidato)
  const resumenExtra = {
    titulo: formatoDelServicio?.titulo,
    datos: (formatoDelServicio?.campos ?? []).filter((c) => (datosAdicionales[c.clave] ?? '').trim())
      .map((c) => ({ etiqueta: c.etiqueta, valor: datosAdicionales[c.clave].trim() })),
    temas: temasElegidos.map((e) => ({ nombre: temasCatalogo.find((t) => t.id === e.idPregunta)?.nombre ?? '', detalle: e.detalle?.trim() || null })),
  }

  // Panel lateral: qué hay que cargar y cuánto va
  const itemsPanel = useMemo(() => {
    if (!procesoElegido) return []
    const TOTAL_EVALUADO = pideDireccion ? 9 : 8
    const basicos = TOTAL_EVALUADO - Object.keys(validarEvaluado(evaluado, pideDireccion)).length
    const lista = [
      { clave: 'evaluado', titulo: 'Datos del evaluado', detalle: `Cédula, nombres, apellidos, cargo, criticidad, celular, correo, ciudad${pideDireccion ? ' y dirección de la visita' : ''} (${basicos} de ${TOTAL_EVALUADO}).`, estado: basicos === TOTAL_EVALUADO ? 'listo' : 'pendiente' },
    ]
    if (formatoDelServicio) {
      const obligatorios = formatoDelServicio.campos.filter((c) => c.obligatorio)
      const hechos = obligatorios.filter((c) => (datosAdicionales[c.clave] ?? '').trim()).length
      lista.push({
        clave: 'formato', titulo: formatoDelServicio.titulo,
        detalle: `${hechos} de ${obligatorios.length} obligatorios${formatoDelServicio.campos.length > obligatorios.length ? ` · ${formatoDelServicio.campos.length - obligatorios.length} opcionales` : ''}`,
        estado: hechos === obligatorios.length ? 'listo' : 'pendiente',
      })
    }
    if (admiteTemas) {
      lista.push({
        clave: 'temas', titulo: 'Temas a evaluar',
        detalle: `${temasElegidos.length} de ${MAX_TEMAS} elegidos`,
        estado: temasElegidos.length > 0 && !validarTemas(temasCatalogo, temasElegidos) ? 'listo' : 'opcional',
      })
    }
    return lista
  }, [procesoElegido, evaluado, formatoDelServicio, datosAdicionales, admiteTemas, temasElegidos, temasCatalogo])

  const estadoPago = useMemo(() => {
    if (!procesoElegido) return null
    if (esPospago) return { texto: 'Crédito pospago: se factura según tu ciclo de facturación.' }
    const saldo = saldos.find((s) => s.idProceso === procesoElegido.idProceso)
    if (saldo?.cantidadDisponible > 0) return { texto: `Se descuenta 1 de ${saldo.cantidadDisponible} disponible${saldo.cantidadDisponible !== 1 ? 's' : ''} en tu bolsa prepago.` }
    return { texto: 'Sin saldo disponible en tu bolsa prepago.', tono: 'alerta' }
  }, [procesoElegido, esPospago, saldos])


  const errorDe = (k) => (intentoAvanzar ? erroresEvaluado[k] : undefined)
  const claseObligatorio = (k) => (errorDe(k) ? `${inputCls} border-red-500` : inputCls)

  return (
    <div className="max-w-6xl mx-auto py-8 px-4">
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Nueva solicitud de servicio</h1>
        <p className="text-sm text-gray-600 mt-1">Elige el servicio, completa los datos del evaluado y confirma. Te mostramos en todo momento qué falta.</p>
      </header>
      {mora?.enMora && (
        <div role="alert" className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-sm text-red-800">
          <p className="font-semibold">No puedes enviar solicitudes por ahora</p>
          <p className="mt-1">{mora.mensaje}</p>
        </div>
      )}


      {/* Pasos */}
      <nav aria-label="Pasos de la solicitud" className="mb-8">
        <ol className="grid grid-cols-3 gap-2 sm:gap-3">
          {PASOS.map((p, i) => {
            const hecho = i < paso
            const actual = i === paso
            const contenido = (
              <>
                <span className={`flex-shrink-0 w-7 h-7 rounded-full text-xs font-semibold flex items-center justify-center ${
                  hecho ? 'bg-green-600 text-white' : actual ? 'bg-primary-600 text-white' : 'bg-gray-200 text-gray-700'
                }`}>
                  {hecho ? (
                    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3.5} aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" /></svg>
                  ) : i + 1}
                </span>
                <span className="min-w-0 text-left">
                  <span className={`block text-sm font-semibold truncate ${actual ? 'text-gray-900' : 'text-gray-700'}`}>{p.titulo}</span>
                  <span className="hidden sm:block text-xs text-gray-600 truncate">{p.subtitulo}</span>
                </span>
              </>
            )
            const base = `flex items-center gap-2.5 rounded-xl border px-3 py-2.5 ${actual ? 'border-primary-300 bg-white shadow-sm' : 'border-gray-200 bg-white/60'}`
            return (
              <li key={p.titulo}>
                {hecho ? (
                  <button type="button" onClick={() => irAPaso(i)} className={`${base} w-full hover:border-gray-300`} aria-label={`Volver al paso ${i + 1}: ${p.titulo}`}>{contenido}</button>
                ) : (
                  <div className={base} aria-current={actual ? 'step' : undefined}>{contenido}</div>
                )}
              </li>
            )
          })}
        </ol>
      </nav>

      <div className={paso < 2 ? 'grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] items-start' : ''}>
        <div className="space-y-6 min-w-0">

          {/* Paso 1: Servicio */}
          {paso === 0 && (
            <Seccion titulo="Elige el servicio" descripcion="Solo puedes elegir un servicio por solicitud.">
              {!esPospago && !cargandoCatalogo && (
                <p className="mb-4 rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white" role="status">
                  Bolsa prepago: {totalDisponible} servicio{totalDisponible !== 1 ? 's' : ''} disponible{totalDisponible !== 1 ? 's' : ''}
                </p>
              )}
              {esPospago && !cargandoCatalogo && cupo && (
                cupo.error ? (
                  <p className="mb-4 rounded-lg bg-amber-50 border border-amber-200 px-4 py-2.5 text-sm text-amber-900" role="alert">{cupo.error}</p>
                ) : (
                  <p className="mb-4 rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-semibold text-white" role="status">
                    Cupo de crédito: {formatearPrecio(cupo.creditoDisponible ?? 0)} de {formatearPrecio(cupo.limiteCredito ?? 0)}
                  </p>
                )
              )}
              {cargandoCatalogo ? (
                <div className="flex items-center justify-center h-48">
                  <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
                </div>
              ) : catalogoDisponible.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-14 gap-2 text-center">
                  <p className="text-sm font-medium text-gray-800">Aún no has comprado servicios en tu bolsa prepago</p>
                  <p className="text-xs text-gray-600">Compra el servicio que necesitas y vuelve aquí para solicitarlo.</p>
                  <a href="/cliente/comprar-servicios" className="mt-2 text-sm font-semibold text-primary-700 hover:underline">Ir a Comprar Servicios →</a>
                </div>
              ) : (
                <>
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                    <div className="relative sm:w-64">
                      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-500"><Lupa /></span>
                      <input type="search" aria-label="Buscar servicio" placeholder="Buscar servicio…" value={busqueda}
                        onChange={(e) => setBusqueda(e.target.value)} className={`${inputCls} pl-9`} />
                    </div>
                    {!busqueda && (
                      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Clasificación">
                        {categorias.map((cat) => {
                          const activa = clasificacionActiva === cat
                          const hayElegido = catalogoDisponible.some((s) => s.clasificacion === cat && serviciosSeleccionados.includes(s.idProceso))
                          return (
                            <button key={cat} type="button" onClick={() => setClasificacionActiva(cat)} aria-pressed={activa}
                              className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
                                activa ? 'bg-gray-900 border-gray-900 text-white' : 'bg-white border-gray-300 text-gray-700 hover:border-gray-400'
                              }`}>
                              {cat}{hayElegido && <span className="ml-1.5 inline-block w-1.5 h-1.5 rounded-full bg-primary-500 align-middle" aria-label="con servicio elegido" />}
                            </button>
                          )
                        })}
                      </div>
                    )}
                  </div>

                  {serviciosVisibles.length === 0 ? (
                    <p className="text-sm text-gray-600 text-center py-12">Ningún servicio coincide con «{busqueda}».</p>
                  ) : (
                    <div className="mt-5 grid grid-cols-1 md:grid-cols-2 gap-4">
                      {serviciosVisibles.map((s) => {
                        const seleccionado = serviciosSeleccionados.includes(s.idProceso)
                        const agotado = s.bloqueo === 'AGOTADO'   // NS-09: se ve, pero no se elige
                        const bloqueado = !!s.bloqueo
                        const subprocesos = subprocesosPorProceso[s.idProceso]
                        const expandida = !!descripcionesExpandidas[s.idProceso]
                        const descripcionLarga = (s.descripcion?.length ?? 0) > 110
                        return (
                          <div key={s.idProceso} onClick={() => toggleServicio(s.idProceso)}
                            className={`flex flex-col rounded-xl border p-5 transition-all ${
                              bloqueado ? 'border-gray-200 bg-gray-50 cursor-not-allowed'
                                : seleccionado ? 'border-primary-500 ring-2 ring-primary-100 bg-primary-50/40 cursor-pointer'
                                  : 'border-gray-200 hover:border-gray-300 hover:shadow-sm bg-white cursor-pointer'
                            }`}>
                            <div className="flex items-start gap-3">
                              <span className={`mt-0.5 flex-shrink-0 w-5 h-5 rounded-full border-2 flex items-center justify-center ${seleccionado ? 'border-primary-600 bg-primary-600' : 'border-gray-300'}`} aria-hidden="true">
                                {seleccionado && <svg className="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3.5}><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" /></svg>}
                              </span>
                              <div className="min-w-0 flex-1">
                                <button type="button" aria-pressed={seleccionado} disabled={bloqueado} onClick={(e) => { e.stopPropagation(); toggleServicio(s.idProceso) }}
                                  className="text-left text-base font-semibold text-gray-900 focus:outline-none focus-visible:underline disabled:text-gray-500">
                                  {s.nombreProceso}
                                </button>
                                {s.descripcion && (
                                  <p className={`text-sm text-gray-600 mt-1 ${expandida ? '' : 'line-clamp-2'}`}>{s.descripcion}</p>
                                )}
                                {s.descripcion && descripcionLarga && (
                                  <button type="button"
                                    onClick={(e) => { e.stopPropagation(); setDescripcionesExpandidas((prev) => ({ ...prev, [s.idProceso]: !expandida })) }}
                                    className="text-xs font-medium mt-1 text-primary-700 hover:underline">
                                    {expandida ? 'Ver menos' : 'Ver más'}
                                  </button>
                                )}
                              </div>
                            </div>

                            <div className="mt-3 flex flex-wrap items-center gap-1.5">
                              {esPospago ? (
                                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-gray-900 text-white">{s.valor ? formatearPrecio(s.valor) : 'Sin precio'}</span>
                              ) : agotado ? (
                                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-red-100 text-red-800">Agotado</span>
                              ) : (
                                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-gray-900 text-white">{s.disponibles} disponible{s.disponibles !== 1 ? 's' : ''}</span>
                              )}
                              {!esPospago && <span className="text-xs text-gray-600">Usados {s.consumidos} de {s.comprados}</span>}
                              <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">{s.diasHabilesEntrega || 5} días hábiles</span>
                              {s.admitePreguntasPoligrafia && <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-primary-100 text-primary-800">Poligrafía</span>}
                            </div>

                            <div className="mt-4 pt-3 border-t border-gray-100">
                              <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wide mb-2">Incluye</p>
                              {!subprocesos ? (
                                <div className="flex items-center gap-2 py-1">
                                  <div className="animate-spin rounded-full h-3.5 w-3.5 border-2 border-primary-300 border-t-transparent" />
                                  <span className="text-xs text-gray-600">Cargando…</span>
                                </div>
                              ) : subprocesos.length === 0 ? (
                                <p className="text-xs text-gray-600">Sin subprocesos configurados.</p>
                              ) : (
                                <ul className="flex flex-wrap gap-1.5">
                                  {(expandida ? subprocesos : subprocesos.slice(0, MAX_SUBPROCESOS_VISIBLES)).map((sp, i) => (
                                    <li key={i}>
                                      {sp.descripcion ? (
                                        <button type="button" onClick={(e) => { e.stopPropagation(); setInfoSubproceso(sp) }}
                                          title="Ver información" className="text-xs px-2 py-0.5 rounded-full border border-gray-300 text-gray-700 hover:border-primary-400 hover:text-primary-800">
                                          {sp.nombreProgreso}
                                        </button>
                                      ) : (
                                        <span className="text-xs px-2 py-0.5 rounded-full border border-gray-200 text-gray-700">{sp.nombreProgreso}</span>
                                      )}
                                    </li>
                                  ))}
                                  {!expandida && subprocesos.length > MAX_SUBPROCESOS_VISIBLES && (
                                    <li>
                                      <button type="button" onClick={(e) => { e.stopPropagation(); setDescripcionesExpandidas((prev) => ({ ...prev, [s.idProceso]: true })) }}
                                        className="text-xs px-2 py-0.5 text-primary-700 hover:underline">
                                        +{subprocesos.length - MAX_SUBPROCESOS_VISIBLES} más
                                      </button>
                                    </li>
                                  )}
                                </ul>
                              )}
                            </div>
                            {esPospago && bloqueado && (
                              <p className="mt-3 text-xs font-medium text-amber-800">
                                {s.bloqueo === 'SIN_PRECIO'
                                  ? 'Este servicio no tiene precio para tu cuenta: contacta a tu gestor.'
                                  : 'Supera tu cupo disponible: no se puede solicitar. Contacta a tu gestor para ampliarlo.'}
                              </p>
                            )}
                            {agotado && (
                              <div className="mt-4">
                                <Link to={`/cliente/comprar-servicios?idProceso=${s.idProceso}`} onClick={(e) => e.stopPropagation()}
                                  className="inline-flex items-center rounded-lg bg-primary-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-primary-700">
                                  Comprar más
                                </Link>
                              </div>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  )}
                  {serviciosSeleccionados.length === 0 && (
                    <p className="text-sm text-gray-600 mt-5">Selecciona un servicio para continuar.</p>
                  )}
                  {!esPospago && (
                    <p className="mt-5 text-sm text-gray-700">
                      ¿Necesitas otro servicio?{' '}
                      <Link to="/cliente/comprar-servicios" className="font-semibold text-primary-700 hover:underline">Ver catálogo completo y comprar</Link>
                    </p>
                  )}
                </>
              )}
            </Seccion>
          )}

          {/* Paso 2: Datos del evaluado, formato y temas */}
          {paso === 1 && (
            <>
              <Seccion numero={1} titulo="Identificación" descripcion="Los campos con * son obligatorios.">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Campo id={`${idCampos}-1`} etiqueta="Cédula" obligatorio error={errorDe('cedula')}>
                    <input id={`${idCampos}-1`} inputMode="numeric" className={claseObligatorio('cedula')} aria-invalid={!!errorDe('cedula') || undefined}
                      value={evaluado.cedula} onChange={(e) => setEvaluado({ ...evaluado, cedula: e.target.value })} />
                  </Campo>
                  <Campo id={`${idCampos}-2`} etiqueta="Cargo" obligatorio error={errorDe('cargo')}>
                    <input id={`${idCampos}-2`} className={claseObligatorio('cargo')} aria-invalid={!!errorDe('cargo') || undefined} value={evaluado.cargo}
                      onChange={(e) => setEvaluado({ ...evaluado, cargo: e.target.value })} />
                  </Campo>
                  <Campo id={`${idCampos}-3`} etiqueta="Nombres" obligatorio error={errorDe('nombres')}>
                    <input id={`${idCampos}-3`} className={claseObligatorio('nombres')} aria-invalid={!!errorDe('nombres') || undefined}
                      value={evaluado.nombres} onChange={(e) => setEvaluado({ ...evaluado, nombres: e.target.value })} />
                  </Campo>
                  <Campo id={`${idCampos}-4`} etiqueta="Apellidos" obligatorio error={errorDe('apellidos')}>
                    <input id={`${idCampos}-4`} className={claseObligatorio('apellidos')} aria-invalid={!!errorDe('apellidos') || undefined}
                      value={evaluado.apellidos} onChange={(e) => setEvaluado({ ...evaluado, apellidos: e.target.value })} />
                  </Campo>
                  <div className="sm:col-span-2">
                    <Campo id={`${idCampos}-8`} etiqueta="Criticidad del cargo" obligatorio error={errorDe('criticidadCargo')}>
                      <select id={`${idCampos}-8`} className={claseObligatorio('criticidadCargo')} aria-invalid={!!errorDe('criticidadCargo') || undefined}
                        value={evaluado.criticidadCargo} onChange={(e) => setEvaluado({ ...evaluado, criticidadCargo: e.target.value })}>
                        <option value="">Elige la criticidad…</option>
                        {CRITICIDADES.map((c) => <option key={c.valor} value={c.valor}>{c.titulo} · {c.detalle}</option>)}
                      </select>
                    </Campo>
                    <p className="mt-1 text-[11px] text-gray-600">Define qué tan exigente es la calificación de riesgo del estudio. Solo el gestor puede cambiarla después.</p>
                  </div>
                </div>
              </Seccion>

              <Seccion numero={2} titulo="Contacto y ubicación" descripcion="El candidato verá estos datos ya cargados en su portal y solo tendrá que confirmarlos o actualizarlos.">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <Campo id={`${idCampos}-5`} etiqueta="Celular" obligatorio error={errorDe('celular')}>
                    <input id={`${idCampos}-5`} type="tel" inputMode="tel" maxLength={10} className={claseObligatorio('celular')} aria-invalid={!!errorDe('celular') || undefined}
                      value={evaluado.celular} onChange={(e) => setEvaluado({ ...evaluado, celular: e.target.value })} />
                  </Campo>
                  <Campo id={`${idCampos}-6`} etiqueta="Correo electrónico" obligatorio error={errorDe('email')}>
                    <input id={`${idCampos}-6`} type="email" className={claseObligatorio('email')} aria-invalid={!!errorDe('email') || undefined} value={evaluado.email}
                      onChange={(e) => setEvaluado({ ...evaluado, email: e.target.value })} />
                  </Campo>
                  <div className="sm:col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-1">{rotulo}<span className="text-red-600"> *</span></label>
                    <CiudadSelect value={evaluado.idCiudad} soloConfirmadas
                      onChange={(idCiudad) => setEvaluado({ ...evaluado, idCiudad })} className={errorDe('idCiudad') ? `${inputCls} border-red-500` : inputCls} />
                    {errorDe('idCiudad') && <p className="mt-1 text-[11px] font-medium text-red-600">{errorDe('idCiudad')}</p>}
                  </div>
                  {pideDireccion && (
                    <div className="sm:col-span-2">
                      <Campo id={`${idCampos}-9`} etiqueta="Dirección de la visita" obligatorio error={errorDe('direccionVisita')}>
                        <input id={`${idCampos}-9`} className={claseObligatorio('direccionVisita')} aria-invalid={!!errorDe('direccionVisita') || undefined}
                          placeholder="Calle o carrera, número, barrio y detalles (torre, apartamento)" value={evaluado.direccionVisita}
                          onChange={(e) => setEvaluado({ ...evaluado, direccionVisita: e.target.value })} />
                      </Campo>
                    </div>
                  )}
                </div>
              </Seccion>

              <FormatoAdicional formato={formatoDelServicio} datos={datosAdicionales} resaltar={intentoAvanzar}
                onChange={(clave, valor) => setDatosAdicionales((d) => ({ ...d, [clave]: valor }))} />
              {admiteTemas && <TemasPoligrafia temas={temasCatalogo} elegidos={temasElegidos} onChange={setTemasElegidos} numero={formatoDelServicio ? 4 : 3} />}

              {intentoAvanzar && faltaEnDatos() && (
                <p className="text-sm font-medium text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-3" role="alert">{faltaEnDatos()}</p>
              )}
            </>
          )}

          {/* Paso 3: Confirmar */}
          {paso === 2 && (
            <>
              <div>
                <h2 className="text-lg font-semibold text-gray-900">Revisa tu solicitud</h2>
                <p className="text-sm text-gray-600 mt-0.5">Con «Editar» vuelves al paso donde se cambia. Nada se envía hasta que confirmes.</p>
              </div>

              <div className="grid gap-6 lg:grid-cols-2">
                <Seccion titulo="Servicio" onEditar={() => irAPaso(0)}>
                  {procesoElegido && (
                    <>
                      <p className="text-base font-semibold text-gray-900">{procesoElegido.nombreProceso}</p>
                      {procesoElegido.clasificacion && <p className="text-xs text-gray-600">{procesoElegido.clasificacion}</p>}
                      {(subprocesosPorProceso[procesoElegido.idProceso] ?? []).length > 0 && (
                        <ul className="mt-3 flex flex-wrap gap-1.5">
                          {subprocesosPorProceso[procesoElegido.idProceso].map((sp, i) => (
                            <li key={i} className="text-xs px-2 py-0.5 rounded-full border border-gray-200 text-gray-700">{sp.nombreProgreso}</li>
                          ))}
                        </ul>
                      )}
                    </>
                  )}
                </Seccion>

                <Seccion titulo="Datos del evaluado" onEditar={() => irAPaso(1)}>
                  <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
                    <Dato etiqueta="Nombre" valor={`${evaluado.nombres} ${evaluado.apellidos}`.trim()} />
                    <Dato etiqueta="Cédula" valor={evaluado.cedula} />
                    <Dato etiqueta="Cargo" valor={evaluado.cargo} />
                    <Dato etiqueta="Criticidad del cargo" valor={CRITICIDADES.find((c) => c.valor === evaluado.criticidadCargo)?.titulo} />
                    <Dato etiqueta={rotulo} valor={ciudadSeleccionada ? `${ciudadSeleccionada.nombreCiudad} — ${ciudadSeleccionada.departamento}` : ''} />
                    {pideDireccion && <Dato etiqueta="Dirección de la visita" valor={evaluado.direccionVisita} />}
                    <Dato etiqueta="Celular" valor={evaluado.celular} />
                    <Dato etiqueta="Correo" valor={evaluado.email} />
                  </dl>
                </Seccion>
              </div>

              {(formatoDelServicio || temasElegidos.length > 0) && (
                <SolicitudCliente titulo="Información de la poligrafía" detalle="Esto verá el poligrafista" solicitud={resumenExtra} onEditar={() => irAPaso(1)} />
              )}

              <div className="grid gap-6 lg:grid-cols-2">
                <Seccion titulo="Entrega estimada">
                  {entrega ? (
                    <>
                      <p className="text-sm text-gray-900">
                        <span className="text-2xl font-bold text-primary-700">{entrega.diasHabiles}</span>{' '}
                        <span className="font-medium">día{entrega.diasHabiles !== 1 ? 's' : ''} hábil{entrega.diasHabiles !== 1 ? 'es' : ''}</span>
                      </p>
                      <p className="text-sm text-gray-600 mt-1">Fecha estimada: <span className="font-medium text-gray-900">{entrega.texto}</span></p>
                      {entrega.nota && <p className="text-xs text-amber-700 mt-2">{entrega.nota}</p>}
                    </>
                  ) : <p className="text-sm text-gray-500">Calculando la fecha…</p>}
                </Seccion>

                <Seccion titulo="Notas adicionales" descripcion="Opcional">
                  <label htmlFor={`${idCampos}-7`} className="sr-only">Notas adicionales</label>
                  <textarea id={`${idCampos}-7`} rows={3} className={`${inputCls} resize-y`} value={notas} onChange={(e) => setNotas(e.target.value)}
                    placeholder="Instrucciones especiales, contexto del cargo, etc." />
                </Seccion>
              </div>

              <Seccion titulo="Costo y forma de pago">
                <BloqueCosto proceso={procesoElegido} ciudad={ciudadSeleccionada} resumen={resumenCobro} cargando={cargandoResumen} />
              </Seccion>

              {error && <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700" role="alert">{error}</div>}
            </>
          )}

          {/* Navegación */}
          <div className="flex items-center justify-between pt-2">
            <button type="button" onClick={() => irAPaso(paso - 1)} disabled={paso === 0}
              className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
              Volver
            </button>
            {paso < 2 ? (
              <button type="button" onClick={siguiente} disabled={paso === 0 && !camposValidos()}
                className="px-6 py-2.5 text-sm font-semibold text-white bg-primary-600 rounded-lg disabled:opacity-40 hover:bg-primary-700">
                Continuar
              </button>
            ) : (
              <button type="button" onClick={enviar} disabled={enviando || mora?.enMora || (resumenCobro && !resumenCobro.alcanza)}
                className="px-6 py-2.5 text-sm font-semibold text-white bg-green-700 rounded-lg disabled:opacity-40 hover:bg-green-800">
                {enviando ? 'Enviando…' : mora?.enMora ? 'Enviar · bloqueado por factura vencida' : 'Confirmar y enviar'}
              </button>
            )}
          </div>
        </div>

        {paso < 2 && (
          <PanelLateral servicio={procesoElegido} items={itemsPanel} entrega={procesoElegido ? entrega : null} estadoPago={estadoPago} />
        )}
      </div>

      {/* Modal pequeño: info de un subproceso */}
      {infoSubproceso && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
          onClick={() => setInfoSubproceso(null)}>
          <div role="dialog" aria-modal="true" aria-label={infoSubproceso.nombreProgreso} onClick={(e) => e.stopPropagation()}
            className="bg-white rounded-2xl shadow-xl w-full max-w-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100 flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-gray-900">{infoSubproceso.nombreProgreso}</p>
                <span className={`inline-block mt-1 text-[10px] px-1.5 py-0.5 rounded-full ${
                  infoSubproceso.obligatorio === false ? 'bg-gray-100 text-gray-600' : 'bg-primary-100 text-primary-800'
                }`}>
                  {infoSubproceso.obligatorio === false ? 'Opcional' : 'Obligatorio'}
                </span>
              </div>
              <button type="button" aria-label="Cerrar" onClick={() => setInfoSubproceso(null)}
                className="text-gray-500 hover:text-gray-700 text-xl leading-none flex-shrink-0">×</button>
            </div>
            <div className="px-5 py-4">
              <p className="text-sm text-gray-700">{infoSubproceso.descripcion}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default NewRequest
