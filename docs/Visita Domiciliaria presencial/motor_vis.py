"""Motor de referencia – Visita domiciliaria (reglas acordadas el 07 y 08/10/2026).
Especificación ejecutable para el código de la plataforma: el Java debe dar los mismos resultados.

Un solo formato con módulos OPCIONALES que ACTIVA EL VISITADOR (la plataforma puede sugerirlos según la solicitud):
- Clase de visita: Pre-empleo o Rutina (Rutina activa los dos cuadros del módulo rutina).
- Tipo de visita: Presencial o Virtual (Virtual activa el módulo de ubicación obligatoria).
- Módulos adicionales: OEA, Teletrabajo, Vehicular. Teletrabajo y Vehicular traen su propio grupo de fotos.
- Estado de salud y pregunta 10 (sindical): se encienden o apagan por CONFIGURACIÓN (decisión jurídica), no por el visitador.
"""

CATALOGO = {  # código: (descripción, puntos, acumula)
    'VIS-01': ('No reside en la dirección declarada, o la ubicación de la visita virtual no corresponde, sin explicación', 70, 'Una vez por estudio'),
    'VIS-02': ('Vínculo (relación, colaboración o negocios) con personas al margen de la ley o grupos armados ilegales', 70, 'Una vez por estudio'),
    'VIS-03': ('Arma de fuego sin salvoconducto en el hogar', 50, 'Una vez por estudio'),
    'VIS-04': ('Visita no realizada por causa del candidato: dos intentos fallidos o negativa a recibirla', 45, 'Una vez por estudio'),
    'VIS-05': ('Consumo actual de sustancias psicoactivas declarado', 40, 'Una vez por estudio'),
    'VIS-06': ('Economía desequilibrada: los egresos superan los ingresos sin explicación', 35, 'Una vez por estudio'),
    'VIS-07': ('Frecuenta juegos de azar con impacto en su economía', 35, 'Una vez por estudio'),
    'VIS-08': ('Falta de colaboración en la visita: se niega a responder, a mostrar espacios o a las fotos, o trata de forma irrespetuosa al visitador',
               35, 'Una vez por estudio'),
    'VIS-09': ('Recibe la visita bajo efectos aparentes de alcohol o sustancias', 50, 'Una vez por estudio'),
    'VIS-10': ('Teletrabajo: no garantiza la seguridad de la información (red no privada, equipo compartido, sin privacidad de contraseñas '
               'o espacio expuesto a terceros)', 35, 'Una vez por estudio'),
    'VIS-11': ('Vehicular: el parqueadero no ofrece seguridad para el vehículo (vía pública, sin vigilancia ni cerramiento, o arriendo sin soportes)',
               35, 'Una vez por estudio'),
}
ALERTAS = {
    'ALR-VIS-01': 'Familiar privado de la libertad, detenido o con procesos judiciales',
    'ALR-VIS-02': 'Conocido o familiar que trabaja o trabajó en la empresa (cómo llegó la hoja de vida o pregunta 8)',
    'ALR-VIS-03': 'Problemáticas graves en el sector (expendio de drogas, presencia de grupos armados u otras)',
    'ALR-VIS-04': 'Arma de fuego con salvoconducto en el hogar',
    'ALR-VIS-05': 'Teletrabajo: no cumple, o cumple con observaciones, las condiciones físicas',
    'ALR-VIS-06': 'El candidato o su familia fueron contactados por grupos ilegales como víctimas (extorsión u otra)',
    'ALR-VIS-07': 'Visita no realizada por causa ajena al candidato: reprogramar',
    'ALR-VIS-08': 'Rutina: la vinculación laboral actual no coincide con lo reportado por la empresa',
    'ALR-VIS-09': 'Vehicular: la distancia o el tamaño de la zona de parqueo no son adecuados',
}
UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}  # modelo v1.8, sección 3
PESO_AVANZADO = 15

