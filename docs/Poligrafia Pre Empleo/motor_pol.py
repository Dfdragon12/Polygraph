"""Motor de referencia – Prueba de poligrafía pre-empleo. Modelo de calificación v1.8, con UNA excepción aprobada por Polygraph el 21/10/2026:
el robo en empleos de baja cuantía y ocasional (POL-05) suma 50 puntos en vez de ser crítico (capa 1). Debe quedar escrito en la versión 1.9 del modelo.
Especificación ejecutable: el Java debe dar los mismos resultados.

El poligrafista NO escoge códigos: registra el resultado de cada pregunta relevante y, por cada admisión, el texto (como hoy en Word), la categoría
y cuándo ocurrió. La plataforma deriva los códigos, el resultado general, el puntaje y las alertas.
"""

UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}
GRUPOS = ['A. Resultado de la prueba', 'B. Conducta laboral', 'C. Documentos e información del proceso', 'D. Delitos y vínculos',
          'E. Consumo de alcohol y drogas', 'F. Economía', 'G. Admisiones antiguas']
CATALOGO = {   # código: (grupo, hallazgo, puntos | 'Crítico')
    'POL-01': ('A', 'Resultado de la prueba: No veraz', 'Crítico'),
    'POL-02': ('A', 'Contramedidas o intento deliberado de manipular la prueba', 60),
    'POL-03': ('A', 'Dificultad para seguir las instrucciones durante el examen', 15),
    'POL-04': ('B', 'Robo en empleos (dinero, mercancía, materiales o información) por un valor total igual o mayor a un salario mínimo, o más de dos veces', 'Crítico'),
    'POL-05': ('B', 'Robo en empleos de baja cuantía: una o dos veces y por un valor total menor a un salario mínimo', 50),
    'POL-06': ('B', 'Recibir sobornos o comisiones por trabajar de forma irregular', 'Crítico'),
    'POL-07': ('B', 'Complicidad en ilícitos contra un empleador', 'Crítico'),
    'POL-08': ('B', 'Alteración u ocultamiento intencional de documentos, registros o información', 'Crítico'),
    'POL-09': ('B', 'Despido por mala conducta o faltas graves', 35),
    'POL-10': ('B', 'Abandono del cargo o retiros sin aviso (dejó de presentarse o se fue sin entregar el cargo)', 35),
    'POL-11': ('B', 'Conflicto de interés no declarado: negocios, sociedad o trabajo con proveedores, clientes o competencia del empleador', 35),
    'POL-12': ('B', 'Conductas agresivas o violentas sin relación con consumo: riñas frecuentes, agresiones a compañeros o jefes', 40),
    'POL-13': ('C', 'Uso de títulos, certificados o documentos falsos o comprados', 'Crítico'),
    'POL-14': ('C', 'Mintió en la hoja de vida o en el proceso de selección', 45),
    'POL-15': ('D', 'Beneficio económico de actividades ilícitas', 'Crítico'),
    'POL-16': ('D', 'Participación en venta, transporte o tráfico de drogas', 'Crítico'),
    'POL-17': ('D', 'Vínculo activo con grupos ilegales o delincuencia organizada', 'Crítico'),
    'POL-18': ('D', 'Otro delito admitido (por ejemplo, porte ilegal de armas)', 'Crítico'),
    'POL-19': ('D', 'Relación social actual con personas dedicadas a actividades ilícitas, sin participar', 45),
    'POL-20': ('D', 'Vínculo pasado con personas dedicadas a actividades ilícitas, ya terminado', 15),
    'POL-21': ('E', 'Consumo frecuente de drogas que puede afectar su trabajo o sus funciones (último año)', 60),
    'POL-22': ('E', 'Consumo ocasional de drogas en los últimos 5 años', 45),
    'POL-23': ('E', 'Trabajar o presentarse a trabajar bajo efectos de alcohol o drogas', 45),
    'POL-24': ('E', 'Problemas por consumo de alcohol: riñas, problemas familiares, sociales o laborales', 40),
    'POL-25': ('E', 'Problemas por consumo de drogas: riñas, problemas familiares, sociales o laborales', 40),
    'POL-26': ('E', 'Conducir bajo efectos de alcohol o drogas', 45),
    'POL-27': ('E', 'Conducir bajo efectos de alcohol o drogas, en cargos de conducción', 'Crítico'),
    'POL-28': ('F', 'Apuestas o juegos de azar que afectan su economía o generan deudas', 35),
    'POL-29': ('G', 'Admisiones no críticas de hace más de 5 años, o hechos cometidos siendo menor de edad', 10),
}
DEFINICIONES = {
    'POL-08': ('Adulteración, falsificación, modificación, supresión u ocultamiento intencional de documentos, registros, resultados, transacciones '
               'o información física o digital, de carácter laboral, comercial, contable, financiero, logístico, operativo, técnico o de cualquier '
               'otra naturaleza, con el propósito de obtener o facilitar beneficios indebidos, favorecer o perjudicar a la empresa o a terceros, '
               'encubrir irregularidades, alterar la realidad de los hechos o eludir controles establecidos. "Intencional" deja por fuera los '
               'errores (un inventario mal digitado por descuido no es hallazgo). "Favorecer o perjudicar a la empresa o a terceros" incluye al '
               'contador que maquilla los estados financieros para que la empresa se vea mejor, aunque él no gane nada. "Eludir controles '
               'establecidos" cubre marcar la asistencia de otro o saltarse una aprobación.'),
    'POL-05': 'Una o dos veces y por un valor total menor a un salario mínimo. Excepción a la capa 1 aprobada por Polygraph (21/10/2026).',
}
# Códigos de OTROS servicios que la poligrafía usa SOLO cuando ese servicio no está contratado en el estudio
# (regla del servicio no contratado). Se marcan "declarado por el evaluado, sin verificación en fuentes".
OTROS = {   # tipo declarado: (servicio dueño, código, hallazgo, puntos, ¿suma por cada uno?)
    'Orden de captura o requerimiento vigente': ('Antecedentes legales', 'LEG-03', 'Orden de captura o requerimiento judicial vigente', 'Crítico', False),
    'Proceso penal activo, relacionado con el cargo': ('Antecedentes legales', 'LEG-05', 'Proceso penal activo como investigado, relacionado con el cargo', 'Crítico', True),
    'Proceso penal activo, no relacionado con el cargo': ('Antecedentes legales', 'LEG-11', 'Proceso penal activo como investigado, no relacionado con el cargo', 70, True),
    'Sanción o inhabilidad disciplinaria vigente': ('Antecedentes legales', 'LEG-09', 'Sanción o inhabilidad disciplinaria vigente', 'Crítico', False),
    'Responsabilidad fiscal vigente': ('Antecedentes legales', 'LEG-08', 'Responsabilidad fiscal vigente', 45, False),
    'Medida correctiva': ('Antecedentes legales', 'LEG-18', 'Medida correctiva registrada, en cualquier estado', 30, False),
    'Proceso penal terminado sin condena': ('Antecedentes legales', 'LEG-19', 'Proceso penal terminado sin condena', 15, True),
    'Condena cumplida, relacionada con el cargo': ('Antecedentes legales', 'LEG-20', 'Condena cumplida por delito relacionado con el cargo', 30, True),
    'Condena cumplida, no relacionada con el cargo': ('Antecedentes legales', 'LEG-21', 'Condena cumplida por delito no relacionado con el cargo', 20, True),
    'Demanda no penal activa en contra': ('Antecedentes legales', 'LEG-22', 'Demanda no penal activa en contra (civil, laboral o de familia)', 10, True),
    'Demanda no penal terminada en contra': ('Antecedentes legales', 'LEG-23', 'Demanda no penal terminada en contra', 5, True),
    'Rotación laboral frecuente': ('Verificación laboral', 'LAB-15', 'Rotación laboral frecuente: 3 o más empleos con permanencia menor a 6 meses en '
                                                                  'los últimos 4 años, sin causa que lo explique', 30, False),
}
SERVICIOS_AVANZADO = ['Prueba de poligrafía', 'Visita domiciliaria', 'Antecedentes legales', 'Análisis financiero', 'Verificación académica',
                      'Verificación laboral', 'Referencias personales']
