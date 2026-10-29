"""Motor de referencia – Antecedentes legales (reglas acordadas el 02/10/2026).
Sirve para: (1) generar los ejemplos, (2) dar a Cursor casos de prueba con resultados esperados."""

CATALOGO = {  # código: (descripción, puntos o 'Crítico', por_proceso, solo_conduccion)
    'LEG-01': ('Documento cancelado o no encontrado en la Registraduría', 'Crítico', False, False),
    'LEG-02': ('Coincidencia confirmada en lista vinculante o restrictiva (ONU, OFAC, HM Treasury)', 'Crítico', False, False),
    'LEG-03': ('Orden de captura o requerimiento judicial vigente', 'Crítico', False, False),
    'LEG-04': ('Notificación o búsqueda vigente de Interpol o Europol', 'Crítico', False, False),
    'LEG-05': ('Proceso penal activo o pena vigente como investigado, relacionado con el cargo', 'Crítico', True, False),
    'LEG-06': ('Registrado como proveedor ficticio en la DIAN', 50, False, False),
    'LEG-07': ('Inhabilitado por el Banco Mundial o el BID', 50, False, False),
    'LEG-08': ('Responsabilidad fiscal vigente (Contraloría)', 45, False, False),
    'LEG-09': ('Sanción o inhabilidad disciplinaria vigente (Procuraduría, Personería)', 'Crítico', False, False),
    'LEG-10': ('Licencia de conducción suspendida o cancelada (solo cargos de conducción)', 'Crítico', False, True),
    'LEG-11': ('Proceso penal activo o pena vigente como investigado, no relacionado con el cargo', 70, True, False),
    'LEG-12': ('Omitió declarar un antecedente vigente', 25, False, False),
    'LEG-13': ('Información adversa en noticias criminales o medios', 40, False, False),
    'LEG-14': ('Licencia vencida o categoría que no corresponde al cargo (solo cargos de conducción)', 40, False, True),
    'LEG-15': ('Multa o sanción como contratista del Estado (SECOP)', 20, False, False),
    'LEG-16': ('Reportado como deudor moroso del Estado (BDME)', 15, False, False),
    'LEG-17': ('Multas de tránsito pendientes sin acuerdo de pago (solo cargos de conducción)', 20, False, True),
    'LEG-18': ('Medida correctiva registrada (RNMC), en cualquier estado', 30, False, False),
    'LEG-19': ('Proceso penal terminado sin condena (archivo, prescripción, desistimiento, conciliación o principio de oportunidad)', 15, True, False),
    'LEG-20': ('Condena cumplida por delito relacionado con el cargo', 30, True, False),
    'LEG-21': ('Condena cumplida por delito no relacionado con el cargo', 20, True, False),
    'LEG-22': ('Demanda no penal activa en contra (civil, laboral o de familia)', 10, True, False),
    'LEG-23': ('Demanda no penal terminada en contra (no aplica si el fallo fue a favor del evaluado)', 5, True, False),
    'LEG-24': ('Suplantación de identidad confirmada (el evaluado usa una identidad ajena)', 'Crítico', False, False),
}
UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}
ESTADOS_LEG01 = {'Cancelada por muerte', 'Cancelada por doble cedulación', 'No encontrada'}


def fmt(x):
    return f'{x:.1f}'.replace('.', ',')