# ---------------------------------------------------------------- listas
TIPO = ['Presencial', 'Virtual']
CLASE = ['Pre-empleo', 'Rutina']
SI_NO = ['Sí', 'No']
RESIDENCIA = ['Sí', 'No – con explicación', 'No – sin explicación']
GEOREF = ['Corresponde a la dirección suministrada', 'No corresponde']
RES_INTENTO = ['Realizada', 'No atendió', 'Canceló el candidato', 'Reprogramó el candidato', 'Se negó a recibirla', 'No realizada por causa ajena']
FALLIDO_CANDIDATO = ('No atendió', 'Canceló el candidato', 'Reprogramó el candidato')
QUIEN = ['Candidato', 'Familiar', 'Candidato y familiar']
VINCULO = ['Vínculo (relación, colaboración o negocios)', 'Víctima (extorsión u otra)']
CONSUMO = ['Consumo actual', 'Consumo en el pasado']
ECONOMIA = ['Economía equilibrada', 'Equilibrada con observación', 'Economía desequilibrada']
CONDUCTA = ['Colaboró durante toda la visita', 'Se negó a responder preguntas', 'Se negó a mostrar espacios de la vivienda', 'Se negó a las fotos',
            'Trato irrespetuoso con el visitador', 'Bajo efectos aparentes de alcohol o sustancias']
COND_FISICAS = ['Cumple', 'Cumple con observaciones', 'No cumple']
PARQUEO_TAM = ['Adecuados', 'No adecuados']
CAMBIO = ['Sin cambio', 'Cambió', 'Primer registro']
RESULTADO = ['Coincide', 'Corregido']
RES_RUTINA = ['Coincide', 'No coincide']
PAT_OEA = ['El candidato informó su patrimonio sin novedad', 'No informó su patrimonio después del recordatorio']
DC = ['ciudad', 'barrio', 'direccion', 'adicionales', 'estrato', 'estado_civil']
TT_SEG = range(35, 39)      # preguntas 35 a 38 del módulo teletrabajo: seguridad de la información
MIN_FOTOS = {'fotos': 3, 'fotos_vehicular': 2, 'fotos_teletrabajo': 20}


def fmt(x):
    return f'{x:.1f}'.replace('.', ',')


def concepto(pct):
    return 'Desfavorable' if pct < 40 else 'Favorable' if pct < 70 else 'Bien favorable' if pct < 85 else 'Muy favorable'


def escala(f, prefix, n):
    vals = [int(f[f'{prefix}_{i}']) for i in range(1, n + 1) if str(f.get(f'{prefix}_{i}', '')).isdigit()]
    if len(vals) < n:
        return None
    return round(sum(vals) / (4 * n) * 100)


def norm(s):
    return ' '.join(str(s or '').lower().replace('#', ' ').replace('n°', ' ').split())


def validar(f, t, final=False):
    if not f.get('ev_criticidad'):
        raise ValueError('Falta la criticidad del cargo.')
    av = []
    for i in range(1, 18):
        if f.get(f'ps_{i}') == 'Sí' and not f.get(f'ps_{i}_det'):
            av.append(f'Pregunta {i}: respuesta afirmativa sin detalle.')
    for i, campo, txt in ((9, 'ps_9_tipo', 'vínculo o víctima'), (15, 'ps_15_tipo', 'vínculo o víctima'), (6, 'ps_6_salvoconducto', 'si tiene salvoconducto'),
                          (7, 'ps_7_impacto', 'si afecta su economía'), (17, 'ps_17_actual', 'si el consumo es actual o pasado')):
        if f.get(f'ps_{i}') == 'Sí' and not f.get(campo):
            av.append(f'Pregunta {i}: indique {txt}.')
    for i in (3, 4, 5):
        if f.get(f'ps_{i}') == 'Sí' and f.get(f'ps_{i}_quien') in ('Candidato', 'Candidato y familiar'):
            av.append(f'Pregunta {i}: el candidato declara un antecedente propio; se compara con el servicio de Antecedentes (no suma en la visita).')
    for nombre in ('familia_origen', 'familia_pareja', 'familia_convive'):
        if not t.get(nombre):
            av.append(f'El cuadro "{nombre}" está vacío: registre las personas o una fila "No tiene".')
    corr = [k for k in DC if f.get(f'dc_{k}_reg') and norm(f.get(f'dc_{k}_reg')) != norm(f.get(f'dc_{k}_vis'))]
    if corr:
        av.append('Datos corregidos frente a lo registrado por el candidato: ' + ', '.join(corr) + '. Explíquelos en Observaciones.')
    if any(e.get('l_resultado', '').startswith('Corregido') for e in t.get('empleos', [])):
        av.append('Hay empleos corregidos: la diferencia pasa a la verificación laboral.')
    if any(e.get('e_resultado', '').startswith('Corregido') for e in t.get('estudios', [])):
        av.append('Hay estudios corregidos: la diferencia pasa a la verificación académica.')
    if final:
        for lista, minimo in MIN_FOTOS.items():
            activo = {'fotos': True, 'fotos_vehicular': f.get('mod_vehicular') == 'Sí', 'fotos_teletrabajo': f.get('mod_teletrabajo') == 'Sí'}[lista]
            if activo and len(t.get(lista, [])) < minimo:
                av.append(f'Faltan fotos en "{lista}": mínimo {minimo}.')
    return av


