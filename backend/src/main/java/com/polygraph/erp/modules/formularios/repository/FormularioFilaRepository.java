package com.polygraph.erp.modules.formularios.repository;

import com.polygraph.erp.modules.formularios.entity.FormularioFila;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormularioFilaRepository extends JpaRepository<FormularioFila, Integer> {
    List<FormularioFila> findByFormulario_IdFormularioOrderByOrdenAsc(Integer idFormulario);
    void deleteByFormulario_IdFormulario(Integer idFormulario);
    boolean existsBySeccion_IdSeccion(Integer idSeccion);
    boolean existsByFormulario_IdFormulario(Integer idFormulario);
}
