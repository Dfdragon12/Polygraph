package com.polygraph.erp.modules.servicios.repository;

import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ServicioSubprocesoRepository
        extends JpaRepository<ServicioSubproceso, Long>, JpaSpecificationExecutor<ServicioSubproceso> {

    List<ServicioSubproceso> findByServicio_IdServicioOrderByTipoProgreso_OrdenAsc(Integer idServicio);

    List<ServicioSubproceso> findByServicio_IdServicioInOrderByServicio_IdServicioAscTipoProgreso_OrdenAsc(
            List<Integer> idsServicio);

    List<ServicioSubproceso> findByFechaProgramadaBetween(LocalDateTime desde, LocalDateTime hasta);

    boolean existsByServicio_IdServicioAndTipoProgreso_IdTipoProgreso(Integer idServicio, Integer idTipoProgreso);

    /** Carga actual de un empleado — usada para repartir la autoasignación al que tenga menos pendientes. */
    long countByUsuarioAsignado_IdUsuarioAndEstado(Long idUsuario, EstadoAsignacion estado);

    /** Agenda vigente de un empleado — usada para detectar choques de horario al programar. */
    List<ServicioSubproceso> findByUsuarioAsignado_IdUsuarioAndEstado(Long idUsuario, EstadoAsignacion estado);

    /** ¿Este empleado tiene algún subproceso (de cualquier tipo, en cualquier estado) asignado
     *  para algún servicio de este candidato? Usado para que ANALISTA_INTERNO/POLIGRAFISTA/
     *  VISITADOR solo puedan ver/editar la hoja de vida de candidatos con los que tienen relación
     *  real — ver CandidatoHojaVidaService. */
    boolean existsByServicio_Candidato_IdCandidatoAndUsuarioAsignado_IdUsuario(Long idCandidato, Long idUsuario);

    /** Para el buscador global de ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR/PROGRAMADOR — solo SUS
     *  propios subprocesos (asignado a ejecutarlo, o a cargo de coordinarlo si es programador),
     *  nunca los de otro empleado. Busca por nombre del proceso o del candidato; navega directo
     *  al formulario de ese subproceso puntual (a diferencia de GESTOR, que ve el Servicio
     *  completo — estos roles trabajan uno a uno por subproceso). `patron` ya viene armado como
     *  "%texto%" en minúsculas — ver la nota en ServicioRepository.buscar. */
    @Query("""
            select sp from ServicioSubproceso sp
            join sp.servicio s
            join s.proceso p
            left join s.candidato c
            where (sp.usuarioAsignado.idUsuario = :idUsuario or sp.programadorResponsable.idUsuario = :idUsuario)
              and (
                lower(p.nombreProceso) like :patron
                or lower(coalesce(c.nombres, '')) like :patron
                or lower(coalesce(c.apellidos, '')) like :patron
              )
            order by sp.fechaProgramada desc
            """)
    List<ServicioSubproceso> buscarAsignadoA(@Param("patron") String patron, @Param("idUsuario") Long idUsuario, Pageable pageable);
}
