package com.polygraph.erp.modules.riesgo.hallazgos;

import com.polygraph.erp.modules.riesgo.codigos.CalificadorPorCodigos;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.AvisoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoCatalogo;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoEncontrado;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.ResultadoCalificacionCodigos;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;
import com.polygraph.erp.shared.enums.CriticidadCargo;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calificación por hallazgos de UN subproceso, igual para todos los servicios: la plantilla dice qué
 * campo aplica hallazgos (y cuáles) y el catálogo de hallazgos dice cuánto vale cada uno. Para
 * Antecedentes legales da lo mismo que docs/antecedentes/motor_ant.py. Cálculo puro (sin Spring/JPA).
 *
 *  1. NUMERO_FILA: numera 1, 2, 3… esa columna en cada tabla (antes de todo, así el detalle la usa).
 *  2. Criticidad: el campo CRITICIDAD del formulario; si la plantilla no tiene, la del estudio.
 *  3. Condición: el campo CONDICION; "Sí" habilita los hallazgos "solo con condición". Sin campo: sí.
 *  4. Hallazgos, en el orden de la plantilla (secciones, filas, columnas): un campo HALLAZGO aporta
 *     el código elegido; una lista normal, el código que el admin asoció a la opción elegida.
 *  5. CalificadorPorCodigos: acumula por fila o cuenta una vez, crítico → 100/ALTO, tope 100, nivel.
 *  6. Salidas: columnas calculadas (puntaje, fórmula, nivel, semáforo, notas) y la tabla de hallazgos.
 */
public final class MotorHallazgos {

    public static final String FALTA_CRITICIDAD = "Falta la criticidad del cargo.";
    private static final String AVISO_CONDICION = "{codigo} ignorado: el cargo no implica conducción";
    private static final Pattern MARCADOR = Pattern.compile("\\{([^}]+)}");

    /** La plantilla, solo con lo que importa para calificar (secciones y columnas activas, en orden). */
    public record Plantilla(List<Seccion> secciones) {}

    public record Seccion(String clave, String nombre, boolean tabla, boolean calculada, List<Columna> columnas) {}

    /** uso: null | HALLAZGO | CRITICIDAD | CONDICION. calculo: solo en columnas calculadas.
     *  hallazgoPorOpcion: en listas normales, opción → código. */
    public record Columna(String clave, String etiqueta, boolean calculada, String uso, String calculo,
                          String referencia, String textoNota, String fuente, String detalle,
                          Map<String, String> hallazgoPorOpcion) {}

    private MotorHallazgos() {}

    /** Una columna FORMULA con referencia CON_CODIGO pide que, con un solo hallazgo, la fórmula diga "LAB-07: 50 de 100 posibles". */
    static boolean formulaConCodigo(Plantilla p) {
        return p.secciones().stream().flatMap(s -> s.columnas().stream())
                .anyMatch(c -> "FORMULA".equals(c.calculo()) && "CON_CODIGO".equals(c.referencia()));
    }

    /** Cálculos que se resuelven fila por fila en una tabla que llena una persona. */
    static final Set<String> CALCULOS_POR_FILA = Set.of("NUMERO_FILA", "PROMEDIO_FILA", "CODIGOS_FILA", "TEXTO_SI");

    /** "PER-03<3": el código que se genera y el valor por debajo del cual se genera (textoNota de PROMEDIO_FILA / MINIMO_FILAS). */
    private static final Pattern REGLA_MENOR = Pattern.compile("^\\s*([A-Za-z0-9-]+)\\s*<\\s*(\\d+(?:[.,]\\d+)?)\\s*$");

    /** Código y límite de una regla "CODIGO<N"; null si el texto no la tiene. */
    private record ReglaMenor(String codigo, double limite) {}

    private static ReglaMenor reglaMenor(Columna c) {
        if (c.textoNota() == null) return null;
        Matcher m = REGLA_MENOR.matcher(c.textoNota());
        return m.matches() ? new ReglaMenor(m.group(1), Double.parseDouble(m.group(2).replace(',', '.'))) : null;
    }

