import InternalLayout from './InternalLayout'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/poligrafista/dashboard', etiqueta: 'Dashboard',    icono: '📊' },
    ],
  },
  {
    titulo: 'Mis Servicios',
    items: [
      { ruta: '/poligrafista/servicios', etiqueta: 'Mis Servicios', icono: '🧪' },
      { ruta: '/poligrafista/resultados',etiqueta: 'Resultados',    icono: '📈' },
    ],
  },
]

export default function PoligrafistLayout() {
  return <InternalLayout secciones={SECCIONES} titulo="Portal Poligrafista" />
}
