import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoChincheta, IconoCheck, IconoDocumento } from './IconosNav'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/analista/dashboard',    etiqueta: 'Dashboard',      icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Mi Trabajo',
    items: [
      { ruta: '/analista/asignaciones', etiqueta: 'Mis Asignaciones', icono: <IconoChincheta /> },
      { ruta: '/analista/validaciones', etiqueta: 'Validaciones',     icono: <IconoCheck /> },
      { ruta: '/analista/informes',     etiqueta: 'Informes',         icono: <IconoDocumento /> },
    ],
  },
]

export default function AnalistaLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Analista Interno" busquedaEntidades />
}
