package com.polygraph.erp.modules.trabajos.config;

import com.polygraph.erp.shared.enums.TipoTrabajo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/** app.trabajos.* en application.yml. Todo se puede cambiar por variables de entorno sin tocar
 *  código — los tamaños de los pools dependen de la RAM/CPU del servidor de producción. */
@Data
@ConfigurationProperties(prefix = "app.trabajos")
public class PropiedadesTrabajos {

    /** false apaga el despachador y la limpieza (ej. en pruebas con H2, que no tiene SKIP LOCKED). */
    private boolean habilitado = true;

    /** Cada cuánto el despachador revisa la cola. Además se despierta al encolar algo. */
    private long intervaloSondeoMs = 2000;

    /** Entradas subidas, resultados y caché de informes. Fuera de git, como backend/uploads. */
    private String directorio = "./uploads/trabajos";

    /** Completados: en horas — ya cumplieron (el archivo se descargó); no hay por qué guardarlos. */
    private int retencionHorasCompletados = 24;
    private int retencionDiasFallidos = 30;
    /** PDFs de informes publicados ya generados: si nadie los pide en este tiempo, se borran. */
    private int retencionDiasCache = 30;

    /** Límite de filas del Excel de carga masiva — protege RAM y el tiempo de un solo trabajo. */
    private int maxFilasCargaMasiva = 1000;

    private Map<TipoTrabajo, Tipo> tipos = new EnumMap<>(TipoTrabajo.class);

    @Data
    public static class Tipo {
        /** Hilos dedicados = cuántos de este tipo corren a la vez. */
        private int hilos = 1;
        /** 1 = sin reintento automático (para trabajos que no son idempotentes). */
        private int maxIntentos = 3;
        /** Si lleva más que esto en PROCESANDO, se asume que el backend murió en medio. */
        private int timeoutMinutos = 10;
        /** Espera antes del reintento n: base * 4^(n-1) → 15s, 60s, 240s... */
        private int esperaBaseSegundos = 15;
    }

    public Tipo tipo(TipoTrabajo tipo) {
        return tipos.computeIfAbsent(tipo, t -> new Tipo());
    }
}