def calificar(f, t, final=False, config=None):
    config = config or {'salud': False, 'sindical': False}
    avisos = validar(f, t, final)
    hall, info = [], list(t.get('informativos', []))

    def add(code, fuente, detalle):
        d, p, _ = CATALOGO[code]
        if code not in [h['h_ref'] for h in hall]:
            hall.append(dict(h_ref=code, h_descripcion=d, h_fuente=fuente, h_detalle=detalle, h_puntos=str(p)))

    def alerta(code, detalle, tratamiento='Alerta: requiere revisión del analista. No suma.'):
        if code not in [i.get('i_codigo') for i in info]:
            info.append(dict(i_codigo=code, i_situacion=f'{ALERTAS[code]} ({code})', i_detalle=detalle, i_tratamiento=tratamiento))

    campos = {}
    # --- módulos activos (los activa el visitador)
    virtual = f.get('v_tipo') == 'Virtual'
    rutina = f.get('v_clase') == 'Rutina'
    oea = f.get('mod_oea') == 'Sí'
    tele = f.get('mod_teletrabajo') == 'Sí'
    vehic = f.get('mod_vehicular') == 'Sí'
    clase = ('OEA ' + f.get('v_clase', '').lower()) if oea else f.get('v_clase', '')
    campos['v_modulos'] = ' · '.join([clase] + (['Teletrabajo'] if tele else []) + (['Vehicular'] if vehic else []))

    # --- gestión de la visita (VIS-04 / ALR-VIS-07)
    its = t.get('intentos', [])
    realizada = any(i.get('it_resultado') == 'Realizada' for i in its)
    fallidos = sum(1 for i in its if i.get('it_resultado') in FALLIDO_CANDIDATO)
    negativa = any(i.get('it_resultado') == 'Se negó a recibirla' for i in its)
    if not realizada and (fallidos >= 2 or negativa):
        add('VIS-04', 'Gestión de la visita', 'Se negó a recibir la visita' if negativa else f'{fallidos} intentos fallidos por causa del candidato')
        avisos.append('Visita no realizada por causa del candidato: las demás secciones se marcan "No evaluable".')
    elif not realizada and its and its[-1].get('it_resultado') == 'No realizada por causa ajena':
        alerta('ALR-VIS-07', its[-1].get('it_obs', ''), 'Reprogramar la visita. No suma.')

    # --- datos confirmados
    for k in DC:
        if f.get(f'dc_{k}_reg') is not None:
            campos[f'dc_{k}_res'] = 'Coincide' if norm(f.get(f'dc_{k}_reg')) == norm(f.get(f'dc_{k}_vis')) else 'Corregido'
    res = f.get('v_residencia', '')
    if res == 'No – sin explicación' or (virtual and f.get('vv_georef') == 'No corresponde' and res != 'No – con explicación'):
        add('VIS-01', 'Datos generales', f.get('v_residencia_det') or 'No reside en la dirección declarada')
    if f.get('v_hv_conocido') == 'Sí' or f.get('ps_8') == 'Sí':
        alerta('ALR-VIS-02', f.get('ps_8_det') or f.get('v_hoja_vida', ''), 'Alerta para el cliente según su política de conflicto de interés. No suma.')
    if f.get('sec_grave') == 'Sí':
        alerta('ALR-VIS-03', f.get('sec_problematicas', ''))

    # --- protocolo de seguridad
    ref = {i: [] for i in range(1, 18)}
    for i in (3, 4, 5):
        if f.get(f'ps_{i}') == 'Sí' and f.get(f'ps_{i}_quien') in ('Familiar', 'Candidato y familiar'):
            alerta('ALR-VIS-01', f.get(f'ps_{i}_det', '')); ref[i].append('ALR-VIS-01')
        if f.get(f'ps_{i}') == 'Sí' and f.get(f'ps_{i}_quien') in ('Candidato', 'Candidato y familiar'):
            ref[i].append('Antecedentes')
    if f.get('ps_6') == 'Sí':
        if f.get('ps_6_salvoconducto') == 'No':
            add('VIS-03', 'Protocolo de seguridad (pregunta 6)', f.get('ps_6_det', '')); ref[6].append('VIS-03')
        else:
            alerta('ALR-VIS-04', f.get('ps_6_det', '')); ref[6].append('ALR-VIS-04')
    if f.get('ps_7') == 'Sí' and f.get('ps_7_impacto') == 'Sí':
        add('VIS-07', 'Protocolo de seguridad (pregunta 7)', f.get('ps_7_det', '')); ref[7].append('VIS-07')
    if f.get('ps_8') == 'Sí':
        ref[8].append('ALR-VIS-02')
    for i in (9, 15):
        if f.get(f'ps_{i}') == 'Sí':
            if str(f.get(f'ps_{i}_tipo', '')).startswith('Vínculo'):
                add('VIS-02', f'Protocolo de seguridad (pregunta {i})', f.get(f'ps_{i}_det', '')); ref[i].append('VIS-02')
            elif str(f.get(f'ps_{i}_tipo', '')).startswith('Víctima'):
                alerta('ALR-VIS-06', f.get(f'ps_{i}_det', '')); ref[i].append('ALR-VIS-06')
    if f.get('ps_17') == 'Sí' and f.get('ps_17_actual') == 'Consumo actual':
        add('VIS-05', 'Protocolo de seguridad (pregunta 17)', f.get('ps_17_det', '')); ref[17].append('VIS-05')
    for i, svc in ((1, 'Laboral'), (2, 'Financiero'), (13, 'Laboral'), (14, 'Laboral')):
        if f.get(f'ps_{i}') == 'Sí':
            ref[i].append(svc)
    for i in range(1, 18):
        if i == 10:
            continue
        campos[f'ps_{i}_ref'] = ', '.join(ref[i]) or '—'
        campos[f'ps_{i}_det'] = f.get(f'ps_{i}_det') or '—'

    # --- economía, conducta
    if f.get('eco_estado') == 'Economía desequilibrada':
        add('VIS-06', 'Concepto económico', f.get('eco_desequilibrio_det') or f.get('eco_conclusion', ''))
    cond = f.get('cf_conducta_lista', [])
    neg = [c for c in cond if c in CONDUCTA[1:5]]
    if neg:
        add('VIS-08', 'Concepto final (conducta durante la visita)', '; '.join(neg))
    if CONDUCTA[5] in cond:
        add('VIS-09', 'Concepto final (conducta durante la visita)', f.get('cf_actitud', ''))
    campos['cf_conducta'] = '; '.join(cond) or '—'

    # --- escalas (perfil informativo)
    for prefix, n, pct, con in (('df', 12, 'df_pct', 'df_concepto'), ('hab', 3, 'hab_pct', 'hab_concepto'), ('eco', 3, 'eco_pct', 'eco_concepto')):
        v = escala(f, prefix, n)
        campos[pct] = f'{v} %' if v is not None else '—'
        campos[con] = concepto(v) if v is not None else '—'
    ps = [escala(f, p, n) for p, n in (('df', 12), ('hab', 3), ('eco', 3))]
    if all(x is not None for x in ps):
        prom = round(sum(ps) / 3)
        campos['perfil_pct'] = f'{prom} %'; campos['perfil_concepto'] = concepto(prom)

    # --- módulos
    tablas = {}
    tablas['salud'] = [t.get('salud_rec', {})] if config.get('salud') else []
    tablas['ps_sindical'] = [t.get('sindical_rec', {})] if config.get('sindical') else []
    tablas['virtual'] = [dict(vv_georef=f.get('vv_georef', ''), vv_nota=f.get('vv_nota', ''))] if virtual else []
    if rutina:
        rec = dict(t.get('rutina_rec', {}))
        if any(rec.get(f'rv_{k}_res') == 'No coincide' for k in ('ingreso', 'contrato', 'cargo', 'salario', 'jefe')):
            alerta('ALR-VIS-08', rec.get('rv_obs', ''))
        tablas['rutina'] = [rec]
    else:
        tablas['rutina'] = []
    if vehic:
        rec = dict(t.get('vehicular_rec', {}))
        if rec.get('vh_seg_ok') == 'No':
            add('VIS-11', 'Módulo vehicular', rec.get('vh_seg_det', ''))
            rec['vh_concepto_seg'] = 'No ofrece seguridad suficiente (VIS-11)'
        else:
            rec['vh_concepto_seg'] = 'Ofrece seguridad para el vehículo'
        if rec.get('vh_concepto') == 'No adecuados':
            alerta('ALR-VIS-09', rec.get('vh_obs', ''))
        tablas['vehicular'] = [rec]
    else:
        tablas['vehicular'] = []
    if tele:
        rec = dict(t.get('teletrabajo_rec', {}))
        malas = [i for i in TT_SEG if rec.get(f'tt_{i}') == 'No']
        if malas:
            add('VIS-10', 'Módulo teletrabajo', f'{len(malas)} aspecto(s) de seguridad de la información sin cumplir (preguntas {", ".join(map(str, malas))})')
            rec['tt_seg'] = 'No garantiza la seguridad de la información (VIS-10)'
        else:
            rec['tt_seg'] = 'Garantiza la seguridad de la información'
        if rec.get('tt_cond') in ('Cumple con observaciones', 'No cumple'):
            alerta('ALR-VIS-05', rec.get('tt_obs', ''))
            rec['tt_cond'] = rec['tt_cond'] + ' (ALR-VIS-05)'
        v = escala(rec, 'tq', 12)
        rec['tq_pct'] = f'{v} %' if v is not None else '—'; rec['tq_concepto'] = concepto(v) if v is not None else '—'
        tablas['teletrabajo'] = [rec]
    else:
        tablas['teletrabajo'] = []
    tablas['oea'] = [t.get('oea_rec', {})] if oea else []
    tablas['oea_bd'] = t.get('oea_bd', []) if oea else []
    tablas['fotos_vehicular'] = t.get('fotos_vehicular', []) if vehic else []
    tablas['fotos_teletrabajo'] = t.get('fotos_teletrabajo', []) if tele else []

    # --- puntaje
    nums = [int(h['h_puntos']) for h in hall]
    bruto = sum(nums)
    puntaje = float(min(100, bruto))
    b, m = UMBRALES[f['ev_criticidad']]
    nivel = 'BAJO' if puntaje <= b else 'MEDIO' if puntaje <= m else 'ALTO'
    if not nums:
        formula = 'Sin hallazgos que sumen puntos'
    elif len(nums) == 1:
        formula = f"{hall[0]['h_ref']}: {nums[0]} de 100 posibles"
    else:
        formula = ' + '.join(map(str, nums)) + (f' = {bruto} → 100 (tope máximo)' if bruto > 100 else f' = {bruto} de 100 posibles')
    marca = f'▲ {fmt(puntaje)} puntos'
    campos.update(srv_puntaje=fmt(puntaje), srv_formula=formula,
                  sem_bajo=marca if nivel == 'BAJO' else '', sem_medio=marca if nivel == 'MEDIO' else '', sem_alto=marca if nivel == 'ALTO' else '')
    for i in info:
        i.pop('i_codigo', None)
    tablas.update(hallazgos=hall, informativos=info)
    return dict(campos=campos, tablas=tablas, hallazgos=hall, nivel=nivel, puntaje=puntaje, critico=False, avisos=avisos)