    /** PROMEDIO_FILA: promedio de los campos de la fila listados en referencia ("r_a1,r_a2,…"); solo cuentan los números. null si no hay ninguno. */
    private static Double promedioDeFila(Columna c, Map<String, String> fila) {
        if (c.referencia() == null) return null;
        double suma = 0;
        int n = 0;
        for (String k : c.referencia().split(",")) {
            String v = fila.get(k.trim());
            if (v != null && v.trim().matches("\\d+")) {
                suma += Integer.parseInt(v.trim());
                n++;
            }
        }
        return n == 0 ? null : suma / n;
    }

    /** Un decimal con coma ("2,6"), como el resto de números del informe. */
    private static String decimal(double x) {
        return java.math.BigDecimal.valueOf(x).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** El hallazgo que genera por sí sola una columna PROMEDIO_FILA cuando el promedio queda por debajo de su límite; null si no. */
    private static String codigoAutomatico(Columna c, Map<String, String> fila) {
        ReglaMenor regla = reglaMenor(c);
        Double promedio = "PROMEDIO_FILA".equals(c.calculo()) && regla != null ? promedioDeFila(c, fila) : null;
        return promedio != null && promedio < regla.limite() ? regla.codigo() : null;
    }

    /** Los códigos de hallazgo marcados en la fila (sin "—" ni repetidos), separados por coma; "—" si no hay. */
    private static String codigosDeLaFila(Seccion s, Map<String, String> fila) {
        Set<String> codigos = new LinkedHashSet<>();
        for (Columna c : s.columnas()) {
            if (!"HALLAZGO".equals(c.uso())) continue;
            String v = fila.get(c.clave());
            if (v != null && !sinValor(v)) codigos.add(v.trim());
        }
        for (Columna c : s.columnas()) {
            String automatico = codigoAutomatico(c, fila);
            if (automatico != null) codigos.add(automatico);
        }
        return codigos.isEmpty() ? "—" : String.join(", ", codigos);
    }

    /** TEXTO_SI: referencia = "clave=Valor"; si ese campo de la fila vale Valor, sale el texto; si no, vacío. */
    private static String textoSi(Columna c, Map<String, String> fila) {
        if (c.referencia() == null || c.textoNota() == null) return "";
        int i = c.referencia().indexOf('=');
        if (i < 0) return "";
        String valor = fila.get(c.referencia().substring(0, i).trim());
        return valor != null && valor.trim().equals(c.referencia().substring(i + 1).trim()) ? c.textoNota() : "";
    }

    /** ¿Un único campo califica (un campo HALLAZGO, ninguna opción con hallazgo ni campos HALLAZGO en tablas)? */
    static boolean calificacionUnica(Plantilla p) {
        long campos = p.secciones().stream().filter(s -> !s.tabla()).flatMap(s -> s.columnas().stream())
                .filter(c -> "HALLAZGO".equals(c.uso())).count();
        boolean otros = p.secciones().stream().flatMap(s -> s.columnas().stream().filter(c -> s.tabla() && "HALLAZGO".equals(c.uso())
                || !c.hallazgoPorOpcion().isEmpty())).findAny().isPresent();
        return campos == 1 && !otros;
    }

    /** ¿La plantilla califica por hallazgos? (algún campo HALLAZGO o alguna opción con hallazgo). */
    public static boolean califica(Plantilla p) {
        return p.secciones().stream().flatMap(s -> s.columnas().stream())
                .anyMatch(c -> "HALLAZGO".equals(c.uso()) || !c.hallazgoPorOpcion().isEmpty());
    }

    /**
     * campos/tablas: lo diligenciado, por clave (ValoresPorClave). catalogo: hallazgos activos que
     * suman (sin alertas). criticidadEstudio: la del estudio, solo si la plantilla no tiene campo
     * CRITICIDAD. Nunca lanza por un dato faltante: lo deja en CalculoFormulario.aviso.
     */
    public static CalculoFormulario calcular(Plantilla p, Map<String, CodigoCatalogo> catalogo, Map<CriticidadCargo, Umbrales> umbrales,
                                             CriticidadCargo criticidadEstudio, Map<String, String> campos,
                                             Map<String, List<Map<String, String>>> tablasEntrada) {
        // 1. Numeración (sobre una copia de cada fila).
        Map<String, List<Map<String, String>>> tablas = new LinkedHashMap<>();
        tablasEntrada.forEach((k, filas) -> tablas.put(k, filas.stream().<Map<String, String>>map(LinkedHashMap::new).toList()));
        Map<String, List<Map<String, String>>> salidaTablas = new LinkedHashMap<>();
        for (Seccion s : p.secciones()) {
            if (!s.tabla() || s.calculada()) continue;
            List<Columna> numeradas = s.columnas().stream().filter(c -> c.calculo() != null && CALCULOS_POR_FILA.contains(c.calculo())).toList();
            if (numeradas.isEmpty()) continue;
            List<Map<String, String>> filas = tablas.getOrDefault(s.clave(), List.of());
            List<Map<String, String>> salida = new ArrayList<>();
            for (int i = 0; i < filas.size(); i++) {
                Map<String, String> fila = new LinkedHashMap<>();
                // Primero el número (otras columnas de la fila pueden usarlo); luego lo que depende de lo diligenciado.
                for (Columna c : numeradas) {
                    if (!"NUMERO_FILA".equals(c.calculo())) continue;
                    filas.get(i).put(c.clave(), String.valueOf(i + 1));
                    fila.put(c.clave(), String.valueOf(i + 1));
                }
                for (Columna c : numeradas) {
                    if (!"PROMEDIO_FILA".equals(c.calculo())) continue;
                    Double promedio = promedioDeFila(c, filas.get(i));
                    String valor = promedio == null ? "—" : decimal(promedio);
                    filas.get(i).put(c.clave(), valor);
                    fila.put(c.clave(), valor);
                }
                for (Columna c : numeradas) {
                    if ("NUMERO_FILA".equals(c.calculo()) || "PROMEDIO_FILA".equals(c.calculo())) continue;
                    String valor = "CODIGOS_FILA".equals(c.calculo()) ? codigosDeLaFila(s, filas.get(i)) : textoSi(c, filas.get(i));
                    filas.get(i).put(c.clave(), valor);
                    fila.put(c.clave(), valor);
                }
                salida.add(fila);
            }
            salidaTablas.put(s.clave(), salida);
        }

        // Notas (no dependen de la criticidad).
        Map<String, String> salidaCampos = new LinkedHashMap<>();
        for (Columna c : columnasDeCampos(p)) {
            if ("NOTA".equals(c.calculo())) {
                boolean sinValor = tablas.containsKey(c.referencia())
                        ? tablas.get(c.referencia()).isEmpty() : sinValor(campos.get(c.referencia()));
                salidaCampos.put(c.clave(), sinValor && c.textoNota() != null ? c.textoNota() : "");
            }
        }

        // 2–3. Criticidad y condición.
        Columna colCriticidad = columnasDeCampos(p).stream().filter(c -> "CRITICIDAD".equals(c.uso())).findFirst().orElse(null);
        CriticidadCargo criticidadFormulario = colCriticidad == null ? null : criticidad(campos.get(colCriticidad.clave()));
        CriticidadCargo criticidad = colCriticidad != null ? criticidadFormulario : criticidadEstudio;
        Columna colCondicion = columnasDeCampos(p).stream().filter(c -> "CONDICION".equals(c.uso())).findFirst().orElse(null);
        boolean cumpleCondicion = colCondicion == null || esSi(campos.get(colCondicion.clave()));

        // 4. Hallazgos en el orden de la plantilla.
        List<CodigoEncontrado> encontrados = new ArrayList<>();
        List<AvisoFormulario> avisos = new ArrayList<>();
        for (Seccion s : p.secciones()) {
            if (s.calculada()) continue;
            if (!s.tabla()) {
                for (Columna c : s.columnas()) {
                    agregar(encontrados, avisos, catalogo, cumpleCondicion, s, c, campos.get(c.clave()), Map.of(), campos, s.nombre(), null, List.of());
                }
            } else {
                List<Map<String, String>> filas = tablas.getOrDefault(s.clave(), List.of());
                for (int i = 0; i < filas.size(); i++) {
                    Set<String> vistosEnLaFila = new HashSet<>();
                    for (Columna c : s.columnas()) {
                        agregar(encontrados, avisos, catalogo, cumpleCondicion, s, c, filas.get(i).get(c.clave()), filas.get(i), campos,
                                s.nombre() + ", fila " + (i + 1), vistosEnLaFila, filas);
                        String automatico = codigoAutomatico(c, filas.get(i));
                        if (automatico != null && vistosEnLaFila.add(automatico)) {
                            encontrados.add(new CodigoEncontrado(automatico, textoDe(c.fuente(), s.nombre(), filas.get(i), campos),
                                    textoDe(c.detalle(), "", filas.get(i), campos)));
                        }
                    }
                }
                // Reglas de conteo sobre esta tabla (MINIMO_FILAS): se evalúan al terminar de leerla, así su hallazgo
                // queda justo después de los de sus filas.
                for (Columna c : columnasDeCampos(p)) {
                    ReglaMenor regla = "MINIMO_FILAS".equals(c.calculo()) && s.clave() != null && s.clave().equals(c.referencia()) ? reglaMenor(c) : null;
                    if (regla != null && filas.size() < regla.limite()) {
                        Map<String, String> conteo = Map.of("#", String.valueOf(filas.size()));
                        encontrados.add(new CodigoEncontrado(regla.codigo(), textoDe(c.fuente(), c.etiqueta(), conteo, campos),
                                textoDe(c.detalle(), "", conteo, campos)));
                    }
                }
            }
        }

        if (criticidad == null || !umbrales.containsKey(criticidad)) {
            columnasDeCampos(p).stream().filter(c -> c.calculo() != null && !"NOTA".equals(c.calculo()))
                    .forEach(c -> salidaCampos.put(c.clave(), ""));
            p.secciones().stream().filter(Seccion::calculada).forEach(s -> salidaTablas.put(s.clave(), List.of()));
            return new CalculoFormulario(salidaCampos, salidaTablas, null, FALTA_CRITICIDAD, criticidadFormulario, avisos);
        }

        // 5. Calificación.
        ResultadoCalificacionCodigos r = CalificadorPorCodigos.calificar(encontrados, catalogo, cumpleCondicion, AVISO_CONDICION,
                umbrales.get(criticidad), calificacionUnica(p) || formulaConCodigo(p));

        // 6. Salidas.
        String marca = r.critico() ? "▲ Hallazgo crítico" : "▲ " + r.puntajeTexto() + " puntos";
        for (Columna c : columnasDeCampos(p)) {
            if (c.calculo() == null || "NOTA".equals(c.calculo())) continue;
            salidaCampos.put(c.clave(), switch (c.calculo()) {
                case "PUNTAJE" -> r.puntajeTexto();
                case "FORMULA" -> r.formula();
                case "NIVEL" -> r.nivel().name();
                case "SEMAFORO_BAJO" -> r.nivel() == NivelRiesgo.BAJO ? marca : "";
                case "SEMAFORO_MEDIO" -> r.nivel() == NivelRiesgo.MEDIO ? marca : "";
                case "SEMAFORO_ALTO" -> r.nivel() == NivelRiesgo.ALTO ? marca : "";
                case "MINIMO_FILAS" -> String.valueOf(tablas.getOrDefault(c.referencia(), List.of()).size());
                case "PROMEDIO_TABLA" -> promedioDeTabla(p, c.referencia(), tablas.getOrDefault(c.referencia(), List.of()));
                default -> "";
            });
        }
        for (Seccion s : p.secciones()) {
            if (!s.calculada()) continue;
            salidaTablas.put(s.clave(), r.hallazgos().stream().map(h -> {
                Map<String, String> fila = new LinkedHashMap<>();
                for (Columna c : s.columnas()) {
                    fila.put(c.clave(), c.calculo() == null ? "" : switch (c.calculo()) {
                        case "REF" -> h.ref();
                        case "DESCRIPCION" -> h.descripcion();
                        case "FUENTE" -> h.fuente();
                        case "DETALLE" -> h.detalle();
                        case "PUNTOS" -> h.puntos();
                        default -> "";
                    });
                }
                return fila;
            }).toList());
        }
        return new CalculoFormulario(salidaCampos, salidaTablas, r, null, criticidadFormulario, avisos);
    }

    private static void agregar(List<CodigoEncontrado> encontrados, List<AvisoFormulario> avisos, Map<String, CodigoCatalogo> catalogo,
                                boolean cumpleCondicion, Seccion s, Columna c, String valor, Map<String, String> fila,
                                Map<String, String> campos, String lugar, Set<String> vistosEnLaFila,
                                List<Map<String, String>> filasDeLaTabla) {
        if (c.calculada() || valor == null || valor.isBlank()) return;
        String v = valor.trim();
        String codigo = "HALLAZGO".equals(c.uso()) ? v : c.hallazgoPorOpcion().get(v);
        if (codigo == null || sinValor(codigo)) return;
        if (vistosEnLaFila != null && !vistosEnLaFila.add(codigo)) return;
        // {#} en la fuente o el detalle = cuántas filas de la tabla tienen esta misma opción elegida.
        String cuantas = String.valueOf(filasDeLaTabla.stream().filter(f -> v.equals(f.get(c.clave()) == null ? null : f.get(c.clave()).trim())).count());
        Map<String, String> contexto = new HashMap<>(fila);
        contexto.put("#", cuantas);
        String fuente = c.fuente() != null && !c.fuente().isBlank() ? plantilla(c.fuente(), contexto, campos) : s.nombre();
        String detalle = c.detalle() != null && !c.detalle().isBlank() ? plantilla(c.detalle(), contexto, campos)
                : s.tabla() ? detalleFila(fila, c.clave()) : c.etiqueta() + ": " + v;
        encontrados.add(new CodigoEncontrado(codigo, fuente, detalle));
        CodigoCatalogo cc = catalogo.get(codigo);
        if (cc != null && cc.soloConduccion() && !cumpleCondicion) {
            avisos.add(new AvisoFormulario(codigo + " no suma porque el cargo no implica conducción.", lugar));
        }
    }

    /** La fuente o el detalle configurado ({clave} = valor de la fila o del campo); por defecto, alternativo. */
    private static String textoDe(String configurado, String alternativo, Map<String, String> fila, Map<String, String> campos) {
        return configurado != null && !configurado.isBlank() ? plantilla(configurado, fila, campos) : alternativo;
    }

    /** PROMEDIO_TABLA: promedio de lo que da la columna PROMEDIO_FILA de esa tabla en cada fila; "—" si ninguna tiene. */
    private static String promedioDeTabla(Plantilla p, String claveTabla, List<Map<String, String>> filas) {
        Columna promedio = p.secciones().stream().filter(s -> s.tabla() && s.clave() != null && s.clave().equals(claveTabla))
                .flatMap(s -> s.columnas().stream()).filter(c -> "PROMEDIO_FILA".equals(c.calculo())).findFirst().orElse(null);
        if (promedio == null) return "—";
        List<Double> valores = filas.stream().map(f -> promedioDeFila(promedio, f)).filter(Objects::nonNull).toList();
        return valores.isEmpty() ? "—" : decimal(valores.stream().mapToDouble(Double::doubleValue).sum() / valores.size());
    }

    /** Lo diligenciado en la fila (sin la columna del hallazgo ni lo vacío), separado por " · ". */
    private static String detalleFila(Map<String, String> fila, String sin) {
        StringJoiner j = new StringJoiner(" · ");
        fila.forEach((k, v) -> { if (!k.equals(sin) && !sinValor(v)) j.add(v.trim()); });
        return j.toString();
    }

    /** Clave del campo de criticidad de la plantilla (uso CRITICIDAD); null si no tiene. */
    public static String claveCriticidad(Plantilla p) {
        return columnasDeCampos(p).stream().filter(c -> "CRITICIDAD".equals(c.uso())).map(Columna::clave).findFirst().orElse(null);
    }

    private static List<Columna> columnasDeCampos(Plantilla p) {
        return p.secciones().stream().filter(s -> !s.tabla()).flatMap(s -> s.columnas().stream()).toList();
    }

    /** {clave} → valor de la fila o, si no está, del campo. */
    static String plantilla(String texto, Map<String, String> fila, Map<String, String> campos) {
        Matcher m = MARCADOR.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String k = m.group(1).trim();
            String v = fila.containsKey(k) ? fila.get(k) : campos.get(k);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Vacío, "—", "No" o "false": sin valor. */
    static boolean sinValor(String v) {
        if (v == null) return true;
        String t = v.trim();
        return t.isEmpty() || "—".equals(t) || "-".equals(t) || "no".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t);
    }

    private static boolean esSi(String v) {
        if (v == null) return false;
        String t = Normalizer.normalize(v.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return "si".equalsIgnoreCase(t) || "true".equalsIgnoreCase(t);
    }

    /** "Estándar" | "Sensible" | "Crítico" → CriticidadCargo; null si vacío o no válida. */
    public static CriticidadCargo criticidad(String valor) {
        if (valor == null || valor.isBlank() || "—".equals(valor.trim())) return null;
        String clave = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
        try {
            return CriticidadCargo.valueOf(clave);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
