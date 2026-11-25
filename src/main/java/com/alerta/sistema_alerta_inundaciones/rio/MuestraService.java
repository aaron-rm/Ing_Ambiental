package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class MuestraService {

    private final MuestraRepository muestraRepository;
    private final RioRepository rioRepository;

    public MuestraService(MuestraRepository muestraRepository, RioRepository rioRepository) {
        this.muestraRepository = muestraRepository;
        this.rioRepository = rioRepository;
    }

    @Transactional(readOnly = true)
    public List<MuestraDTO> listarPorRio(Long rioId) {
        if (!rioRepository.existsById(rioId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No existe un río con ID " + rioId + ".");
        }
        return muestraRepository.findTop500ByRioIdOrderByFechaDescIdDesc(rioId).stream()
                .map(MuestraDTO::desde)
                .toList();
    }

    @Transactional
    public MuestraDTO crearMuestra(Long rioId, CrearMuestraRequest solicitud, Usuario usuario) {
        Rio rio = rioRepository.findById(rioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un río con ID " + rioId + "."));
        if (solicitud == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Los datos de la muestra son obligatorios.");
        }
        if (solicitud.valor() == null || !Double.isFinite(solicitud.valor())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El valor numérico es obligatorio y debe ser finito.");
        }
        try {
            Instant fecha = MuestraCsvParser.parsearFecha(solicitud.fecha());
            String parametro = MuestraCsvParser.validarParametro(solicitud.parametro());
            String unidad = MuestraCsvParser.validarUnidad(solicitud.unidad());
            String nota = MuestraCsvParser.validarNota(solicitud.nota());

            Muestra muestra = new Muestra(
                    rio,
                    fecha,
                    parametro,
                    solicitud.valor(),
                    unidad,
                    nota,
                    usuario.getIdentificador()
            );
            return MuestraDTO.desde(muestraRepository.save(muestra));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Transactional
    public int cargarCsv(Long rioId, String contenido, Usuario usuario) {
        Rio rio = rioRepository.findById(rioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un río con ID " + rioId + "."));
        List<MuestraCsvParser.FilaMuestra> filas;
        try {
            filas = MuestraCsvParser.parsear(contenido);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }

        String responsable = usuario.getIdentificador();
        List<Muestra> entidades = filas.stream()
                .map(f -> new Muestra(
                        rio, f.fecha(), f.parametro(), f.valor(), f.unidad(), f.nota(), responsable))
                .toList();
        if (!entidades.isEmpty()) {
            muestraRepository.saveAll(entidades);
        }
        return entidades.size();
    }
}
