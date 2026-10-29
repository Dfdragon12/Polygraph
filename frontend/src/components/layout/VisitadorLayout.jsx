import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoCasa, IconoCuenta, IconoCatalogo } from './IconosNav'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/visitador/dashboard', etiqueta: 'Dashboard',   icono: <IconoDashboard /> },
    ],
  },
  {
    titulo: 'Mis Visitas',
    items: [
      { ruta: '/visitador/visitas',   etiqueta: 'Mis Visitas', icono: <IconoCasa /> },
    ],
  },
  {
    titulo: 'Reportes',
    items: [
      { ruta: '/visitador/productividad', etiqueta: 'Productividad', icono: <IconoCatalogo /> },
    ],
  },
  {
    titulo: 'Mi Perfil',
    items: [
      { ruta: '/visitador/perfil', etiqueta: 'Mis datos', icono: <IconoCuenta /> },
    ],
  },
]

export default function VisitadorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Visitador" busquedaEntidades />
}