# Financiero declarado (solo cuando el análisis financiero NO está contratado). Cada código una vez, como en el financiero.
FINANCIERO = {
    'FIN-01': ('Obligación en cartera castigada sin pagar, o con mora vigente superior a 180 días', 60),
    'FIN-02': ('Obligación con mora vigente de 90 a 180 días', 45),
    'FIN-03': ('Moras de 30 a 89 días repetidas en el último año', 25),
    'FIN-04': ('Endeudamiento alto: las cuotas mensuales superan el 70 % de sus ingresos', 40),
    'FIN-05': ('Proceso ejecutivo o embargo vigente', 50),
    'FIN-08': ('Ocultamiento financiero: en el link negó moras, reportes o embargos que admite en la prueba', 40),
}
MORA = ['Al día', 'Menos de 30 días', '30 a 89 días, repetidas', '90 a 180 días', 'Más de 180 días o castigada']
MORA_COD = {'30 a 89 días, repetidas': 'FIN-03', '90 a 180 días': 'FIN-02', 'Más de 180 días o castigada': 'FIN-01'}
MOTIVOS_RETIRO = ['Renuncia voluntaria', 'Abandono o retiro sin aviso', 'Fin del contrato, obra o proyecto', 'Despido sin justa causa o liquidación',
                  'Despido con justa causa', 'Salud propia o de un familiar', 'Embarazo o cuidado de hijos', 'Estudio', 'Otro']
