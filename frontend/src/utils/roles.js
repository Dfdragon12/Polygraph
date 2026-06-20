// src/utils/roles.js

export const ROLES = {
  ADMIN_POLYGRAPH:   'ADMIN_POLYGRAPH',
  GESTOR:            'GESTOR',
  ANALISTA_INTERNO:  'ANALISTA_INTERNO',
  PROGRAMADOR:       'PROGRAMADOR',
  POLIGRAFISTA:      'POLIGRAFISTA',
  VISITADOR:         'VISITADOR',
  ADMIN_CLIENTE:     'ADMIN_CLIENTE',
  ANALISTA_CLIENTE:  'ANALISTA_CLIENTE',
  EVALUADO:          'EVALUADO',
}

const ROLES_INTERNOS = [
  ROLES.ADMIN_POLYGRAPH,
  ROLES.GESTOR,
  ROLES.ANALISTA_INTERNO,
  ROLES.PROGRAMADOR,
  ROLES.POLIGRAFISTA,
  ROLES.VISITADOR,
]

const ROLES_CLIENTE = [
  ROLES.ADMIN_CLIENTE,
  ROLES.ANALISTA_CLIENTE,
]

// Normaliza el rol: mayúsculas, sin espacios — por si el backend lo manda raro
const normalizar = (rol) => (rol ?? '').toString().trim().toUpperCase()

export const esRolInterno = (rol) => ROLES_INTERNOS.includes(normalizar(rol))
export const esRolCliente = (rol) => ROLES_CLIENTE.includes(normalizar(rol))
export const esEvaluado   = (rol) => normalizar(rol) === ROLES.EVALUADO
export const esAdminPoly  = (rol) => normalizar(rol) === ROLES.ADMIN_POLYGRAPH

export const rutaInicialPorRol = (rol) => {
  if (esRolInterno(rol)) return '/admin/dashboard'
  if (esRolCliente(rol)) return '/cliente/dashboard'
  if (esEvaluado(rol))   return '/evaluado/hoja-vida'
  return '/login'
}

export const etiquetaRol = (rol) => {
  const mapa = {
    ADMIN_POLYGRAPH:  'Administrador',
    GESTOR:           'Gestor',
    ANALISTA_INTERNO: 'Analista Interno',
    PROGRAMADOR:      'Programador',
    POLIGRAFISTA:     'Poligrafista',
    VISITADOR:        'Visitador Domiciliario',
    ADMIN_CLIENTE:    'Administrador Cliente',
    ANALISTA_CLIENTE: 'Analista Cliente',
    EVALUADO:         'Evaluado',
  }
  return mapa[normalizar(rol)] ?? 'Usuario'
}