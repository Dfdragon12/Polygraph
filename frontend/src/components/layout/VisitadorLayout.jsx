import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoCasa, IconoDocumento } from './IconosNav'

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
      { ruta: '/visitador/informes',  etiqueta: 'Informes',    icono: <IconoDocumento /> },
    ],
  },
]

export default function VisitadorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Visitador" busquedaEntidades />
}