MOTIVOS_ROTACION = ['Renuncia voluntaria', 'Abandono o retiro sin aviso', 'Despido con justa causa']   # los demás nunca cuentan para la rotación
ALERTAS = {    # todas visibles al cliente
    'ALR-POL-01': 'Prueba no concluyente (No opinión) sin posibilidad de repetirla',
    'ALR-POL-02': 'No opinión: se debe repetir la prueba',
    'ALR-POL-03': 'El evaluado desistió del examen después de la entrevista',
    'ALR-POL-04': 'Admite deudas, moras o préstamos informales (pasa al análisis financiero)',
    'ALR-POL-05': 'Admite préstamos "gota a gota"',
}
CUANDO = ['En el último año', 'Hace 1 a 5 años', 'Hace más de 5 años', 'Siendo menor de edad']
FRECUENCIA = ['Frecuente', 'Ocasional', 'Nulo']          # consumo de alcohol y de drogas (áreas complementarias)
TECNICAS = ['AFMGQT', 'Utah']
CARGOS_CONDUCCION = ['Logística, transporte y conducción', 'Escoltas y transporte de valores']
# Categoría de la admisión -> (código base o regla, servicio al que se avisa, ¿se imprime?)
CATEGORIAS = {
    'Robo en empleos: un salario mínimo o más, o más de dos veces': ('POL-04', 'Verificación laboral', True),
    'Robo en empleos: menos de un salario mínimo, una o dos veces': ('POL-05', 'Verificación laboral', True),
    'Sobornos o comisiones irregulares': ('POL-06', 'Verificación laboral', True),
    'Complicidad en ilícitos contra un empleador': ('POL-07', 'Verificación laboral', True),
    'Alteración u ocultamiento de documentos, registros o información': ('POL-08', 'Verificación laboral', True),
    'Abandono del cargo o retiros sin aviso': ('POL-10', 'Verificación laboral', True),
    'Despido por mala conducta o faltas graves': ('POL-09', 'Verificación laboral', True),
    'Conflicto de interés no declarado': ('POL-11', None, True),
    'Conductas agresivas o violentas': ('POL-12', None, True),
    'Documentos falsos o comprados (títulos, certificados)': ('POL-13', 'Verificación académica / laboral', True),
    'Mintió en la hoja de vida o en el proceso': ('POL-14', None, True),
    'Beneficio de actividades ilícitas': ('POL-15', 'Antecedentes legales', True),
    'Venta, transporte o tráfico de drogas': ('POL-16', 'Antecedentes legales', True),
    'Vínculo activo con grupos ilegales': ('POL-17', 'Antecedentes legales', True),
    'Otro delito (incluye porte ilegal de armas)': ('POL-18', 'Antecedentes legales', True),
    'Relación social actual con personas en actividades ilícitas': ('POL-19', None, True),
    'Vínculo pasado con personas en actividades ilícitas (terminado)': ('POL-20', None, True),
    'Consumo frecuente de drogas (puede afectar su trabajo)': ('DROGA-F', None, True),
    'Consumo ocasional de drogas': ('DROGA-O', None, True),
    'Trabajar bajo efectos de alcohol o drogas': ('POL-23', None, True),
    'Problemas por consumo de alcohol': ('POL-24', None, True),
    'Problemas por consumo de drogas': ('POL-25', None, True),
    'Conducir bajo efectos de alcohol o drogas': ('CONDUCIR', None, True),
    'Apuestas o juegos de azar': ('POL-28', None, True),
    'Deudas, moras o préstamos': (None, 'Análisis financiero', True),
    'Préstamos gota a gota': (None, 'Análisis financiero', True),
    'Experiencia laboral, llamados de atención o descargos': (None, 'Verificación laboral', True),
    'Estudios': (None, 'Verificación académica', True),
    'Antecedentes, detenciones o medidas correctivas': (None, 'Antecedentes legales', True),
    'Ayudó en algo irregular sin saberlo': (None, None, True),
    'Otra información relevante': (None, None, True),
    'Personal o de salud (no se imprime)': (None, None, False),
}
RESULTADOS_PREGUNTA = ['Veraz', 'No veraz', 'Inconclusa']
ESTADOS_EXAMEN = ['Realizado', 'Sin examen: desistió después de la entrevista', 'Reprogramado']


