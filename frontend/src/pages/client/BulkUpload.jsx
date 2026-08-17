import { useState, useRef } from 'react'
import solicitudService from '../../services/solicitudService'

function BulkUpload() {
  const [archivo, setArchivo] = useState(null)
  const [arrastrando, setArrastrando] = useState(false)
  const [procesando, setProcesando] = useState(false)
  const [resultado, setResultado] = useState(null)
  const inputRef = useRef(null)

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
    try {
      const resp = await solicitudService.cargaMasiva(archivo)
      setResultado(resp.data)
    } catch (e) {
      setResultado({ error: e.response?.data?.mensaje || 'Error al procesar el archivo' })
    } finally {
      setProcesando(false)
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

      <p className="text-sm text-gray-500 mb-4">
        Columnas requeridas: <code className="bg-gray-100 px-1 rounded">nit, razon_social, cedula, nombre, apellido, telefono, ciudad, cargo, tipo_servicio</code>
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
          {procesando ? 'Procesando...' : 'Cargar solicitudes'}
        </button>
      )}

      {/* Resultado */}
      {resultado && !resultado.error && (
        <div className="mt-6 space-y-4">
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
                <table className="w-full text-sm">
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
                        <td className="px-4 py-2 text-gray-500">{e.fila}</td>
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
