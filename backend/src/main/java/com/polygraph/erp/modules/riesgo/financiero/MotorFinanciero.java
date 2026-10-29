package com.polygraph.erp.modules.riesgo.financiero;

import com.polygraph.erp.modules.riesgo.codigos.CalificadorPorCodigos;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.AvisoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoCatalogo;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoEncontrado;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.ResultadoCalificacionCodigos;
import com.polygraph.erp.modules.riesgo.hallazgos.MotorHallazgos;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;
import com.polygraph.erp.shared.enums.CriticidadCargo;

import java.util.*;

/**
 * Calificación de "Antecedentes financieros": traducción fiel de calificar() y validar() de
 * docs/Analisis Financiero/motor_fin.py (manda el .py). Cálculo puro, sin Spring ni JPA.
 *
 * Fuentes: en TODOS los estudios, la consulta de síntesis de centrales de riesgo (totales por sector,
 * barra de comportamiento de 12 meses, análisis de ingresos). Solo en OEA, además, el reporte detallado
 * (una fila por obligación negativa) y la declaración de renta y patrimonio.
 *
 * No usa el MotorHallazgos genérico porque las reglas dependen del tipo de estudio (OEA), de rangos
 * (meses de mora) y de la banda del score; sí usa CalificadorPorCodigos para sumar, topar y dar nivel.
 */
public final class MotorFinanciero {

    /** Si la plantilla tiene este campo, califica con este motor y no con el genérico. */
    public static final String CAMPO_DETECCION = "fin_meses_mora_90";

    public static final int LIMITE_ENDEUDAMIENTO = 70;   // % de utilización del ingreso estimado
    public static final int MESES_FIN03 = 3;             // meses con mora 30 o 60 en el último año
    public static final int MESES_FIN01 = 5;             // meses seguidos con mora 90 o más => más de 180 días
    static final String SALVEDAD = "Alcance: la consulta de síntesis muestra los totales por sector y el comportamiento de pago de los "
            + "últimos 12 meses. No identifica la entidad de cada obligación, la cartera castigada ni las obligaciones como codeudor en mora.";
    static final String[] SECTORES = {"fin", "real", "coop", "tel"};
    static final String[] COLUMNAS = {"vig", "cer", "pri", "cod", "cuota", "mora"};
    private static final String OMITIO = "No coincide – omitió";

    /** Banda del indicador de pago de una central (se configura por central). */
    public record Banda(int desde, int hasta, String nombre) {}

    /** Las bandas de la central actual, para quien no tiene una configurada. */
    public static final List<Banda> BANDAS_POR_DEFECTO = List.of(new Banda(150, 400, "Muy bajo"), new Banda(401, 500, "Bajo"),
            new Banda(501, 600, "Medio"), new Banda(601, 950, "Alto"));

    private MotorFinanciero() {}

    public static boolean aplica(MotorHallazgos.Plantilla p) {
        return p.secciones().stream().flatMap(s -> s.columnas().stream()).anyMatch(c -> CAMPO_DETECCION.equals(c.clave()));
    }

    // ─────────────────────────── utilidades (num, miles, fmt de motor_fin.py) ───────────────────────────

