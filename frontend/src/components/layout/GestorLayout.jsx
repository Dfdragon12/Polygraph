import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoCatalogo, IconoReversion, IconoAgenda } from './IconosNav'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/gestor/dashboard',    etiqueta: 'Dashboard',    icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Gestión',
    items: [
      { ruta: '/gestor/solicitudes',   etiqueta: 'Solicitudes',  icono: <IconoCatalogo /> },
      { ruta: '/gestor/reversiones',   etiqueta: 'Reversiones',  icono: <IconoReversion /> },
    ],
  },
  {
    titulo: 'Análisis',
    items: [
      { ruta: '/gestor/agenda',        etiqueta: 'Agenda',        icono: <IconoAgenda /> },
    ],
  },
]

export default function GestorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Gestor" busquedaEntidades />
}
