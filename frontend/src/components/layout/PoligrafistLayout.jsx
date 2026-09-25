import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoProbeta, IconoEstadisticas } from './IconosNav'

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
      { ruta: '/poligrafista/resultados',etiqueta: 'Resultados',    icono: <IconoEstadisticas /> },
    ],
  },
]

export default function PoligrafistLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Poligrafista" busquedaEntidades />
}
