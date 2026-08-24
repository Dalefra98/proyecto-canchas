package ec.ups.dae.reservas.controller;

import ec.ups.dae.reservas.dto.DisponibilidadResponse;
import ec.ups.dae.reservas.dto.ReservaRequest;
import ec.ups.dae.reservas.dto.ReservaResponse;
import ec.ups.dae.reservas.service.DisponibilidadService;
import ec.ups.dae.reservas.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ReservaService reservaService;

    public ReservaController(DisponibilidadService disponibilidadService,
                             ReservaService reservaService) {
        this.disponibilidadService = disponibilidadService;
        this.reservaService = reservaService;
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

    /**
     * HU-02: crea una reserva sobre un bloque libre. El cuerpo es { canchaId, fecha,
     * horaInicio }; horaFin lo calcula el servicio (decision D-11 del requirements).
     *
     * Ambos roles pueden reservar: un ADMIN tambien es una persona que puede hacerlo
     * (decision D-08, consecuencia C-03). El usuarioId sale del token, nunca del cuerpo.
     */
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody ReservaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.crear(peticion));
    }
}
