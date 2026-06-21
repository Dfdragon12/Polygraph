import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import catalogoService from '../../services/catalogoService'
import solicitudService from '../../services/solicitudService'

const PASOS = ['Datos del evaluado', 'Servicios', 'Confirmar y enviar']

function calcularFechaEntrega(serviciosIds, catalogo) {
  const seleccionados = catalogo.filter(s => serviciosIds.includes(s.idCatalogo))
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

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none'

function NewRequest() {
  const navigate = useNavigate()
  const [paso, setPaso] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [catalogo, setCatalogo] = useState([])

  const [evaluado, setEvaluado] = useState({
    cedula: '', nombres: '', apellidos: '', celular: '', email: '', ciudad: '', cargo: '',
  })
  const [serviciosSeleccionados, setServiciosSeleccionados] = useState([])
  const [notas, setNotas] = useState('')

  useEffect(() => {
    catalogoService.listarServicios().then((data) => setCatalogo(data)).catch(() => {})
  }, [])

  const camposValidos = () => {
    if (paso === 0) return evaluado.cedula && evaluado.nombres && evaluado.apellidos
    if (paso === 1) return serviciosSeleccionados.length > 0
    return true
  }

  const toggleServicio = (id) =>
    setServiciosSeleccionados((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    )

  const enviar = async () => {
    setEnviando(true)
    setError(null)
    try {
      await solicitudService.crear({ ...evaluado, serviciosIds: serviciosSeleccionados, notas })
      navigate('/cliente/solicitudes', { state: { exito: true } })
    } catch (e) {
      setError(e.response?.data?.mensaje || 'Error al crear la solicitud')
    } finally {
      setEnviando(false)
    }
  }

  const categorias = [...new Set(catalogo.map((s) => s.categoria))]
  const serviciosSeleccionadosData = catalogo.filter(s => serviciosSeleccionados.includes(s.idCatalogo))
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

  return (
    <div className="max-w-2xl mx-auto py-8 px-4">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Nueva Solicitud de Servicio</h1>

      {/* Stepper */}
      <div className="flex items-center mb-8">
        {PASOS.map((nombre, i) => (
          <div key={i} className="flex items-center flex-1">
            <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium
              ${i <= paso ? 'bg-indigo-600 text-white' : 'bg-gray-200 text-gray-500'}`}>
              {i < paso ? '✓' : i + 1}
            </div>
            <span className={`ml-2 text-sm hidden sm:block ${i === paso ? 'text-indigo-600 font-medium' : 'text-gray-400'}`}>
              {nombre}
            </span>
            {i < PASOS.length - 1 && (
              <div className={`flex-1 h-0.5 mx-3 ${i < paso ? 'bg-indigo-600' : 'bg-gray-200'}`} />
            )}
          </div>
        ))}
      </div>

      {/* Paso 0: Datos del evaluado */}
      {paso === 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
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
              <input className={inputCls} value={evaluado.ciudad}
                onChange={(e) => setEvaluado({ ...evaluado, ciudad: e.target.value })} />
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
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-800 mb-4">Selecciona los servicios</h2>
          {categorias.map((cat) => (
            <div key={cat} className="mb-5">
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                {cat.replace(/_/g, ' ')}
              </h3>
              <div className="space-y-2">
                {catalogo.filter((s) => s.categoria === cat).map((s) => (
                  <label key={s.idCatalogo} className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition-colors
                    ${serviciosSeleccionados.includes(s.idCatalogo)
                      ? 'border-indigo-400 bg-indigo-50' : 'border-gray-200 hover:border-gray-300'}`}>
                    <input type="checkbox" className="mt-0.5 accent-indigo-600"
                      checked={serviciosSeleccionados.includes(s.idCatalogo)}
                      onChange={() => toggleServicio(s.idCatalogo)} />
                    <div>
                      <p className="text-sm font-medium text-gray-800">{s.nombre}</p>
                      <p className="text-xs text-gray-500">{s.descripcion}</p>
                    </div>
                  </label>
                ))}
              </div>
            </div>
          ))}
          {serviciosSeleccionados.length === 0 && (
            <p className="text-sm text-red-500 mt-2">Selecciona al menos un servicio para continuar.</p>
          )}
        </div>
      )}

      {/* Paso 2: Confirmar */}
      {paso === 2 && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-800">Resumen de la solicitud</h2>

          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
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
                {evaluado.ciudad && (
                  <div>
                    <span className="text-gray-500">Ciudad: </span>
                    <span className="text-gray-800">{evaluado.ciudad}</span>
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
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Servicios solicitados</h3>
              <ul className="space-y-1.5">
                {serviciosSeleccionadosData.map((s) => (
                  <li key={s.idCatalogo} className="flex items-center gap-2 text-sm text-gray-800">
                    <span className="text-green-500 font-bold text-base">✓</span>
                    {s.nombre}
                  </li>
                ))}
              </ul>
            </div>

            {/* Tiempo estimado */}
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Tiempo estimado de entrega</h3>
              <p className="text-sm text-gray-800">
                <span className="font-semibold text-indigo-600">
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
              <p className="text-sm text-gray-600">Se descontará de tu bolsa de servicios prepago.</p>
            </div>
          </div>

          {/* Notas */}
          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <label className="block text-sm font-medium text-gray-700 mb-1">Notas adicionales</label>
            <textarea rows={3}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
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
            className="px-5 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg disabled:opacity-40 hover:bg-indigo-700">
            Siguiente
          </button>
        ) : (
          <button onClick={enviar} disabled={enviando}
            className="px-5 py-2 text-sm font-medium text-white bg-green-600 rounded-lg disabled:opacity-40 hover:bg-green-700">
            {enviando ? 'Enviando...' : 'Confirmar y enviar'}
          </button>
        )}
      </div>
    </div>
  )
}

export default NewRequest
