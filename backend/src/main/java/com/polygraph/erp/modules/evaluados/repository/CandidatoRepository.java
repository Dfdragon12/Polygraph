package com.polygraph.erp.modules.evaluados.repository;

import com.polygraph.erp.modules.evaluados.entity.Candidato;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

    Optional<Candidato> findByCedula(String cedula);

    /** Para el buscador global (ADMIN_POLYGRAPH y GESTOR, que ya administran candidatos sin
     *  restricción) — por nombres, apellidos o cédula. `patron` ya viene armado como "%texto%" en
     *  minúsculas — ver la nota en ServicioRepository.buscar. */
    @Query("""
            select c from Candidato c
            where lower(c.nombres) like :patron
               or lower(c.apellidos) like :patron
               or lower(c.cedula) like :patron
            """)
    List<Candidato> buscar(@Param("patron") String patron, Pageable pageable);

    /** Para el buscador global de ANALISTA_INTERNO/POLIGRAFISTA/VISITADOR/PROGRAMADOR — mismo
     *  alcance que ya usa CandidatoHojaVidaService para decidir si pueden ver la hoja de vida de
     *  un candidato: solo candidatos con al menos un servicio donde el usuario es el asignado
     *  (usuarioAsignado) o el programador responsable de coordinarlo. Sin este filtro, cualquiera
     *  de estos roles podría buscar y encontrar candidatos con los que no tiene ninguna relación
     *  — sería un candidato más para ver por el buscador, aunque después no pudiera abrir su
     *  formulario (esa restricción sigue existiendo aparte), así que igual conviene no mostrarlo. */
    @Query("""
            select distinct c from Candidato c
            join Servicio s on s.candidato = c
            join ServicioSubproceso sp on sp.servicio = s
            where (lower(c.nombres) like :patron or lower(c.apellidos) like :patron or lower(c.cedula) like :patron)
              and (sp.usuarioAsignado.idUsuario = :idUsuario or sp.programadorResponsable.idUsuario = :idUsuario)
            """)
    List<Candidato> buscarAsignadoA(@Param("patron") String patron, @Param("idUsuario") Long idUsuario, Pageable pageable);
}