def fmt1(x):
    return f'{float(x):.1f}'.replace('.', ',')


def nivel(p, crit):
    b, m = UMBRALES[crit]
    return 'BAJO' if p <= b else 'MEDIO' if p <= m else 'ALTO'


def es_critico(c):
    return CATALOGO[c][2] == 'Crítico'


def calificar(f, t):
    """f: campos (ev_criticidad, ev_tipo_cargo, examen_estado, nombre, pol_contramedidas, pol_dificultad_instr, pol_puede_repetir).
       t: tablas preguntas (pr_num, pr_pregunta, pr_resultado) y admisiones_reg (ad_texto, ad_categoria, ad_cuando)."""
    crit = f['ev_criticidad']
    avisos, avisos_srv, alertas = [], [], []
    codigos = {}
    estado_ex = f.get('examen_estado', 'Realizado')
    if estado_ex == 'Reprogramado':
        return dict(estado='Pendiente', campos={}, tablas={}, critico=False, puntaje=None, avisos=['Examen reprogramado: el servicio sigue pendiente'])

    contratados = f.get('servicios_contratados', SERVICIOS_AVANZADO)
    extra = []                    # hallazgos de otros catálogos (regla del servicio no contratado): (código, hallazgo, puntos)
    impresas = []
    for a in t.get('admisiones_reg', []):
        cod, srv, imp = CATEGORIAS[a['ad_categoria']]
        if a.get('ad_tipo') in OTROS:                     # antecedentes declarados: tipo y cantidad
            dueno, c_, h_, p_, acum = OTROS[a['ad_tipo']]
            if imp:
                impresas.append(dict(ad_texto=a['ad_texto']))
            if dueno in contratados:
                avisos_srv.append(f"{dueno}: {a['ad_tipo'].lower()} (declarado en la poligrafía)")
            else:
                for _ in range(int(a.get('ad_cantidad', 1)) if acum else 1):
                    extra.append((c_, h_ + ' (declarado por el evaluado, sin verificación en fuentes)', p_))
            continue
        cuando = a.get('ad_cuando', 'En el último año')
        antigua = cuando in ('Hace más de 5 años', 'Siendo menor de edad')
        if imp:
            impresas.append(dict(ad_texto=a['ad_texto']))
        if srv:
            avisos_srv.append(f"{srv}: {a['ad_categoria'].lower()} (admitido en la poligrafía)")
        if a['ad_categoria'] == 'Deudas, moras o préstamos':
            alertas.append(('ALR-POL-04', a['ad_texto']))
        if a['ad_categoria'] == 'Préstamos gota a gota':
            alertas.append(('ALR-POL-05', a['ad_texto']))
        if not cod:
            continue
        if cod == 'DROGA-F':
            cod = 'POL-21' if cuando == 'En el último año' else 'POL-22' if cuando == 'Hace 1 a 5 años' else 'POL-29'
        elif cod == 'DROGA-O':
            cod = 'POL-22' if cuando in ('En el último año', 'Hace 1 a 5 años') else 'POL-29'
        elif cod == 'CONDUCIR':
            cod = 'POL-27' if f.get('ev_tipo_cargo') in CARGOS_CONDUCCION and not antigua else 'POL-26'
            if antigua:
                cod = 'POL-29'
        elif es_critico(cod):
            if cuando == 'Siendo menor de edad':            # lo cometido siendo menor de edad no es crítico
                cod = 'POL-29'
        elif antigua:
            cod = 'POL-29'
        codigos.setdefault(cod, a['ad_texto'])
    cortos = [e for e in t.get('empleos_reg', []) if e.get('meses', 99) < 6 and e.get('motivo') in MOTIVOS_ROTACION
              and e.get('anios_desde_retiro', 0) <= 4]
    if len(cortos) >= 3:                                    # rotación laboral frecuente
        if 'Verificación laboral' in contratados:
            avisos_srv.append('Verificación laboral: posible rotación laboral frecuente (fechas declaradas en el link)')
        else:
            d_, c_, h_, p_, a_ = OTROS['Rotación laboral frecuente']
            extra.append((c_, h_ + ' (declarado por el evaluado, sin verificación en fuentes)', p_))
    if 'Análisis financiero' not in contratados:            # financiero declarado (solo poligrafía)
        fin = set()
        for a in t.get('admisiones_reg', []):
            if a['ad_categoria'] in ('Deudas, moras o préstamos', 'Préstamos gota a gota'):
                if a.get('ad_mora') in MORA_COD:
                    fin.add(MORA_COD[a['ad_mora']])
                if a.get('ad_embargo') == 'Sí':
                    fin.add('FIN-05')
        if {'FIN-01', 'FIN-02'} & fin:
            fin.discard('FIN-03')
        if 'FIN-01' in fin:
            fin.discard('FIN-02')
        moras_adm = bool({'FIN-01', 'FIN-02', 'FIN-03'} & fin) or any(a.get('ad_mora') not in (None, 'Al día') for a in t.get('admisiones_reg', [])
                                                                      if a['ad_categoria'] == 'Deudas, moras o préstamos')
        if (f.get('fd1_modulo_resp') == 'No' and moras_adm) or (f.get('fd2_modulo_resp') == 'No' and 'FIN-05' in fin):
            fin.add('FIN-08')
        ing, cuo = float(f.get('pol_ingreso_mensual') or 0), float(f.get('pol_cuotas_mensuales') or 0)
        if ing > 0 and cuo / ing > 0.70:
            fin.add('FIN-04')
        for c_ in sorted(fin):
            extra.append((c_, FINANCIERO[c_][0] + ' (declarado por el evaluado, sin verificación en fuentes)', FINANCIERO[c_][1]))
    if 'POL-21' in codigos:                                 # drogas: solo el más grave
        codigos.pop('POL-22', None)
    if 'POL-04' in codigos:                                 # robos: solo el más grave
        codigos.pop('POL-05', None)
    if f.get('pol_contramedidas') == 'Sí':
        codigos['POL-02'] = f.get('pol_contramedidas_det', '')
    if f.get('pol_dificultad_instr') == 'Sí':
        codigos['POL-03'] = f.get('pol_dificultad_instr_det', '')

    res = [p['pr_resultado'] for p in t.get('preguntas', [])]
    estado = 'Cerrado'
    if estado_ex.startswith('Sin examen'):
        resultado = 'SIN EXAMEN'
        alertas.append(('ALR-POL-03', 'Completó la entrevista y desistió antes del examen. Se califican las admisiones de la entrevista.'))
    elif 'No veraz' in res:
        resultado = 'NO VERAZ'; codigos['POL-01'] = 'Una o más preguntas relevantes con resultado No veraz'
    elif 'Inconclusa' in res:
        resultado = 'NO OPINIÓN'
        if f.get('pol_puede_repetir', 'Sí') == 'Sí':
            estado = 'Pendiente'; alertas.append(('ALR-POL-02', 'Se programa una nueva prueba.'))
        else:
            alertas.append(('ALR-POL-01', f.get('pol_no_repite_motivo', 'No fue posible repetir la prueba.')))
    else:
        resultado = 'VERAZ CON INFORMACIÓN' if impresas else 'VERAZ'
    if not res and not estado_ex.startswith('Sin examen'):
        avisos.append('Faltan las preguntas relevantes y su resultado')

    criticos = [c for c in codigos if es_critico(c)] + sorted({c for c, h, p in extra if p == 'Crítico'})
    orden = sorted(codigos, key=lambda c: int(c[4:]))
    hallazgos = [dict(h_ref=c, h_descripcion=CATALOGO[c][1], h_puntos='Crítico' if c in criticos else str(CATALOGO[c][2]), h_detalle=codigos[c])
                 for c in orden]
    hallazgos += [dict(h_ref=c, h_descripcion=h, h_puntos=str(p), h_detalle='') for c, h, p in sorted(extra)]
    if criticos:
        puntaje = 100; formula = f"Hallazgo crítico ({', '.join(sorted(set(criticos)))}): el servicio queda en 100"
    else:
        vals = [CATALOGO[c][2] for c in orden] + [p for c, h, p in sorted(extra)]
        puntaje = min(100, sum(vals))
        formula = (' + '.join(str(v) for v in vals) + f" = {puntaje}" + (' (tope 100)' if sum(vals) > 100 else '') + ' de 100 posibles') if vals else 'Sin hallazgos: 0 de 100 posibles'
    niv = 'ALTO' if criticos else nivel(puntaje, crit)
    marca = f"▲ {fmt1(puntaje)} puntos" + (' (crítico)' if criticos else '')

    nombre = f.get('nombre', 'el evaluado')
    concl = {
        'VERAZ': f'Después de un cuidadoso análisis de las gráficas poligráficas, es la opinión del examinador que {nombre} NO PRESENTÓ REACCIONES '
                 'NORMALMENTE INDICATIVAS DE ENGAÑO a las preguntas relevantes realizadas en la prueba.',
        'VERAZ CON INFORMACIÓN': f'Después de un cuidadoso análisis de las gráficas poligráficas, es la opinión del examinador que {nombre} NO PRESENTÓ '
                                 'REACCIONES NORMALMENTE INDICATIVAS DE ENGAÑO a las preguntas relevantes realizadas en la prueba. Durante la entrevista '
                                 'realizó las admisiones registradas en este informe.',
        'NO VERAZ': f'Después de un cuidadoso análisis de las gráficas poligráficas, es la opinión del examinador que {nombre} PRESENTÓ REACCIONES '
                    'SIGNIFICATIVAS INDICATIVAS DE ENGAÑO a una o más de las preguntas relevantes realizadas en la prueba.',
        'NO OPINIÓN': f'Después de un cuidadoso análisis de las gráficas poligráficas, no fue posible emitir una opinión concluyente sobre las respuestas '
                      f'de {nombre} a las preguntas relevantes.',
        'SIN EXAMEN': f'{nombre} completó la entrevista, pero desistió antes del examen. No se emite opinión sobre la veracidad de sus respuestas; '
                      'las admisiones de la entrevista se registran y califican en este informe.',
    }[resultado]
    campos = dict(pol_resultado=resultado, pol_conclusion=concl, srv_puntaje=fmt1(puntaje), srv_nivel=niv, srv_formula=formula,
                  sem_bajo=marca if niv == 'BAJO' else '', sem_medio=marca if niv == 'MEDIO' else '', sem_alto=marca if niv == 'ALTO' else '',
                  admisiones_nota='' if impresas else 'No realizó admisiones relevantes.',
                  hallazgos_nota=('Este estudio no incluye ' + ' ni '.join(sorted({d.lower() for d in [OTROS[k][0] for k in OTROS] + ['Análisis financiero']} - {x.lower() for x in contratados}))
                                  + '; los hallazgos marcados "declarado por el evaluado" provienen de lo que el evaluado declaró en la prueba.')
                                 if extra else ('' if hallazgos else 'Sin hallazgos con impacto en el nivel de riesgo.'),
                  alertas_nota='' if alertas else 'Sin alertas.')
    tablas = dict(preguntas=t.get('preguntas', []), admisiones=impresas, hallazgos=hallazgos,
                  alertas=[dict(a_codigo=c, a_alerta=ALERTAS[c], a_detalle=d) for c, d in sorted(alertas, key=lambda x: x[0])])
    return dict(estado=estado, campos=campos, tablas=tablas, critico=bool(criticos), puntaje=puntaje, nivel=niv, avisos=avisos,
                avisos_servicios=avisos_srv)


