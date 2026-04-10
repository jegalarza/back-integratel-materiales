package com.automatizacion.service;

import com.automatizacion.dto.request.RangoRequest;
import com.automatizacion.dto.response.GuiaResponse;
import com.automatizacion.dto.response.RangoResponse;

public interface RangoService {

    GuiaResponse obtenerListaRangosDesdeExcel() throws Exception;

    RangoResponse procesarRangos(RangoRequest request) throws Exception;

    String ejecutarShellController(String ambiente);

}
