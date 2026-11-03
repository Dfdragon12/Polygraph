import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import enlaceProveedorService from '../../services/enlaceProveedorService'
import formulariosService from '../../services/formulariosService'
import colaOffline from '../../services/colaOffline'
import { guardarTokenProveedor, olvidarTokenProveedor } from '../../services/accesoProveedor'
import { FormatoPoligrafia } from '../poligrafista/FormatoPoligrafia'
import { esFormatoPoligrafia } from '../poligrafista/fasesFormato'

// PF-09 · Pantalla del proveedor externo: /proveedor/:token. Sin usuario ni contraseña, sin menú: solo la evaluación a la que da acceso el enlace.
// Antes de cargar sube lo que haya quedado guardado en este dispositivo de una visita anterior sin señal.

const MENSAJES = {
  ENLACE_ENVIADO: 'Este informe ya se envió. El enlace dejó de funcionar.',
  ENLACE_VENCIDO: 'Este enlace venció. Pide uno nuevo al programador.',
  ENLACE_REVOCADO: 'Este enlace fue cancelado. Pide uno nuevo al programador.',
  ENLACE_REEMPLAZADO: 'Hay un enlace más reciente para esta evaluación. Usa el último que te enviaron.',
  ENLACE_NO_DISPONIBLE: 'Esta evaluación ya no está disponible para diligenciar. Consulta con el programador.',
  ENLACE_DESCONOCIDO: 'Este enlace no es válido. Revisa que lo copiaste completo o pide uno nuevo al programador.',
}

function Marco({ children }) {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 px-4 py-3">
        <p className="text-base font-bold tracking-wide text-gray-900">POLYGRAPH SERVICE</p>
        <p className="text-xs text-gray-700">Informe de poligrafía · acceso de proveedor</p>
      </header>
      {children}
    </div>
  )
}

function Aviso({ titulo, texto, tono = 'neutro' }) {
  const color = tono === 'exito' ? 'border-green-300 bg-green-50 text-green-900' : 'border-gray-300 bg-white text-gray-900'
  return (
    <Marco>
      <main id="contenido" className="max-w-md mx-auto px-4 py-12">
        <div className={`rounded-xl border p-6 ${color}`} role="status">
          <h1 className="text-lg font-bold mb-2">{titulo}</h1>
          <p className="text-base">{texto}</p>
        </div>
      </main>
    </Marco>
  )
}

export default function ProveedorExterno() {
  const { token } = useParams()
  const [fase, setFase] = useState({ estado: 'cargando' })

  useEffect(() => {
    let vivo = true
    guardarTokenProveedor(token)
    const cargar = async () => {
      try {
        const sesion = await enlaceProveedorService.sesion()
        // Lo que quedó sin subir de una visita anterior (sin señal) sube ANTES de leer el formulario, para no mostrar datos viejos.
        const id = sesion.idServicioSubproceso
        const pendiente = await colaOffline.leer(`form:${id}`)
        let aviso = null
        if (pendiente) {
          try { await formulariosService.guardarFormulario(id, pendiente.payload, pendiente.omitidas); await colaOffline.borrarSiIgual(`form:${id}`, pendiente.ts) } catch (err) {
            aviso = err.response ? (err.response.data?.mensaje ?? 'No se pudo subir lo que quedó guardado en este dispositivo.') : null
            if (!err.response) throw err
          }
        }
        const datos = await formulariosService.obtenerFormulario(id)
        if (!vivo) return
        setFase(esFormatoPoligrafia(datos.secciones) ? { estado: 'listo', id, datos, aviso } : { estado: 'otroFormato' })
      } catch (err) {
        if (!vivo) return
        const codigo = err.response?.data?.codigo
        if (err.response && MENSAJES[codigo]) setFase({ estado: 'invalido', texto: MENSAJES[codigo] })
        else if (!err.response) setFase({ estado: 'sinRed' })
        else setFase({ estado: 'invalido', texto: err.response?.data?.mensaje ?? MENSAJES.ENLACE_DESCONOCIDO })
      }
    }
    cargar()
    return () => { vivo = false }
  }, [token])

  // Al terminar, el enlace ya no sirve: se olvida en esta pestaña.
  const alEnviar = () => { olvidarTokenProveedor(); setFase({ estado: 'enviado' }) }

  if (fase.estado === 'cargando') {
    return <Marco><div className="flex justify-center py-20" role="status" aria-label="Cargando"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div></Marco>
  }
  if (fase.estado === 'sinRed') return <Aviso titulo="Sin conexión" texto="No se pudo abrir la evaluación. Conéctate a internet y vuelve a abrir el enlace; lo que ya habías escrito está guardado en este dispositivo." />
  if (fase.estado === 'invalido') return <Aviso titulo="Este enlace no sirve" texto={fase.texto} />
  if (fase.estado === 'otroFormato') return <Aviso titulo="Formato no disponible" texto="Esta evaluación usa un formato que todavía no se puede diligenciar con enlace. Consulta con el programador." />
  if (fase.estado === 'enviado') return <Aviso tono="exito" titulo="Informe enviado" texto="Gracias. El informe quedó en revisión del gestor y este enlace ya no funciona. Puedes cerrar esta página." />

  return (
    <Marco>
      <main id="contenido" className="px-4 md:px-6 py-4">
        {fase.aviso && <p role="alert" className="max-w-[1280px] mx-auto mb-3 text-sm bg-amber-50 border border-amber-300 text-amber-900 rounded px-3 py-2">{fase.aviso}</p>}
        <FormatoPoligrafia key={fase.id} inicial={fase.datos} idServicioSubproceso={String(fase.id)} proveedor={{ onEnviado: alEnviar }} />
      </main>
    </Marco>
  )
}
