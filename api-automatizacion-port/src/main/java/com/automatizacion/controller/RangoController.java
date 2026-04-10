package com.automatizacion.controller;

import com.automatizacion.dto.request.RangoRequest;
import com.automatizacion.dto.response.ErrorResponse;
import com.automatizacion.dto.response.GuiaResponse;
import com.automatizacion.dto.response.RangoResponse;
import com.automatizacion.service.impl.RangoServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/number/port")
@CrossOrigin(origins = "*")
public class RangoController {

    private final RangoServiceImpl excelService;

    public RangoController(RangoServiceImpl excelService) {
        this.excelService = excelService;
    }

    @PostMapping
    public ResponseEntity<?> asignar(@RequestBody RangoRequest request) {
        try {
            RangoResponse response = excelService.procesarRangos(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error interno del servidor"));
        }
    }

    @GetMapping("/{ambiente}")
    public ResponseEntity<String> ejecutarDemonios(
            @PathVariable String ambiente){
        String response = null;
        try {
            response = excelService.ejecutarShellController(ambiente);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno del servidor");
        }
    }

    @GetMapping("/help")
    public ResponseEntity<GuiaResponse> mostrarNumberPort(){
        GuiaResponse response = new GuiaResponse();
        try {
            response = excelService.obtenerListaRangosDesdeExcel();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }
}