# ---------------------------------------------------------------- casos de prueba
def P(*res):
    return [dict(pr_num=f'R{i}', pr_pregunta='—', pr_resultado=r) for i, r in enumerate(res, 1)]


def A(cat, cuando='En el último año', txt='—'):
    return dict(ad_texto=txt, ad_categoria=cat, ad_cuando=cuando)


BASE = dict(ev_criticidad='Estándar', ev_tipo_cargo='Cargos operativos y de apoyo', examen_estado='Realizado', nombre='el evaluado')
V3 = P('Veraz', 'Veraz', 'Veraz')
CASOS = {
    'P1 Veraz sin admisiones': (BASE, dict(preguntas=P('Veraz', 'Veraz', 'Veraz', 'Veraz'))),
    'P2 Consumo ocasional este año + amigos que venden + deudas': (BASE, dict(preguntas=V3, admisiones_reg=[
        A('Consumo ocasional de drogas'), A('Relación social actual con personas en actividades ilícitas'), A('Deudas, moras o préstamos')])),
    'P3 Robo de alta cuantía (crítico aunque pasó la prueba)': (BASE, dict(preguntas=V3, admisiones_reg=[A('Robo en empleos: un salario mínimo o más, o más de dos veces', 'Hace 1 a 5 años')])),
    'P4 Robo de baja cuantía y ocasional (excepción: 50)': (BASE, dict(preguntas=V3, admisiones_reg=[A('Robo en empleos: menos de un salario mínimo, una o dos veces', 'Hace 1 a 5 años')])),
    'P5 No veraz': (BASE, dict(preguntas=P('Veraz', 'No veraz', 'Veraz'))),
    'P6 No opinión, se puede repetir → pendiente': (BASE, dict(preguntas=P('Veraz', 'Inconclusa', 'Veraz'))),
    'P7 No opinión sin repetir': (dict(BASE, pol_puede_repetir='No'), dict(preguntas=P('Inconclusa', 'Veraz', 'Veraz'))),
    'P8 Sin examen con robo de alta cuantía': (dict(BASE, examen_estado='Sin examen: desistió después de la entrevista'),
                                               dict(admisiones_reg=[A('Robo en empleos: un salario mínimo o más, o más de dos veces'), A('Consumo ocasional de drogas')])),
    'P9 Conducir bajo efectos, cargo de conducción → crítico': (dict(BASE, ev_tipo_cargo='Logística, transporte y conducción'),
                                                                dict(preguntas=V3, admisiones_reg=[A('Conducir bajo efectos de alcohol o drogas')])),
    'P10 Conducir bajo efectos, cargo operativo → 45': (BASE, dict(preguntas=V3, admisiones_reg=[A('Conducir bajo efectos de alcohol o drogas')])),
    'P11 Hurto siendo menor y consumo hace 8 años → POL-29 una vez': (BASE, dict(preguntas=V3, admisiones_reg=[
        A('Robo en empleos: un salario mínimo o más, o más de dos veces', 'Siendo menor de edad'), A('Consumo ocasional de drogas', 'Hace más de 5 años')])),
    'P12 Dificultad para seguir instrucciones + despido + problemas por alcohol (Sensible)': (
        dict(BASE, pol_dificultad_instr='Sí', ev_criticidad='Sensible'), dict(preguntas=V3, admisiones_reg=[
            A('Despido por mala conducta o faltas graves', 'Hace 1 a 5 años'), A('Problemas por consumo de alcohol')])),
    'P13 Consumo frecuente y ocasional → solo POL-21': (BASE, dict(preguntas=V3, admisiones_reg=[
        A('Consumo frecuente de drogas (puede afectar su trabajo)'), A('Consumo ocasional de drogas', 'Hace 1 a 5 años')])),
    'P14 Gota a gota + apuestas + conflicto de interés': (BASE, dict(preguntas=V3, admisiones_reg=[
        A('Préstamos gota a gota'), A('Apuestas o juegos de azar'), A('Conflicto de interés no declarado')])),
    'P15 Solo admisión personal o de salud → Veraz, nada impreso': (BASE, dict(preguntas=V3, admisiones_reg=[A('Personal o de salud (no se imprime)')])),
    'P16 Solo poligrafía: 3 medidas correctivas + proceso terminado x2 → LEG-18 una vez, LEG-19 dos veces': (
        dict(BASE, servicios_contratados=['Prueba de poligrafía']), dict(preguntas=V3, admisiones_reg=[
            dict(ad_texto='—', ad_categoria='Antecedentes, detenciones o medidas correctivas', ad_tipo='Medida correctiva', ad_cantidad=3),
            dict(ad_texto='—', ad_categoria='Antecedentes, detenciones o medidas correctivas', ad_tipo='Proceso penal terminado sin condena', ad_cantidad=2)])),
    'P17 Avanzado: mismas declaraciones → solo aviso a antecedentes, 0 puntos': (BASE, dict(preguntas=V3, admisiones_reg=[
        dict(ad_texto='—', ad_categoria='Antecedentes, detenciones o medidas correctivas', ad_tipo='Medida correctiva', ad_cantidad=3)])),
    'P18 Solo poligrafía: rotación (3 empleos cortos por renuncia) → LAB-15': (dict(BASE, servicios_contratados=['Prueba de poligrafía']), dict(
        preguntas=V3, empleos_reg=[dict(meses=3, motivo='Renuncia voluntaria', anios_desde_retiro=1), dict(meses=4, motivo='Abandono o retiro sin aviso', anios_desde_retiro=2),
                                   dict(meses=5, motivo='Renuncia voluntaria', anios_desde_retiro=3), dict(meses=4, motivo='Fin del contrato, obra o proyecto', anios_desde_retiro=1)])),
    'P19 Solo poligrafía: retiros cortos por salud y fin de contrato → no suma': (dict(BASE, servicios_contratados=['Prueba de poligrafía']), dict(
        preguntas=V3, empleos_reg=[dict(meses=3, motivo='Salud propia o de un familiar', anios_desde_retiro=1), dict(meses=4, motivo='Fin del contrato, obra o proyecto', anios_desde_retiro=2),
                                   dict(meses=5, motivo='Renuncia voluntaria', anios_desde_retiro=3)])),
    'P20 Alteración de inventarios (POL-08) + abandono (POL-10)': (BASE, dict(preguntas=V3, admisiones_reg=[
        A('Alteración u ocultamiento de documentos, registros o información'), A('Abandono del cargo o retiros sin aviso', 'Hace 1 a 5 años')])),
    'P21 Solo poligrafía: mora 120 días + embargo, en el link dijo No → FIN-02 + FIN-05 + FIN-08': (
        dict(BASE, servicios_contratados=['Prueba de poligrafía'], fd1_modulo_resp='No', fd2_modulo_resp='No'), dict(preguntas=V3, admisiones_reg=[
            dict(ad_texto='—', ad_categoria='Deudas, moras o préstamos', ad_mora='90 a 180 días', ad_embargo='Sí')])),
    'P22 Avanzado: misma deuda → solo alerta y consolidado financiero': (dict(BASE, fd1_modulo_resp='No'), dict(preguntas=V3, admisiones_reg=[
        dict(ad_texto='—', ad_categoria='Deudas, moras o préstamos', ad_mora='90 a 180 días', ad_embargo='Sí')])),
    'P23 Solo poligrafía: cuotas 1.300.000 de ingresos 1.600.000 → FIN-04': (
        dict(BASE, servicios_contratados=['Prueba de poligrafía'], pol_ingreso_mensual=1600000, pol_cuotas_mensuales=1300000), dict(preguntas=V3)),
}

if __name__ == '__main__':
    for n, (f, t) in CASOS.items():
        r = calificar(f, t)
        c = r['campos']
        print(n, '|', r['estado'], c.get('pol_resultado'), '|', [h['h_ref'] + ':' + h['h_puntos'] for h in r['tablas'].get('hallazgos', [])], '|',
              c.get('srv_formula'), '|', r.get('nivel'), '|', [a['a_codigo'] for a in r['tablas'].get('alertas', [])], '|', len(r['tablas'].get('admisiones', [])))
