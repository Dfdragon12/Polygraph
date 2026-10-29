import { useState, useRef, useEffect } from 'react'
import solicitudService from '../../services/solicitudService'
import { esperarTrabajo, mensajeDeError } from '../../services/trabajoService'

function textoTrabajo(trabajo) {
  if (!trabajo) return 'Subiendo archivo...'
  if (trabajo.estado === 'EN_COLA') {
    return trabajo.posicionEnCola > 1 ? `En fila — hay ${trabajo.posicionEnCola - 1} carga(s) antes` : 'En fila, empieza en un momento...'
  }
  return `Procesando filas... ${trabajo.progreso ?? 0}%`
}

function BulkUpload() {
  const [archivo, setArchivo] = useState(null)
  const [arrastrando, setArrastrando] = useState(false)
  const [procesando, setProcesando] = useState(false)
  const [trabajo, setTrabajo] = useState(null)
  const [resultado, setResultado] = useState(null)
  const inputRef = useRef(null)
  const abortoRef = useRef(null)

  // Al salir de la página se deja de consultar (la carga sigue en el servidor igual).
  useEffect(() => () => abortoRef.current?.abort(), [])

  const manejarArchivo = (file) => {
    if (!file) return
    if (!file.name.endsWith('.xlsx')) {
      alert('Solo se aceptan archivos .xlsx')
      return
    }
    setArchivo(file)
    setResultado(null)
  }

  const onDrop = (e) => {
    e.preventDefault()
    setArrastrando(false)
    manejarArchivo(e.dataTransfer.files[0])
  }

  const enviar = async () => {
    if (!archivo) return
    setProcesando(true)
    setTrabajo(null)
    const aborto = new AbortController()
    abortoRef.current = aborto
    try {
      // 202: el servidor solo guarda el archivo y lo pone en la cola; aquí se sigue el avance.
      const inicial = await solicitudService.cargaMasiva(archivo)
      const terminado = await esperarTrabajo(inicial, { signal: aborto.signal, onActualizacion: setTrabajo })
      setResultado(terminado.resultado)
    } catch (e) {
      if (e.cancelado) return
      setResultado({ error: mensajeDeError(e, 'Error al procesar el archivo') })
    } finally {
      if (!aborto.signal.aborted) {
        setProcesando(false)
        setTrabajo(null)
      }
    }
  }

  const descargarPlantilla = async () => {
    const resp = await solicitudService.descargarPlantilla()
    const url = window.URL.createObjectURL(new Blob([resp.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = 'plantilla-carga-masiva.xlsx'
    a.click()
  }

  return (
    <div className="max-w-3xl mx-auto py-8 px-4">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-800">Carga Masiva desde Excel</h1>
        <button onClick={descargarPlantilla}
          className="flex items-center gap-2 text-sm text-primary-600 border border-primary-300 rounded-lg px-3 py-2 hover:bg-primary-50">
          Descargar plantilla
        </button>
      </div>

      <p className="text-sm text-gray-600 mb-2">
        Columnas: <code className="bg-gray-100 px-1 rounded">cedula, nombres, apellidos, cargo, criticidad_cargo, celular, email, ciudad, direccion, servicio</code>
      </p>
      <p className="text-sm text-gray-600 mb-4">
        Todo el archivo se revisa antes de crear nada: con una sola fila mal, o si pides más de lo que tienes en tu bolsa o cupo, <strong>no se crea ninguna solicitud</strong> y
        te decimos qué corregir en cada fila. La dirección solo se pide en servicios con visita. Descarga la plantilla actualizada: la anterior (con NIT y razón social) ya no sirve.
      </p>

      {/* Zona de drop */}
      <div
        onDragOver={(e) => { e.preventDefault(); setArrastrando(true) }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors
          ${arrastrando ? 'border-primary-400 bg-primary-50' : archivo ? 'border-green-400 bg-green-50' : 'border-gray-300 hover:border-gray-400'}`}>
        <input ref={inputRef} type="file" accept=".xlsx" className="hidden"
          onChange={(e) => manejarArchivo(e.target.files[0])} />
        {archivo ? (
          <div>
            <p className="text-green-700 font-medium">{archivo.name}</p>
            <p className="text-sm text-gray-500 mt-1">{(archivo.size / 1024).toFixed(1)} KB — clic para cambiar</p>
          </div>
        ) : (
          <div>
            <p className="text-gray-500">Arrastra tu archivo .xlsx aquí</p>
            <p className="text-sm text-gray-400 mt-1">o haz clic para seleccionarlo</p>
          </div>
        )}
      </div>

      {archivo && !resultado && (
        <button onClick={enviar} disabled={procesando}
          className="mt-4 w-full py-2.5 text-sm font-medium text-white bg-primary-600 rounded-lg disabled:opacity-50 hover:bg-primary-700">
          {procesando ? textoTrabajo(trabajo) : 'Cargar solicitudes'}
        </button>
      )}

      {procesando && trabajo?.estado === 'PROCESANDO' && (
        <div className="mt-3">
          <div className="h-2 rounded-full bg-gray-100 overflow-hidden">
            <div className="h-full bg-primary-500 transition-all duration-500" style={{ width: `${trabajo.progreso ?? 0}%` }} />
          </div>
          <p className="text-xs text-gray-400 mt-1.5">Si sales de esta página la carga continúa en el servidor y las solicitudes se crean igual, pero no verás este resumen.</p>
        </div>
      )}

      {/* Resultado */}
      {resultado && !resultado.error && (
        <div className="mt-6 space-y-4">
          {resultado.mensaje && (
            <p role="status" className={`rounded-lg border px-4 py-3 text-sm font-medium ${resultado.errores?.length ? 'border-red-200 bg-red-50 text-red-800' : 'border-green-200 bg-green-50 text-green-800'}`}>
              {resultado.mensaje}
            </p>
          )}
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-green-50 border border-green-200 rounded-lg p-4 text-center">
              <p className="text-3xl font-bold text-green-700">{resultado.filasExitosas}</p>
              <p className="text-sm text-green-600 mt-1">solicitudes creadas</p>
            </div>
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-center">
              <p className="text-3xl font-bold text-red-700">{resultado.filasConError}</p>
              <p className="text-sm text-red-600 mt-1">filas con error</p>
            </div>
          </div>

          {resultado.errores?.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-gray-700 mb-2">Detalle de errores</h3>
              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <table className="table-stack w-full text-sm">
                  <thead className="bg-gray-50 text-gray-600">
                    <tr>
                      <th className="text-left px-4 py-2 font-medium">Fila</th>
                      <th className="text-left px-4 py-2 font-medium">Cédula</th>
                      <th className="text-left px-4 py-2 font-medium">Error</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resultado.errores.map((e, i) => (
                      <tr key={i} className="border-t border-gray-100">
                        <td className="px-4 py-2 text-gray-500">{e.fila > 0 ? e.fila : 'Todo el archivo'}</td>
                        <td className="px-4 py-2">{e.cedula}</td>
                        <td className="px-4 py-2 text-red-600">{e.error}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <button onClick={() => { setArchivo(null); setResultado(null) }}
            className="w-full py-2 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50">
            Cargar otro archivo
          </button>
        </div>
      )}

      {resultado?.error && (
        <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          {resultado.error}
        </div>
      )}
    </div>
  )
}

export default BulkUpload