def calificar(f, t):
    """f = campos del formulario (incluye ev_criticidad, que no se imprime); t = tablas (listas de dicts)."""
    cond = f.get('ev_conduccion') == 'Sí'
    cand = []  # (código, fuente, detalle)
    if f.get('ev_estado_doc') in ESTADOS_LEG01:
        cand.append(('LEG-01', 'Registraduría', f"Estado del documento: {f['ev_estado_doc']}"))
    no = [str(i) for i in (1, 2, 3) if f.get(f'dec{i}_coincide') == 'No']
    if no:
        cand.append(('LEG-12', 'Declaración del evaluado', 'La respuesta a la(s) pregunta(s) ' + ', '.join(no) + ' no coincide con lo verificado'))
    for r in t.get('listas_nacionales', []):
        if r.get('ln_ref', '—') in CATALOGO:
            cand.append((r['ln_ref'], r['ln_fuente'], r.get('ln_observacion', '')))
    for r in t.get('listas_internacionales', []):
        if r.get('li_ref', '—') in CATALOGO:
            cand.append((r['li_ref'], r['li_lista'], r.get('li_observacion', '')))
    if f.get('simit_ref', '—') in CATALOGO:
        cand.append((f['simit_ref'], 'SIMIT', f"Multas: {f.get('simit_multas', '')}"))
    for r in t.get('runt', []):
        if r.get('runt_ref', '—') in CATALOGO:
            cand.append((r['runt_ref'], 'RUNT', f"Categoría {r['runt_categoria']}: {r['runt_estado']}"))
    for r in t.get('alertas_id', []):
        if r.get('a_ref', '—') in CATALOGO:
            cand.append((r['a_ref'], r['a_fuente'], r.get('a_descripcion', '')))
    for key, pre, fuente in (('procesos', 'p_', 'Rama Judicial'), ('procesos_privados', 'pp_', 'Bases privadas')):
        for r in t.get(key, []):
            c = r.get(pre + 'calificacion', '—')
            if c in CATALOGO:
                cand.append((c, fuente, f"Proceso N° {r[pre + 'numero']}: {r[pre + 'delito']} ({r[pre + 'estado']})"))
    hall, vistos, avisos = [], set(), []
    for code, fuente, det in cand:
        desc, pts, por_proc, solo_cond = CATALOGO[code]
        if solo_cond and not cond:
            avisos.append(f'{code} ignorado: el cargo no implica conducción'); continue
        if not por_proc and code in vistos:
            continue
        vistos.add(code)
        hall.append(dict(h_ref=code, h_descripcion=desc, h_fuente=fuente, h_detalle=det, h_puntos=pts if pts == 'Crítico' else str(pts)))
    criticos = [h['h_ref'] for h in hall if h['h_puntos'] == 'Crítico']
    nums = [int(h['h_puntos']) for h in hall if h['h_puntos'] != 'Crítico']
    bruto = sum(nums)
    if criticos:
        puntaje, nivel = 100.0, 'ALTO'
        formula = 'Hallazgo crítico (' + ', '.join(dict.fromkeys(criticos)) + '): nivel ALTO'
    else:
        puntaje = float(min(100, bruto))
        b, m = UMBRALES[f['ev_criticidad']]
        nivel = 'BAJO' if puntaje <= b else 'MEDIO' if puntaje <= m else 'ALTO'
        formula = ('Sin hallazgos que sumen puntos' if not nums else
                   ' + '.join(map(str, nums)) + (f' = {bruto} → 100 (tope máximo)' if bruto > 100 else f' = {bruto} de 100 posibles'))
    marca = '▲ Hallazgo crítico' if criticos else f'▲ {fmt(puntaje)} puntos'
    out = dict(srv_puntaje=fmt(puntaje), srv_formula=formula,
               sem_bajo=marca if nivel == 'BAJO' else '', sem_medio=marca if nivel == 'MEDIO' else '', sem_alto=marca if nivel == 'ALTO' else '',
               transito_nota='' if cond else 'Informativo: el cargo no implica conducción; los resultados de tránsito no suman puntos.',
               alertas_id_nota='' if t.get('alertas_id') else 'Sin alertas reportadas.',
               procesos_nota='' if t.get('procesos') else 'No se encontraron procesos judiciales en Rama Judicial.',
               procesos_priv_nota='' if t.get('procesos_privados') else 'No se encontraron procesos en bases privadas.')
    return dict(campos=out, hallazgos=hall, nivel=nivel, puntaje=puntaje, criticos=criticos, avisos=avisos)
