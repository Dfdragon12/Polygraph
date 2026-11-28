import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoChincheta, IconoCuenta, IconoCatalogo } from './IconosNav'

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
    ],
  },
  {
    titulo: 'Reportes',
    items: [
      { ruta: '/analista/productividad', etiqueta: 'Productividad', icono: <IconoCatalogo /> },
    ],
  },
  {
    titulo: 'Mi Perfil',
    items: [
      { ruta: '/analista/perfil', etiqueta: 'Mis datos', icono: <IconoCuenta /> },
    ],
  },
]

export default function AnalistaLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Analista Interno" busquedaEntidades />
}
