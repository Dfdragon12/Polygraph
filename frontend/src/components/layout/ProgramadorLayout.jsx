import InternalLayout from './InternalLayout'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/programador/dashboard',    etiqueta: 'Dashboard',    icono: '📊' },
    ],
  },
  {
    titulo: 'Programación',
    items: [
      { ruta: '/programador/agenda',       etiqueta: 'Agenda',       icono: '📅' },
      { ruta: '/programador/servicios',    etiqueta: 'Servicios',    icono: '🔧' },
    ],
  },
]

export default function ProgramadorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Programador" />
}
