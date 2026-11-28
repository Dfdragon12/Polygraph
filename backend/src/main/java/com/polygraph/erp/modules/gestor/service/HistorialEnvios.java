package com.polygraph.erp.modules.gestor.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PC-03 · El historial de envíos del enlace: UNO por envío o reenvío, con su fecha, los canales, quién lo hizo y hasta cuándo vale. Sale de
 * {@code envios_enlace}, que solo se inserta: un reenvío nunca pisa el primero. Las filas de un mismo envío comparten {@code id_envio}; las anteriores a
 * PC-03 no lo tienen y cada una cuenta como un envío. Pura, sin Spring.
 */
public final class HistorialEnvios {

    private HistorialEnvios() {}

    /** Las filas de envios_enlace que cuentan como envío (no el cambio de contacto, la ampliación ni el recordatorio). */
    public static final Set<String> CANALES_DE_ENVIO = Set.of("CORREO", "WHATSAPP", "COPIA", "ANTERIOR");

    public record Fila(Long id, String idEnvio, String canal, LocalDateTime fecha, LocalDate vigenciaHasta, String usuario) {}

    /** canales: CORREO, WHATSAPP, COPIA o ANTERIOR (envío del sistema anterior, sin canal conocido). */
    public record Envio(LocalDateTime fecha, List<String> canales, String usuario, LocalDate vigenciaHasta, boolean sistemaAnterior) {}

    /** Del más reciente al más antiguo. */
    public static List<Envio> agrupar(List<Fila> filas) {
        Map<String, List<Fila>> grupos = new LinkedHashMap<>();
        for (Fila f : filas) {
            if (!CANALES_DE_ENVIO.contains(f.canal())) continue;
            String clave = f.idEnvio() != null && !f.idEnvio().isBlank() ? f.idEnvio() : "fila-" + f.id();
            grupos.computeIfAbsent(clave, k -> new ArrayList<>()).add(f);
        }
        List<Envio> envios = new ArrayList<>();
        for (List<Fila> grupo : grupos.values()) {
            LocalDateTime fecha = grupo.stream().map(Fila::fecha).min(Comparator.naturalOrder()).orElse(null);
            List<String> canales = grupo.stream().map(Fila::canal).distinct().toList();
            LocalDate vigencia = grupo.stream().map(Fila::vigenciaHasta).filter(v -> v != null).findFirst().orElse(null);
            String usuario = grupo.stream().map(Fila::usuario).filter(u -> u != null && !u.isBlank()).findFirst().orElse(null);
            envios.add(new Envio(fecha, canales, usuario, vigencia, canales.size() == 1 && "ANTERIOR".equals(canales.get(0))));
        }
        envios.sort(Comparator.comparing(Envio::fecha, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return envios;
    }
}
