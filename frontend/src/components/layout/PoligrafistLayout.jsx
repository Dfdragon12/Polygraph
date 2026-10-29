import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoProbeta, IconoCuenta, IconoCatalogo } from './IconosNav'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/poligrafista/dashboard', etiqueta: 'Dashboard',    icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Mis Servicios',
    items: [
      { ruta: '/poligrafista/servicios', etiqueta: 'Mis Servicios', icono: <IconoProbeta /> },
    ],
  },
  {
    titulo: 'Reportes',
    items: [
      { ruta: '/poligrafista/productividad', etiqueta: 'Productividad', icono: <IconoCatalogo /> },
    ],
  },
  {
    titulo: 'Mi Perfil',
    items: [
      { ruta: '/poligrafista/perfil', etiqueta: 'Mis datos', icono: <IconoCuenta /> },
    ],
  },
]

export default function PoligrafistLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Poligrafista" busquedaEntidades />
}
