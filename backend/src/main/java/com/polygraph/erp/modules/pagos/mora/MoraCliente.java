package com.polygraph.erp.modules.pagos.mora;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * La mora, calculada (NS-05). Un cliente está en mora si tiene al menos una factura sin pagar cuya fecha de vencimiento ya pasó (una que vence hoy todavía
 * no está vencida). No depende de una marca guardada: sale de las facturas, así que pagar una de dos facturas vencidas NO lo saca de la mora.
 * Es la ÚNICA fuente del mensaje: lo ve el cliente en pantalla y es el mismo texto con el que el servidor rechaza el envío.
 */
public final class MoraCliente {

    private MoraCliente() {}

    /** Una factura del cliente. {@code pagada}: ya no se debe. */
    public record Factura(String referencia, LocalDate vencimiento, BigDecimal monto, boolean pagada) {}

    public record Resultado(boolean enMora, int facturasVencidas, long diasMora, String referenciaMasAntigua, BigDecimal montoVencido, String mensaje) {
        public static final Resultado SIN_MORA = new Resultado(false, 0, 0, null, BigDecimal.ZERO, null);
    }

    public static Resultado calcular(List<Factura> facturas, LocalDate hoy) {
        List<Factura> vencidas = facturas.stream()
                .filter(f -> !f.pagada() && f.vencimiento() != null && f.vencimiento().isBefore(hoy))
                .sorted((a, b) -> a.vencimiento().compareTo(b.vencimiento()))
                .toList();
        if (vencidas.isEmpty()) return Resultado.SIN_MORA;
        Factura masAntigua = vencidas.get(0);
        long dias = ChronoUnit.DAYS.between(masAntigua.vencimiento(), hoy);
        BigDecimal monto = vencidas.stream().map(f -> f.monto() != null ? f.monto() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resultado(true, vencidas.size(), dias, masAntigua.referencia(), monto, mensaje(vencidas.size(), monto, masAntigua.referencia(), dias));
    }

    static String mensaje(int cantidad, BigDecimal monto, String referencia, long dias) {
        String facturas = cantidad == 1 ? "1 factura vencida" : cantidad + " facturas vencidas";
        String hace = dias == 1 ? "hace 1 día" : "hace " + dias + " días";
        String masAntigua = cantidad == 1 ? "La factura " + referencia + " venció " + hace + "." : "La más antigua, " + referencia + ", venció " + hace + ".";
        return "Tu cuenta está en mora: tienes " + facturas + " por " + pesos(monto) + ". " + masAntigua
                + " Págalas para volver a solicitar servicios.";
    }

    /** $1.200.000 — pesos sin decimales, con punto de miles. */
    static String pesos(BigDecimal v) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.forLanguageTag("es-CO"));
        s.setGroupingSeparator('.');
        return "$" + new DecimalFormat("#,##0", s).format(v);
    }
}
