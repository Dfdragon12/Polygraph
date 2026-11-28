import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoAgenda, IconoCuenta, IconoCatalogo } from './IconosNav'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/programador/dashboard',    etiqueta: 'Dashboard',    icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Programación',
    items: [
      { ruta: '/programador/agenda',       etiqueta: 'Agenda',       icono: <IconoAgenda /> },
    ],
  },
  {
    titulo: 'Reportes',
    items: [
      { ruta: '/programador/productividad', etiqueta: 'Productividad', icono: <IconoCatalogo /> },
    ],
  },
  {
    titulo: 'Mi Perfil',
    items: [
      { ruta: '/programador/perfil', etiqueta: 'Mis datos', icono: <IconoCuenta /> },
    ],
  },
]

export default function ProgramadorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Programador" busquedaEntidades />
}
