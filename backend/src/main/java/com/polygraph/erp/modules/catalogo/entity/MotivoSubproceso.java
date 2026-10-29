package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.shared.enums.TipoMotivo;
import jakarta.persistence.*;
import lombok.*;

/** Un motivo de las listas que usa quien reprograma, cancela o devuelve un subproceso. Lo usado no se borra: se desactiva. */
@Entity
@Table(name = "motivos_subproceso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotivoSubproceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMotivo tipo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** El motivo "Otro": quien actúa debe escribir cuál. */
    @Builder.Default
    @Column(name = "requiere_texto", nullable = false)
    private Boolean requiereTexto = false;

    /** Identifica los motivos con reglas propias (NO_SE_PRESENTO, AVISO_NO_ASISTIRA) sin depender de su texto; null en el resto. */
    @Column(name = "codigo", length = 30)
    private String codigo;

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
