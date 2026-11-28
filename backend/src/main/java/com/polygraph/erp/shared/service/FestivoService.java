package com.polygraph.erp.shared.service;

import com.polygraph.erp.shared.entity.Festivo;
import com.polygraph.erp.shared.repository.FestivoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Los festivos que cuentan para el calendario hábil, por año (con caché). Los de la ley se generan solos y se guardan (origen AUTO)
 *  al arrancar para 2025–2030 y cada 1 de diciembre para el año siguiente; el administrador desactiva uno o agrega un día propio
 *  (origen MANUAL). Un año que aún no se guardó se calcula al vuelo, para que nunca falte un festivo. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FestivoService {

    static final int PRIMER_ANIO = 2025;
    static final int ULTIMO_ANIO_INICIAL = 2030;

    private final FestivoRepository repository;
    private final PlatformTransactionManager transacciones;
    private final Map<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();

    /** Festivos ACTIVOS del año: los guardados, o los de la ley calculados si el año todavía no se guardó (más los propios activos). */
    @Transactional(readOnly = true)
    public Set<LocalDate> activosDelAnio(int anio) {
        Set<LocalDate> conocido = cache.get(anio);
        if (conocido != null) return conocido;
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);
        Set<LocalDate> activos = new HashSet<>();
        for (Festivo f : repository.findByFechaBetweenOrderByFecha(desde, hasta)) {
            if (Boolean.TRUE.equals(f.getActivo())) activos.add(f.getFecha());
        }
        if (!repository.existsByOrigenAndFechaBetween(Festivo.AUTO, desde, hasta)) {
            activos.addAll(FestivosColombiaGenerator.generar(anio).keySet());
        }
        cache.put(anio, activos);
        return activos;
    }

    /** Guarda los festivos de la ley del año si todavía no están. Devuelve cuántos creó. */
    @Transactional
    public int asegurarAnio(int anio) {
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);
        if (repository.existsByOrigenAndFechaBetween(Festivo.AUTO, desde, hasta)) return 0;
        int creados = 0;
        for (Map.Entry<LocalDate, String> e : FestivosColombiaGenerator.generar(anio).entrySet()) {
            // Si el administrador ya había agregado ese día a mano, se respeta lo suyo.
            if (repository.findByFecha(e.getKey()).isPresent()) continue;
            repository.save(Festivo.builder().fecha(e.getKey()).nombre(e.getValue()).origen(Festivo.AUTO).activo(true)
                    .creadoPor("sistema").fechaCreacion(LocalDateTime.now()).build());
            creados++;
        }
        cache.remove(anio);
        log.info("Festivos de Colombia {} guardados: {}", anio, creados);
        return creados;
    }

    /** Hay que llamarlo cuando se cambia un festivo. */
    public void recargar() {
        cache.clear();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cargaInicial() {
        try {
            // Cada año en su propia transacción (asegurarAnio es de la misma clase: su @Transactional no se aplicaría): un año a medias
            // quedaría con festivos faltantes para siempre, porque "ya tiene festivos de la ley" lo daría por completo.
            TransactionTemplate tx = new TransactionTemplate(transacciones);
            for (int anio = PRIMER_ANIO; anio <= ULTIMO_ANIO_INICIAL; anio++) {
                int a = anio;
                tx.executeWithoutResult(estado -> asegurarAnio(a));
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron guardar los festivos iniciales (se calculan al vuelo): {}", e.getMessage());
        }
    }

    /** Cada 1 de diciembre deja listos los festivos del año siguiente. */
    @Scheduled(cron = "0 0 6 1 12 *", zone = "America/Bogota")
    public void generarAnioSiguiente() {
        asegurarAnio(LocalDate.now().getYear() + 1);
    }
}
