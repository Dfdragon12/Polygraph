package com.polygraph.erp.modules.tienda.entity;

import com.polygraph.erp.shared.enums.NivelCiudad;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un recargo por ciudad que se retiró de un servicio (TP-10): se archiva, no se borra. Solo se inserta. */
@Entity
@Table(name = "precios_ciudad_proceso_archivo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrecioCiudadArchivado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo")
    private Long idArchivo;

    @Column(name = "id_proceso", nullable = false)
    private Integer idProceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_ciudad", nullable = false, length = 30)
    private NivelCiudad nivelCiudad;

    @Column(name = "valor", precision = 15, scale = 2, nullable = false)
    private BigDecimal valor;

    @Column(name = "archivado_en", nullable = false)
    private LocalDateTime archivadoEn;

    @Column(name = "archivado_por", nullable = false, length = 150)
    private String archivadoPor;

    @Column(name = "motivo", length = 300)
    private String motivo;
}
