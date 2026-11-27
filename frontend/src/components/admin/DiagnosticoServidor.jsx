import { useCallback, useEffect, useState } from 'react'
import api from '../../services/api'
import { Etiqueta, TarjetaSeccion, botonSecundario } from './PaginaAdmin'

// PC-08 · «Diagnóstico del servidor» (Admin → Trabajos): lo que quien publica necesita saber en una mirada — si LibreOffice está, si las carpetas existen y se pueden escribir,
// qué configuración sigue apuntando a un equipo de desarrollo — y «Probar PDF», que arma un documento de prueba y lo convierte con LibreOffice de verdad (lo mismo que hace el PDF de
// un informe o del estudio completo). Backend: AdminSistemaController (/admin/sistema).
const mensajeDe = (e, porDefecto) => e?.response?.data?.mensaje ?? porDefecto

export default function DiagnosticoServidor() {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const [probando, setProbando] = useState(false)
  const [prueba, setPrueba] = useState(null)

  const cargar = useCallback(() => {
    setError(null)
    api.get('/admin/sistema/diagnostico').then(r => setDatos(r.data)).catch(e => setError(mensajeDe(e, 'No se pudo revisar el servidor.')))
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const probar = async () => {
    setProbando(true); setPrueba(null)
    try { setPrueba((await api.post('/admin/sistema/probar-pdf')).data) }
    catch (e) { setPrueba({ ok: false, mensaje: mensajeDe(e, 'No se pudo hacer la prueba.') }) }
    finally { setProbando(false) }
  }

  return (
    <TarjetaSeccion titulo="Diagnóstico del servidor"
      descripcion="Lo que necesita este servidor para generar PDF y guardar archivos. Pruébalo después de cada instalación o cambio de configuración."
      acciones={<>
        <button type="button" onClick={cargar} className={botonSecundario}>Revisar de nuevo</button>
        <button type="button" onClick={probar} disabled={probando} aria-describedby="diag-probar-ayuda" className={botonSecundario}>{probando ? 'Generando un PDF de prueba…' : 'Probar PDF'}</button>
      </>}>
      <div className="space-y-4 px-5 py-4 text-sm">
        <p id="diag-probar-ayuda" className="sr-only">Genera un documento de prueba y lo convierte a PDF con LibreOffice. No guarda nada.</p>
        {error && <p role="alert" className="text-red-700">{error}</p>}
        {!datos && !error && <p role="status" className="text-gray-600">Revisando el servidor…</p>}
        {datos && (
          <>
            <p className="flex flex-wrap items-center gap-2">
              <Etiqueta tono={datos.todoBien ? 'verde' : 'ambar'}>{datos.todoBien ? 'Todo en orden' : 'Hay algo por revisar'}</Etiqueta>
              <span className="text-gray-700">
                <strong>LibreOffice:</strong> {datos.libreOffice.disponible ? datos.libreOffice.version : `no está disponible — ${datos.libreOffice.error}`}
              </span>
            </p>
            <ul className="space-y-1">
              {datos.carpetas.map(c => (
                <li key={c.nombre} className="flex flex-wrap items-baseline gap-x-2 text-gray-800">
                  <span aria-hidden="true">{c.existe && c.escribible ? '✓' : '✕'}</span>
                  <span className="sr-only">{c.existe && c.escribible ? 'Bien:' : 'Falla:'}</span>
                  <span>{c.nombre}</span>
                  <code className="text-xs text-gray-600 break-all">{c.ruta}</code>
                  <span className="text-xs text-gray-600">
                    {!c.existe ? 'no existe' : !c.escribible ? 'no se puede escribir' : c.espacioLibreMb >= 0 ? `${c.espacioLibreMb.toLocaleString('es-CO')} MB libres` : ''}
                  </span>
                </li>
              ))}
            </ul>
            {datos.advertencias.length > 0 && (
              <ul className="space-y-1 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-amber-900">
                {datos.advertencias.map(a => <li key={a}>{a}</li>)}
              </ul>
            )}
          </>
        )}
        {prueba && (
          <p role={prueba.ok ? 'status' : 'alert'} className={`rounded-lg border px-4 py-3 ${prueba.ok ? 'border-emerald-300 bg-emerald-50 text-emerald-900' : 'border-red-300 bg-red-50 text-red-900'}`}>
            {prueba.ok
              ? `Prueba de PDF correcta: ${prueba.paginas} página, ${Math.round(prueba.bytes / 1024)} KB, ${(prueba.milisegundos / 1000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} s.`
              : `La prueba de PDF falló: ${prueba.mensaje}`}
          </p>
        )}
      </div>
    </TarjetaSeccion>
  )
}
