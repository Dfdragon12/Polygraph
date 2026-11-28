package com.polygraph.erp.shared.estados;

/**
 * Los tres anillos de riesgo (Bajo · Medio · Alto) del inicio del cliente y del gestor (PB-05). Los porcentajes se reparten por el método del mayor
 * residuo: siempre suman exactamente 100 (con la regla de «redondear y ajustar el último» podía salir un 101 o un −1). Sin estudios, todo en 0.
 * Pura.
 */
public final class AnillosRiesgo {

    private AnillosRiesgo() {}

    public record Anillos(int total, int bajo, int medio, int alto, int pctBajo, int pctMedio, int pctAlto) {}

    public static Anillos de(long bajo, long medio, long alto) {
        long total = bajo + medio + alto;
        if (total <= 0) return new Anillos(0, 0, 0, 0, 0, 0, 0);
        long[] n = {bajo, medio, alto};
        int[] pct = new int[3];
        long[] resto = new long[3];
        int suma = 0;
        for (int i = 0; i < 3; i++) {
            pct[i] = (int) (100 * n[i] / total);
            resto[i] = 100 * n[i] % total;
            suma += pct[i];
        }
        for (int falta = 100 - suma; falta > 0; falta--) {
            int mejor = -1;
            for (int i = 0; i < 3; i++) {
                if (n[i] > 0 && (mejor < 0 || resto[i] > resto[mejor])) mejor = i;
            }
            pct[mejor]++;
            resto[mejor] = -1;   // ya recibió su punto
        }
        return new Anillos((int) total, (int) bajo, (int) medio, (int) alto, pct[0], pct[1], pct[2]);
    }
}
