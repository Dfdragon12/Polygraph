package com.polygraph.erp.modules.pagos.dto;

import java.util.List;

public record WompiEventSignatureDTO(
        List<String> properties,
        String checksum
) {
}
