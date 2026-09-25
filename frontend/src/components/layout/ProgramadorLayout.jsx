import InternalLayout from './InternalLayout'
import { IconoDashboard, IconoAgenda } from './IconosNav'

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
]

export default function ProgramadorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Programador" busquedaEntidades />
}
