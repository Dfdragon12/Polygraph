import InternalLayout from './InternalLayout'

const SECCIONES = [
  {
    titulo: 'General',
    items: [
      { ruta: '/gestor/dashboard',    etiqueta: 'Dashboard',    icono: '📊' },
    ],
  },
  {
    titulo: 'Gestión',
    items: [
      { ruta: '/gestor/solicitudes',  etiqueta: 'Solicitudes',  icono: '📋' },
      { ruta: '/gestor/asignaciones', etiqueta: 'Asignaciones', icono: '👤' },
    ],
  },
]

export default function GestorLayout() {
  return <InternalLayout secciones={SECCIONES} />
}
