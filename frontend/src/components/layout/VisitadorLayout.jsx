import InternalLayout from './InternalLayout'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/visitador/dashboard', etiqueta: 'Dashboard',   icono: '📊' },
    ],
  },
  {
    titulo: 'Mis Visitas',
    items: [
      { ruta: '/visitador/visitas',   etiqueta: 'Mis Visitas', icono: '🏠' },
      { ruta: '/visitador/informes',  etiqueta: 'Informes',    icono: '📄' },
    ],
  },
]

export default function VisitadorLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Visitador" />
}
