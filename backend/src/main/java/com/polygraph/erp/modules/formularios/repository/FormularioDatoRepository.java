package com.polygraph.erp.modules.formularios.repository;

import com.polygraph.erp.modules.formularios.entity.FormularioDato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormularioDatoRepository extends JpaRepository<FormularioDato, Integer> {
    List<FormularioDato> findByFormulario_IdFormulario(Integer idFormulario);
    void deleteByFormulario_IdFormulario(Integer idFormulario);
    boolean existsByColumna_IdColumna(Integer idColumna);
    boolean existsByFormulario_IdFormulario(Integer idFormulario);
}
