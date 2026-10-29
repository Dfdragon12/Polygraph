package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.shared.entity.Festivo;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.FestivoRepository;
import com.polygraph.erp.shared.service.FestivoService;
import com.polygraph.erp.shared.service.FestivosColombiaGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Calendario de festivos de Colombia para el cálculo de días hábiles: los de la ley se generan solos (origen AUTO) y el administrador
 *  puede desactivar uno o agregar un día no laborable propio (origen MANUAL, por ejemplo un cierre de la empresa). */
@Service
@RequiredArgsConstructor
@Slf4j
public class FestivoAdminService {

    private final FestivoRepository repository;
    private final FestivoService festivoService;

    /** origen: AUTO (generado con la ley) o MANUAL (lo agregó el administrador). activo: cuenta como día no hábil. */
    public record FestivoDto(LocalDate fecha, String nombre, String origen, boolean activo) {}

    @Transactional
    public List<FestivoDto> listar(int anio) {
        validarAnio(anio);
        festivoService.asegurarAnio(anio);
        return repository.findByFechaBetweenOrderByFecha(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31)).stream()
                .map(FestivoAdminService::dto).toList();
    }

    /** Deja la fecha activa o inactiva. Una fecha nueva que no es festivo de la ley se agrega como día propio (pide nombre). */
    @Transactional
    public FestivoDto cambiar(LocalDate fecha, boolean activo, String nombre, String usuario) {
        validarAnio(fecha.getYear());
        festivoService.asegurarAnio(fecha.getYear());
        Festivo f = repository.findByFecha(fecha).orElse(null);
        if (f == null) {
            if (!activo) throw new ApiException("Esa fecha no está en el calendario de festivos.", HttpStatus.NOT_FOUND);
            if (nombre == null || nombre.isBlank()) throw new ApiException("Escribe una descripción para el día no laborable.", HttpStatus.BAD_REQUEST);
            f = Festivo.builder().fecha(fecha).nombre(nombre.trim()).origen(Festivo.MANUAL).activo(true).creadoPor(usuario)
                    .fechaCreacion(LocalDateTime.now()).build();
        } else {
            f.setActivo(activo);
            if (Festivo.MANUAL.equals(f.getOrigen()) && nombre != null && !nombre.isBlank()) f.setNombre(nombre.trim());
            f.setModificadoPor(usuario);
            f.setFechaModificacion(LocalDateTime.now());
        }
        f = repository.saveAndFlush(f);
        festivoService.recargar();
        log.info("Festivo {} ({}) {} por {}", fecha, f.getNombre(), activo ? "activado" : "desactivado", usuario);
        return dto(f);
    }

    /** Cuántos festivos de la ley tiene un año (18). */
    public static int festivosDeLey(int anio) {
        return FestivosColombiaGenerator.generar(anio).size();
    }

    private static FestivoDto dto(Festivo f) {
        return new FestivoDto(f.getFecha(), f.getNombre(), f.getOrigen(), Boolean.TRUE.equals(f.getActivo()));
    }

    private static void validarAnio(int anio) {
        if (anio < 2000 || anio > 2100) throw new ApiException("El año debe estar entre 2000 y 2100.", HttpStatus.BAD_REQUEST);
    }
}