# ---------------------------------------------------------------- casos de prueba
FAM = dict(familia_origen=[{}], familia_pareja=[{}], familia_convive=[{}])
OK = [dict(it_resultado='Realizada')]


def F(crit='Estándar', **kw):
    d = dict(ev_criticidad=crit, v_tipo='Presencial', v_clase='Pre-empleo', v_residencia='Sí', eco_estado='Economía equilibrada',
             cf_conducta_lista=['Colaboró durante toda la visita'])
    d.update({f'ps_{i}': 'No' for i in range(1, 18)})
    d.update(kw)
    return d


def T(**kw):
    d = dict(FAM, intentos=OK)
    d.update(kw)
    return d


CASOS = {
    'V1 Sin novedades (Estándar)': (F(), T()),
    'V2 Economía desequilibrada (Estándar)': (F(eco_estado='Economía desequilibrada', eco_desequilibrio_det='Egresos superan ingresos en $350.000'), T()),
    'V3 No reside en la dirección declarada (Estándar)': (F(v_residencia='No – sin explicación', v_residencia_det='Vive con su pareja en otro municipio'), T()),
    'V4 Arma sin salvoconducto + consumo actual (Sensible)': (
        F('Sensible', ps_6='Sí', ps_6_det='Revólver del padre', ps_6_salvoconducto='No', ps_17='Sí', ps_17_det='Consumo de marihuana los fines de semana',
          ps_17_actual='Consumo actual'), T()),
    'V5 Contactado como víctima + arma con salvoconducto (Estándar)': (
        F(ps_15='Sí', ps_15_det='Extorsión al negocio familiar en 2024, denunciada', ps_15_tipo='Víctima (extorsión u otra)',
          ps_6='Sí', ps_6_det='Pistola con salvoconducto vigente', ps_6_salvoconducto='Sí'), T()),
    'V6 Dos intentos fallidos por causa del candidato (Crítico)': (
        F('Crítico'), T(intentos=[dict(it_resultado='No atendió'), dict(it_resultado='Canceló el candidato')])),
    'V7 Falta de colaboración y bajo efectos aparentes (Estándar)': (
        F(cf_conducta_lista=['Se negó a las fotos', 'Bajo efectos aparentes de alcohol o sustancias']), T()),
    'V8 Teletrabajo: red compartida + condiciones con observaciones (Estándar)': (
        F(mod_teletrabajo='Sí'), T(teletrabajo_rec=dict({f'tt_{i}': 'Sí' for i in range(1, 39)}, tt_35='No', tt_cond='Cumple con observaciones'))),
    'V9 Vehicular: parqueadero inseguro (Estándar)': (
        F(mod_vehicular='Sí'), T(vehicular_rec=dict(vh_seg_ok='No', vh_seg_det='Vía pública sin vigilancia', vh_concepto='Adecuados'))),
    'V10 Vínculo con personas al margen de la ley (Estándar)': (
        F(ps_9='Sí', ps_9_det='Socio en un negocio con una persona condenada por narcotráfico', ps_9_tipo='Vínculo (relación, colaboración o negocios)'), T()),
}

if __name__ == '__main__':
    for n, (f, t) in CASOS.items():
        r = calificar(f, t)
        print(n, '|', r['campos']['srv_puntaje'], r['nivel'], '|', r['campos']['srv_formula'], '|', [h['h_ref'] for h in r['hallazgos']], '|',
              [i['i_situacion'] for i in r['tablas']['informativos']], '|', r['avisos'])
