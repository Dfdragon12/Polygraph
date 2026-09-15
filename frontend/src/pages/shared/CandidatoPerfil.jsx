import { useState, useEffect } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import formulariosService from '../../services/formulariosService'
import Toast from '../../components/Toast'
import TablaHojaVidaEditable from '../../components/TablaHojaVidaEditable'

// Mismo patrón de "volver" que Formulario.jsx — cada portal nombra su lista de trabajo distinto.
const VOLVER = {
  analista: { ruta: '/analista/asignaciones', etiqueta: 'Mis Asignaciones' },
  poligrafista: { ruta: '/poligrafista/servicios', etiqueta: 'Mis Servicios' },
  visitador: { ruta: '/visitador/visitas', etiqueta: 'Mis Visitas' },
  gestor: { ruta: '/gestor/solicitudes', etiqueta: 'Solicitudes' },
  programador: { ruta: '/programador/agenda', etiqueta: 'Agenda' },
}

// Orden fijo de las 4 tablas — el mismo en todos los perfiles, para que sea predecible.
const ORDEN_TABLAS = ['referencias', 'educacion', 'experiencia_laboral', 'inactividades']

function IconoPerfil() {
  return (
    <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17.982 18.725A7.488 7.488 0 0012 15.75a7.488 7.488 0 00-5.982 2.975m11.964 0a9 9 0 10-11.963 0m11.963 0A8.966 8.966 0 0112 21a8.966 8.966 0 01-5.982-2.275M15 9.75a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

export default function CandidatoPerfil() {
  const { idCandidato } = useParams()
  const prefijo = useLocation().pathname.split('/')[1]
  const volver = VOLVER[prefijo] ?? VOLVER.analista
  // Analista/poligrafista/visitador pueden agregar y editar filas, pero no borrar ninguna —
  // evita que se pierda por accidente un dato real del candidato (compartido con otros
  // servicios). Gestor/programador sí.
  const puedeEliminar = !['analista', 'poligrafista', 'visitador'].includes(prefijo)

  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)
  const [datos, setDatos] = useState(null)

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  useEffect(() => {
    formulariosService.obtenerHojaVidaCandidato(idCandidato)
      .then(setDatos)
      .catch(err => setError(err.response?.data?.mensaje ?? 'No se pudo cargar el perfil del candidato.'))
      .finally(() => setCargando(false))
  }, [idCandidato])

  const actualizarTabla = (actualizado) => {
    setDatos(prev => ({ ...prev, tablas: prev.tablas.map(t => t.clave === actualizado.clave ? actualizado : t) }))
  }

  if (cargando) {
    return (
      <div className="flex justify-center py-16">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-violet-600 border-t-transparent" />
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-3">
        <Link to={volver.ruta} className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          {volver.etiqueta}
        </Link>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
      )}

      {datos && (
        <>
          <div className="mb-6 rounded-2xl border border-violet-100 bg-gradient-to-br from-violet-50 via-white to-primary-50/40 px-6 py-5">
            <div className="flex items-center gap-4">
              <div className="flex-shrink-0 w-14 h-14 rounded-2xl bg-gradient-to-br from-violet-500 to-violet-600 text-white flex items-center justify-center shadow-md shadow-violet-200">
                <IconoPerfil />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-[11px] font-semibold uppercase tracking-wider text-violet-500">Perfil del candidato</p>
                <h2 className="text-xl font-bold text-gray-900 truncate">
                  {datos.nombreCompleto} · {datos.cedula}
                </h2>
              </div>
            </div>
          </div>

          <div className="mb-5 bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3">
            <p className="text-xs text-amber-800">
              Esta es la hoja de vida real de este candidato — lo que edites o agregues aquí se ve en cualquier otro
              servicio que tenga, no solo en el que estás trabajando ahora.
            </p>
          </div>

          <div className="space-y-4">
            {ORDEN_TABLAS.map(clave => {
              const tabla = datos.tablas.find(t => t.clave === clave)
              if (!tabla) return null
              return (
                <TablaHojaVidaEditable key={clave} idCandidato={idCandidato} tabla={tabla}
                  onGuardado={actualizarTabla} onError={m => mostrarToast(m, 'error')} puedeEliminar={puedeEliminar} />
              )
            })}
          </div>
        </>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
