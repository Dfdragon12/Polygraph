import { EMPRESA as E } from '../../config/empresa'

// Textos legales. Son una base redactada para revisión: un abogado colombiano debe aprobarlos
// antes de publicar (sobre todo lo de datos sensibles). Cada sección: [titulo, bloques]; un
// bloque es un párrafo (string) o una lista (arreglo de strings).

export const TEXTOS = {
  privacidad: {
    titulo: 'Política de Privacidad y Tratamiento de Datos Personales',
    secciones: [
      ['1. Responsable del tratamiento', [
        `${E.razonSocial} ("Polygraph"), NIT ${E.nit}, domicilio en ${E.direccion}. Consultas y reclamos: ${E.correoDatos}. Teléfono: ${E.telefono}.`,
      ]],
      ['2. Marco legal', ['Esta política se rige por la Ley 1581 de 2012, el Decreto 1074 de 2015, la Ley 1266 de 2008 y las normas que las modifiquen.']],
      ['3. A quién aplica', ['Clientes (empresas y sus usuarios), candidatos o evaluados que diligencian información a solicitud de un cliente, y visitantes del sitio.']],
      ['4. Datos que tratamos y para qué', [
        'Tratamos solo lo necesario para cada finalidad (principio de minimización):',
        [
          'Clientes: identificación de la empresa y del usuario, contacto y datos de facturación, para gestionar la cuenta, prestar el servicio y facturar.',
          'Candidatos: identificación, contacto, residencia, formación, experiencia, referencias y documentos soporte, para realizar el estudio que su empleador o contratante solicitó.',
          'Candidatos (datos sensibles): datos biométricos y de salud derivados del examen poligráfico, antecedentes y datos de la visita domiciliaria.',
          'Candidatos (financieros): información de centrales de riesgo (Ley 1266 de 2008).',
          'Visitantes: dirección IP, dispositivo y páginas visitadas, según sus preferencias de cookies.',
        ],
      ]],
      ['5. Datos sensibles', ['Responder preguntas sobre datos sensibles es facultativo: nadie está obligado a autorizarlos. Negarse puede impedir que se complete la parte del estudio que los requiere, y es la entidad que solicitó el estudio quien decide las consecuencias en su proceso. Solo los tratamos con autorización previa, expresa y específica.']],
      ['6. Autorización', ['Obtenemos su autorización antes del tratamiento y guardamos fecha, versión del texto aceptado y registro técnico. Puede revocarla cuando quiera, salvo que exista un deber legal o contractual de conservar los datos.']],
      ['7. Con quién compartimos datos', [[
        'El cliente que solicitó el estudio (recibe el resultado).',
        'Encargados que tratan datos en nuestro nombre (alojamiento, correo, pasarela de pagos Wompi, facturación electrónica), con contrato de confidencialidad y seguridad.',
        'Autoridades, cuando exista orden legal.',
      ], 'No vendemos datos personales.']],
      ['8. Transferencias internacionales', ['Si los datos se almacenan o procesan fuera de Colombia, solo se hace hacia países con nivel adecuado de protección según la SIC o con contratos que garanticen un nivel equivalente.']],
      ['9. Conservación', ['Conservamos los datos mientras dure la relación y el tiempo que exijan la ley o la defensa de reclamaciones. Después se eliminan o anonimizan.']],
      ['10. Sus derechos', ['Conocer, actualizar y rectificar sus datos; pedir prueba de la autorización; ser informado del uso; presentar quejas ante la SIC; revocar la autorización y pedir la supresión cuando proceda; y acceder gratuitamente a sus datos.']],
      ['11. Cómo ejercerlos', [`Escriba a ${E.correoDatos} con su identificación y su solicitud. Las consultas se responden en máximo 10 días hábiles (prorrogables 5) y los reclamos en máximo 15 días hábiles (prorrogables 8). Si no queda satisfecho, puede acudir a la Superintendencia de Industria y Comercio (www.sic.gov.co).`]],
      ['12. Menores de edad', ['Nuestros servicios no se dirigen a menores y no los tratamos intencionalmente. Si detectamos datos de un menor, los suprimimos.']],
      ['13. Seguridad', ['Aplicamos cifrado en tránsito, control de acceso por roles, registro de auditoría, copias de respaldo y gestión de incidentes. Los incidentes que afecten datos personales se reportan a la SIC en el plazo legal.']],
      ['14. Cambios', ['Publicaremos los cambios sustanciales con aviso visible y, si requieren nueva autorización, la solicitaremos.']],
    ],
  },

  terminos: {
    titulo: 'Términos y Condiciones de Uso',
    secciones: [
      ['1. Aceptación', [`Al registrarse o usar la plataforma de ${E.razonSocial} (NIT ${E.nit}), usted acepta estos términos y las políticas de Privacidad y Cookies. Si actúa en nombre de una empresa, declara tener facultades para obligarla.`]],
      ['2. El servicio', ['Plataforma web para solicitar, gestionar y consultar estudios de confiabilidad del talento humano (poligrafía, visitas domiciliarias, estudios de seguridad y validaciones de hoja de vida), con seguimiento de estado, informes y facturación.']],
      ['3. Cuentas', ['Usted responde por la veracidad de sus datos y por custodiar sus credenciales. Toda actividad con su cuenta se presume suya. Avísenos de inmediato si hay un uso no autorizado. Podemos suspender cuentas con indicios de fraude o uso indebido.']],
      ['4. Obligaciones del cliente', ['Solo puede solicitar estudios sobre personas con las que tenga relación laboral, contractual o proceso de selección vigente, y siempre que haya obtenido su autorización para el tratamiento de datos. Debe informar al candidato la finalidad del estudio y cumplir la Ley 1581 respecto de los datos que ingrese.']],
      ['5. Conductas prohibidas', [[
        'Usar el servicio para discriminar o con fines ilícitos.',
        'Acceder sin autorización, vulnerar o probar la seguridad del sistema, o introducir software malicioso.',
        'Extraer datos de forma masiva (scraping).',
        'Suplantar identidades o subir información falsa o de terceros sin autorización.',
        'Revender o sublicenciar el acceso.',
        'Cualquier conducta contraria a la Ley 1273 de 2009.',
      ], 'Podemos suspender o terminar la cuenta ante infracciones.']],
      ['6. Naturaleza del resultado', ['Los informes son una herramienta de apoyo a la decisión. Polygraph no toma decisiones de contratación ni garantiza resultados. La decisión final y sus consecuencias corresponden exclusivamente al cliente, que responde por su uso del informe y por cumplir la normativa laboral y de protección de datos.']],
      ['7. Precios y pagos', ['Los precios se informan antes de contratar, en pesos colombianos. Los pagos en línea se procesan por Wompi; Polygraph no almacena datos de tarjetas. Aplica la Política de Reembolsos y Cancelaciones. La mora puede restringir nuevas solicitudes.']],
      ['8. Propiedad intelectual', ['La plataforma, su código, diseño, marcas, textos, metodologías y plantillas son de Polygraph o de sus licenciantes. Le otorgamos una licencia limitada, revocable, no exclusiva e intransferible para usar el servicio. Los informes entregados son suyos para uso interno, sin derecho a revenderlos. Usted nos autoriza a tratar el contenido que cargue solo para prestarle el servicio.']],
      ['9. Disponibilidad', ['Buscamos alta disponibilidad, pero no garantizamos un servicio ininterrumpido ni libre de errores. Anunciaremos los mantenimientos cuando sea posible.']],
      ['10. Limitación de responsabilidad', ['En la máxima medida permitida por la ley, Polygraph responde por daños directos y probados causados por su culpa, hasta el valor pagado por el servicio específico que originó el reclamo, y no responde por lucro cesante, pérdida de oportunidades, decisiones del cliente basadas en los informes ni información falsa suministrada por el candidato o por fuentes públicas de terceros. Nada limita la responsabilidad por dolo o culpa grave ni los derechos irrenunciables del consumidor.']],
      ['11. Indemnidad', ['El cliente mantendrá indemne a Polygraph frente a reclamaciones derivadas de su incumplimiento de estos términos o de haber solicitado estudios sin autorización del titular.']],
      ['12. Terminación', ['Puede cerrar su cuenta cuando quiera, sin perjuicio de los pagos pendientes. Podemos suspender o terminar por incumplimiento, con aviso previo salvo urgencia de seguridad.']],
      ['13. Modificaciones', ['Podemos actualizar estos términos; los cambios sustanciales se notificarán con 15 días de anticipación.']],
      ['14. Ley y jurisdicción', ['Ley colombiana. Las controversias se someten a los jueces de [ciudad], sin perjuicio de las competencias de la SIC en consumo y datos personales.']],
    ],
  },

  cookies: {
    titulo: 'Política de Cookies',
    secciones: [
      ['1. ¿Qué son?', ['Pequeños archivos o identificadores que se guardan en su dispositivo cuando visita nuestro sitio.']],
      ['2. Qué usamos', [[
        'Técnicas (necesarias): iniciar y mantener la sesión, seguridad y recordar su elección de cookies. No requieren consentimiento.',
        'Analíticas: medir el uso y mejorar el servicio con datos anonimizados. Solo con su consentimiento. Hoy no usamos ninguna de terceros.',
        'Marketing: publicidad y medición de campañas. Solo con su consentimiento. Hoy no usamos ninguna.',
      ], 'Si agregamos cookies analíticas o de marketing, actualizaremos esta política y le pediremos su elección.']],
      ['3. Cómo gestionar su elección', ['Al entrar puede aceptar todas, rechazar las no necesarias o configurarlas por categoría. Puede cambiarlo cuando quiera con "Preferencias de cookies" en el pie de página, o borrar las cookies desde su navegador.']],
      ['4. Efecto de rechazar', ['Rechazar las cookies no necesarias no afecta el uso del servicio.']],
      ['5. Plazo', ['Guardamos su elección por 12 meses y luego se la volvemos a preguntar.']],
      ['6. Contacto', [`${E.correoDatos}. Esta política complementa la Política de Privacidad.`]],
    ],
  },

  reembolsos: {
    titulo: 'Política de Reembolsos y Cancelaciones',
    secciones: [
      ['1. Alcance', [`Aplica a los servicios que se contratan por la plataforma de ${E.razonSocial}.`]],
      ['2. Cancelación antes de iniciar', ['Puede cancelar sin costo mientras la solicitud esté en estado Pendiente, antes de asignarse a un profesional o de que el candidato inicie su hoja de vida. Se devuelve el 100 %.']],
      ['3. Cancelación después de iniciada', [[
        'Con profesional asignado o cita programada: se cobra [__ %] por gestión y se reintegra lo demás.',
        'Con el servicio ya ejecutado (examen realizado, visita hecha o informe elaborado): no hay reembolso.',
      ]]],
      ['4. Inasistencia del candidato', ['Si el candidato no atiende, el cliente puede reprogramar una vez sin costo; luego se cobra [__ %]. Si el servicio no se puede ejecutar por causa ajena al cliente, se reprograma o se reintegra el 100 %.']],
      ['5. Derecho de retracto (Ley 1480 de 2011, art. 47)', ['Si usted es consumidor persona natural y contrata a distancia, puede retractarse dentro de los 5 días hábiles siguientes, salvo que el servicio ya haya comenzado con su consentimiento. Para clientes empresariales el retracto legal no aplica y rigen las condiciones de esta política.']],
      ['6. Servicios con error', ['Si el informe tiene un error imputable a Polygraph, lo corregimos sin costo; si no es posible, se reintegra el valor de ese servicio.']],
      ['7. Reversión del pago (Ley 1480 de 2011, art. 51)', ['Si pagó con tarjeta, puede pedir la reversión en los casos legales (fraude, operación no solicitada, producto no recibido) ante su banco dentro de los 5 días hábiles siguientes a conocer el hecho, y avisarnos.']],
      ['8. Cómo solicitarlo', [`Escriba a ${E.correo} con el número de solicitud y el motivo. Respondemos en máximo 5 días hábiles. El reembolso se hace por el mismo medio de pago en hasta 30 días (los tiempos bancarios son ajenos a Polygraph).`]],
      ['9. Saldos prepagados', ['Los saldos a favor se usan en servicios futuros, con vigencia de 12 meses. Su devolución se evalúa caso a caso, descontando costos de transacción no recuperables.']],
    ],
  },
}
