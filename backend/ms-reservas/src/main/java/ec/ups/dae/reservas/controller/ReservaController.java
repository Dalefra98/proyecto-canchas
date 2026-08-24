package ec.ups.dae.reservas.controller;

import ec.ups.dae.reservas.dto.DisponibilidadResponse;
import ec.ups.dae.reservas.service.DisponibilidadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las rutas congeladas de /api/reservas. Los codigos de error de cada endpoint son los de la
 * tabla "Formato de error" del contrato.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final DisponibilidadService disponibilidadService;

    public ReservaController(DisponibilidadService disponibilidadService) {
        this.disponibilidadService = disponibilidadService;
    }

    /**
     * HU-01: bloques de una hora de una cancha en una fecha, con su disponibilidad.
     *
     * canchaId y fecha son obligatorios: si falta alguno, el manejador responde
     * 400 DATOS_INVALIDOS. fecha se recibe como String para que el parseo estricto del mapper
     * sea quien decida, y no el convertidor de Spring (design D-11).
     */
    @GetMapping("/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> disponibilidad(@RequestParam Long canchaId,
                                                                 @RequestParam String fecha) {
        return ResponseEntity.ok(disponibilidadService.consultar(canchaId, fecha));
    }
}
