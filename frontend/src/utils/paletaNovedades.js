// Colores de las categorías de novedad — la categoría guarda solo la clave (categorias_novedad.
// color) y aquí se traduce a clases de Tailwind. Las clases van escritas completas a propósito:
// Tailwind solo genera las que encuentra literalmente en el código.
export const PALETA_NOVEDAD = {
  red:    { nombre: 'Rojo',     punto: 'bg-red-500',    chip: 'bg-red-50 text-red-700 border-red-100' },
  orange: { nombre: 'Naranja',  punto: 'bg-orange-500', chip: 'bg-orange-50 text-orange-700 border-orange-100' },
  amber:  { nombre: 'Ámbar',    punto: 'bg-amber-500',  chip: 'bg-amber-50 text-amber-700 border-amber-100' },
  green:  { nombre: 'Verde',    punto: 'bg-green-500',  chip: 'bg-green-50 text-green-700 border-green-100' },
  sky:    { nombre: 'Celeste',  punto: 'bg-sky-500',    chip: 'bg-sky-50 text-sky-700 border-sky-100' },
  blue:   { nombre: 'Azul',     punto: 'bg-blue-500',   chip: 'bg-blue-50 text-blue-700 border-blue-100' },
  violet: { nombre: 'Violeta',  punto: 'bg-violet-500', chip: 'bg-violet-50 text-violet-700 border-violet-100' },
  slate:  { nombre: 'Pizarra',  punto: 'bg-slate-500',  chip: 'bg-slate-50 text-slate-700 border-slate-200' },
  gray:   { nombre: 'Gris',     punto: 'bg-gray-400',   chip: 'bg-gray-50 text-gray-600 border-gray-200' },
}

export const colorNovedad = (clave) => PALETA_NOVEDAD[clave] ?? PALETA_NOVEDAD.gray

// Roles que ejecutan subprocesos y por tanto reportan novedades.
export const ROLES_NOVEDAD = [
  { valor: 'ANALISTA_INTERNO', etiqueta: 'Analista' },
  { valor: 'POLIGRAFISTA', etiqueta: 'Poligrafista' },
  { valor: 'VISITADOR', etiqueta: 'Visitador' },
]
