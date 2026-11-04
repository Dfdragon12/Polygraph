// AN-03 · Qué códigos puede generar cada fuente de consulta. Sale de la hoja «Listas» del diccionario (Diccionario_marcadores_Antecedentes_FINAL.xlsx);
// si algún día difiere de lo que dice el prompt, manda el diccionario. [] = la fuente no genera código aquí (se califica en otra sección).

const ALR02 = 'ALR-02'

export const CODIGOS_NACIONALES = {
  'Registraduría': [],                       // cédula: el estado se califica en la sección 5 (LEG-01)
  'Procuraduría': ['LEG-09'],
  'Policía Nacional': ['LEG-03'],
  'INPEC': [],                               // se califica en procesos (secciones 11 y 12)
  'Medidas correctivas (RNMC)': ['LEG-18'],
  'Personería de Bogotá': ['LEG-09'],
  'Rama Judicial': [],                       // se califica en procesos (sección 11)
  'Contaduría General (BDME)': ['LEG-16'],
  'Contraloría': ['LEG-08'],
  'DIAN – Proveedores ficticios': ['LEG-06'],
  'RUES': ['ALR-04'],                        // alerta: se registra en la sección 4
  'Boletines': [],
  'RND – Registro Nacional de Desaparecidos': [],
  'RUNCOM': [],
  'PJP': [],
  'Validación en Google': ['LEG-13'],
}

export const CODIGOS_INTERNACIONALES = {
  'Consejo de Seguridad ONU (vinculante)': ['LEG-02', ALR02],
  'OFAC – Lista Clinton': ['LEG-02', ALR02],
  'HM Treasury – Reino Unido': ['LEG-02', ALR02],
  'Otras listas restrictivas': ['LEG-02', ALR02],
  'Interpol': ['LEG-04', ALR02],
  'Europol': ['LEG-04', ALR02],
  'Banco Mundial': ['LEG-07', ALR02],
  'BID': ['LEG-07', ALR02],
  'Noticias criminales': ['LEG-13'],
  'PEP nacionales': ['ALR-01'],
  'PEP internacionales': ['ALR-01'],
  'SECOP': ['LEG-15'],
}

// Con coincidencia «Solo nombre» no se ofrece LEG-02 ni LEG-04: solo la alerta ALR-02 (reglas del analista).
export function codigosOfrecidos(tabla, fuente, coincidencia) {
  const base = (tabla === 'internacionales' ? CODIGOS_INTERNACIONALES : CODIGOS_NACIONALES)[fuente] ?? []
  if (tabla === 'internacionales' && coincidencia === 'Solo nombre' && base.some(c => c === 'LEG-02' || c === 'LEG-04')) return [ALR02]
  return base
}