    /** "1.120" → 1120; null si no es un número. */
    static Integer numeroONulo(String v) {
        if (v == null) return null;
        try {
            return Integer.parseInt(v.replace(".", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int num(String v) {
        Integer n = numeroONulo(v);
        return n == null ? 0 : n;
    }

    static String miles(int n) {
        return String.format(Locale.US, "%,d", n).replace(',', '.');
    }

    private static String vacioSi(String v) {
        return v == null ? "" : v;
    }

    // ─────────────────────────── validar() ───────────────────────────

    static List<AvisoFormulario> validar(boolean oea, Map<String, String> f, Map<String, List<Map<String, String>>> t) {
        List<AvisoFormulario> av = new ArrayList<>();
        int m90 = num(f.get("fin_meses_mora_90"));
        int moraTot = 0;
        for (String k : SECTORES) moraTot += num(f.get("s_" + k + "_mora"));
        List<Map<String, String>> obligaciones = t.getOrDefault("obligaciones_negativas", List.of());
        List<Map<String, String>> patrimonios = t.getOrDefault("patrimonios", List.of());
        String sintesis = "Consulta de síntesis";
        if (m90 > 0 && moraTot == 0) {
            av.add(new AvisoFormulario("Hay meses con mora 90 o más pero el saldo en mora es 0: revise la consulta de síntesis.", sintesis));
        }
        if (moraTot > 0 && m90 == 0 && num(f.get("fin_meses_mora_30_60")) == 0) {
            av.add(new AvisoFormulario("Hay saldo en mora pero ningún mes en mora en la barra: revise la consulta de síntesis.", sintesis));
        }
        if (oea && moraTot > 0 && obligaciones.isEmpty()) {
            av.add(new AvisoFormulario("Estudio OEA con saldo en mora y sin obligaciones registradas en la sección 8: registre el detalle del reporte detallado.", "Obligaciones con reporte negativo"));
        }
        if (oea && patrimonios.isEmpty()) {
            av.add(new AvisoFormulario("Estudio OEA sin bienes registrados: si no tiene bienes, registre una fila \"No registra\".", "Patrimonio"));
        }
        for (Map<String, String> p : patrimonios) {
            String estado = vacioSi(p.get("pt_estado"));
            if (("Nuevo".equals(estado) || "Ya no aparece".equals(estado)) && vacioSi(p.get("pt_explicacion")).isBlank()
                    && !"Sí".equals(f.get("oea_inconsistencia"))) {
                av.add(new AvisoFormulario("Bien \"" + vacioSi(p.get("pt_tipo")) + "\": marcado \"" + estado
                        + "\" sin explicación; revise si corresponde FIN-07.", "Patrimonio"));
            }
        }
        if (vacioSi(f.get("fin_endeudamiento")).isBlank() && !"No reportado".equals(f.get("fin_ingreso"))) {
            av.add(new AvisoFormulario("Falta el % de utilización del ingreso estimado (o marque el ingreso como \"No reportado\").", "Análisis de ingresos"));
        }
        return av;
    }

    // ─────────────────────────── calificar() ───────────────────────────

    /** Código de una obligación del reporte detallado (solo OEA); null si no suma. */
    static String refObligacion(Map<String, String> o) {
        int dias = num(o.get("o_dias"));
        if ("Saldada castigada".equals(o.get("o_estado"))) return null;          // informativo
        if ("Codeudor y otros".equals(o.get("o_calidad"))) return "FIN-06";
        if ("Castigada".equals(o.get("o_estado")) || dias > 180) return "FIN-01";
        if (dias >= 90) return "FIN-02";
        return null;                                                              // menos de 90 días: cuenta en la barra (FIN-03)
    }

    /**
     * @param oea       el estudio es OEA (lo decide el tipo de estudio, no el analista)
     * @param bandas    bandas del indicador de pago de la central consultada
     * @param f         campos diligenciados por clave
     * @param t         tablas diligenciadas por clave (obligaciones_negativas, patrimonios, informativos_analista)
     */
    public static CalculoFormulario calcular(Map<String, CodigoCatalogo> catalogo, Map<CriticidadCargo, Umbrales> umbrales, List<Banda> bandas,
                                             boolean oea, Map<String, String> f, Map<String, List<Map<String, String>>> t) {
        List<AvisoFormulario> avisos = validar(oea, f, t);
        List<CodigoEncontrado> encontrados = new ArrayList<>();
        Map<String, String> campos = new LinkedHashMap<>();

        // --- totales de la síntesis (en miles)
        for (String c : COLUMNAS) {
            int tot = 0;
            for (String k : SECTORES) {
                int v = num(f.get("s_" + k + "_" + c));
                campos.put("s_" + k + "_" + c, miles(v));
                tot += v;
            }
            campos.put("s_tot_" + c, miles(tot));
        }
        campos.put("cr_cuotas", "$" + campos.get("s_tot_cuota") + " mil");
        int moraTot = num(campos.get("s_tot_mora"));

        // --- comportamiento de los últimos 12 meses (barra de la síntesis) y obligaciones del reporte detallado
        int m90 = num(f.get("fin_meses_mora_90"));
        int m3060 = num(f.get("fin_meses_mora_30_60"));
        List<String> cp = new ArrayList<>();
        List<Map<String, String>> filasObligaciones = new ArrayList<>();
        boolean hayNegativas = false;
        boolean algunaObligacionConCodigo = false;
        for (Map<String, String> o : t.getOrDefault("obligaciones_negativas", List.of())) {
            Map<String, String> calculada = new LinkedHashMap<>();
            if (oea) {
                String code = refObligacion(o);
                int dias = num(o.get("o_dias"));
                String diasMora = dias != 0 ? dias + " días" : "—";
                String valorMora = miles(num(o.get("o_valor")));
                calculada.put("o_ref", code == null ? "—" : code);
                calculada.put("o_dias_mora", diasMora);
                calculada.put("o_valor_mora", valorMora);
                hayNegativas = true;
                if (code != null) {
                    algunaObligacionConCodigo = true;
                    encontrados.add(new CodigoEncontrado(code, "Reporte detallado", vacioSi(o.get("o_entidad")) + " (" + vacioSi(o.get("o_tipo")) + "), "
                            + vacioSi(o.get("o_calidad")).toLowerCase() + ", " + vacioSi(o.get("o_estado")).toLowerCase() + ", " + diasMora + ", $" + valorMora + " mil"));
                }
            } else {
                calculada.put("o_ref", "");
                calculada.put("o_dias_mora", "");
                calculada.put("o_valor_mora", "");
            }
            filasObligaciones.add(calculada);
        }
        if (oea) {
            if (m90 != 0 && !algunaObligacionConCodigo) {
                avisos.add(new AvisoFormulario("La barra muestra mora 90 o más pero ninguna obligación del reporte detallado tiene 90 días o más: revise.", "Obligaciones con reporte negativo"));
            }
            if (m90 != 0) cp.add("Moras de 90 días o más: ver sección 8");
        } else {                                                  // solo síntesis: FIN-01 / FIN-02 una vez por estudio
            if (m90 >= MESES_FIN01) {
                encontrados.add(new CodigoEncontrado("FIN-01", "Consulta de síntesis", "Mora 90 o más en los " + m90
                        + " meses más recientes (más de 180 días); saldo en mora $" + miles(moraTot) + " mil"));
                cp.add("FIN-01 (" + m90 + " meses seguidos con mora 90 o más)");
            } else if (m90 >= 1) {
                String meses = m90 == 1 ? "el mes más reciente" : "los " + m90 + " meses más recientes";
                encontrados.add(new CodigoEncontrado("FIN-02", "Consulta de síntesis", "Mora 90 o más en " + meses + "; saldo en mora $" + miles(moraTot) + " mil"));
                cp.add("FIN-02 (" + m90 + " " + (m90 == 1 ? "mes" : "meses") + " con mora 90 o más)");
            }
        }
        if (m3060 >= MESES_FIN03) {
            encontrados.add(new CodigoEncontrado("FIN-03", "Consulta de síntesis", m3060 + " meses con mora 30 o 60 en el último año"));
            cp.add("FIN-03 (" + m3060 + " meses con mora 30 o 60)");
        }
        campos.put("cp_ref", cp.isEmpty() ? "—" : String.join(" · ", cp));

        // --- análisis de ingresos
        String pct = f.get("fin_endeudamiento");
        if ("No reportado".equals(f.get("fin_ingreso")) || vacioSi(pct).isBlank()) {
            campos.put("fin_ingreso", "No reportado");
            campos.put("fin_endeudamiento", "No reportado");
            campos.put("fin_endeudamiento_ref", "— (sin ingreso estimado, FIN-04 no aplica)");
        } else {
            int p = num(pct);
            campos.put("fin_ingreso", "$" + miles(num(f.get("fin_ingreso"))) + " mil");
            campos.put("fin_endeudamiento", p + " %");
            if (p > LIMITE_ENDEUDAMIENTO) {
                encontrados.add(new CodigoEncontrado("FIN-04", "Consulta de síntesis", "Utilización del ingreso estimado: " + p + " %"));
                campos.put("fin_endeudamiento_ref", "FIN-04 (" + p + " %, supera el 70 %)");
            } else {
                campos.put("fin_endeudamiento_ref", "— (no supera el 70 %)");
            }
        }

        // --- ejecutivo / embargo (FIN-05) y ocultamiento (FIN-08)
        if ("Sí".equals(f.get("fin_ejecutivo"))) {
            encontrados.add(new CodigoEncontrado("FIN-05", "Antecedentes judiciales", vacioSi(f.get("fin_ejecutivo_detalle"))));
        }
        List<String> omitidos = new ArrayList<>();
        String[][] temas = {{"ing", "ingresos"}, {"deu", "deudas y cuotas"}, {"mor", "moras y reportes"}, {"bie", "bienes"}};
        for (String[] tema : temas) {
            if (vacioSi(f.get("c_" + tema[0] + "_res")).startsWith(OMITIO)) omitidos.add(tema[1]);
        }
        if (!omitidos.isEmpty()) {
            String detalle = vacioSi(f.get("fin_ocultamiento_detalle")).isBlank() ? "Omitió u ocultó: " + String.join(", ", omitidos) : f.get("fin_ocultamiento_detalle");
            encontrados.add(new CodigoEncontrado("FIN-08", "Consolidado financiero", detalle));
        }

        // --- patrimonio (OEA, FIN-07)
        List<Map<String, String>> filasPatrimonios = new ArrayList<>();
        for (Map<String, String> p : t.getOrDefault("patrimonios", List.of())) {
            String explicacion = vacioSi(p.get("pt_explicacion"));
            filasPatrimonios.add(new LinkedHashMap<>(Map.of("pt_novedad", oea ? vacioSi(p.get("pt_estado")) + (explicacion.isBlank() ? "" : ": " + explicacion) : "")));
        }
        if (oea && "Sí".equals(f.get("oea_inconsistencia"))) {
            encontrados.add(new CodigoEncontrado("FIN-07", "Declaración de renta y patrimonio", vacioSi(f.get("oea_inconsistencia_detalle"))));
        }

        // --- indicador de pago (informativo)
        Integer score = vacioSi(f.get("cr_score")).isBlank() ? null : numeroONulo(f.get("cr_score"));
        Integer indiceBanda = null;
        String nombreBanda = null;
        for (int i = 1; i <= 4; i++) {
            Banda b = bandas != null && i <= bandas.size() ? bandas.get(i - 1) : null;
            campos.put("cr_banda_" + i, b == null ? "" : b.desde() + " a " + b.hasta() + " " + b.nombre());
            campos.put("cr_m" + i, "");
            if (b != null && score != null && b.desde() <= score && score <= b.hasta()) {
                campos.put("cr_m" + i, "▲");
                indiceBanda = i;
                nombreBanda = b.nombre();
            }
        }
        campos.put("cr_score", score != null ? String.valueOf(score) : "No reportado");
        List<Map<String, String>> informativos = new ArrayList<>();
        if (score != null) {
            informativos.add(new LinkedHashMap<>(Map.of("i_situacion", "Indicador de pago (score)",
                    "i_detalle", score + " – banda " + (nombreBanda != null ? nombreBanda : "fuera de rango"),
                    "i_tratamiento", "Informativo: no suma puntos.")));
        }
        for (Map<String, String> i : t.getOrDefault("informativos_analista", List.of())) {
            informativos.add(new LinkedHashMap<>(Map.of("i_situacion", vacioSi(i.get("ia_situacion")), "i_detalle", vacioSi(i.get("ia_detalle")),
                    "i_tratamiento", vacioSi(i.get("ia_tratamiento")))));
        }
        if (indiceBanda != null && indiceBanda == 1 && encontrados.isEmpty()) {
            avisos.add(new AvisoFormulario("ALR-FIN-01: score en la banda más baja sin ningún código FIN marcado. Revise el reporte antes de cerrar.", "Indicador de pago"));
        }

        // --- secciones OEA y textos que dependen del tipo de estudio
        campos.put("cr_salvedad", oea ? "" : SALVEDAD);
        campos.put("sec_concepto", oea ? "10" : "8");
        List<Map<String, String>> oeaTabla = new ArrayList<>();
        if (oea) {
            Map<String, String> fila = new LinkedHashMap<>();
            fila.put("oea_negativas_nota", hayNegativas ? "" : "No registra obligaciones con reporte negativo en el reporte detallado.");
            fila.put("oea_estudio_anterior", vacioSi(f.get("oea_anterior")).isBlank() ? "Primer estudio" : f.get("oea_anterior"));
            fila.put("oea_observaciones", vacioSi(f.get("oea_obs")));
            oeaTabla.add(fila);
        }

        Map<String, List<Map<String, String>>> tablas = new LinkedHashMap<>();
        tablas.put("obligaciones_negativas", filasObligaciones);
        tablas.put("patrimonios", filasPatrimonios);
        tablas.put("informativos", informativos);
        tablas.put("oea", oeaTabla);

        // --- puntaje, fórmula, nivel
        CriticidadCargo criticidad = MotorHallazgos.criticidad(f.get("ev_criticidad"));
        if (criticidad == null || !umbrales.containsKey(criticidad)) {
            for (String k : new String[]{"srv_puntaje", "srv_formula", "sem_bajo", "sem_medio", "sem_alto"}) campos.put(k, "");
            tablas.put("hallazgos", List.of());
            return new CalculoFormulario(campos, tablas, null, MotorHallazgos.FALTA_CRITICIDAD, null, avisos);
        }
        ResultadoCalificacionCodigos r = CalificadorPorCodigos.calificar(encontrados, catalogo, true, "", umbrales.get(criticidad), true);
        String marca = "▲ " + r.puntajeTexto() + " puntos";
        campos.put("srv_puntaje", r.puntajeTexto());
        campos.put("srv_formula", r.formula());
        campos.put("sem_bajo", r.nivel() == NivelRiesgo.BAJO ? marca : "");
        campos.put("sem_medio", r.nivel() == NivelRiesgo.MEDIO ? marca : "");
        campos.put("sem_alto", r.nivel() == NivelRiesgo.ALTO ? marca : "");
        List<Map<String, String>> hallazgos = new ArrayList<>();
        r.hallazgos().forEach(h -> hallazgos.add(new LinkedHashMap<>(Map.of("h_ref", h.ref(), "h_descripcion", h.descripcion(),
                "h_fuente", h.fuente(), "h_detalle", h.detalle(), "h_puntos", h.puntos()))));
        tablas.put("hallazgos", hallazgos);
        return new CalculoFormulario(campos, tablas, r, null, criticidad, avisos);
    }
}
