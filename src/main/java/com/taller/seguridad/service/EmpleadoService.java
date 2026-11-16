package com.taller.seguridad.service;

import com.taller.seguridad.model.Empleado;
import com.taller.seguridad.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;

    public EmpleadoService(EmpleadoRepository empleadoRepository) {
        this.empleadoRepository = empleadoRepository;
    }

    @Transactional(readOnly = true)
    public List<Empleado> listarTodos() {
        return empleadoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Empleado> buscarPorId(Long id) {
        return empleadoRepository.findById(id);
    }

    @Transactional
    public Empleado guardar(Empleado empleado) {
        if (empleado.getId() == null && empleadoRepository.existsByEmail(empleado.getEmail())) {
            throw new IllegalArgumentException("Ya existe un empleado con el email: " + empleado.getEmail());
        }
        return empleadoRepository.save(empleado);
    }

    @Transactional
    public Empleado actualizar(Long id, Empleado datosActualizados) {
        Empleado existente = empleadoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado con ID: " + id));

        // Validar si el nuevo email ya pertenece a otro empleado
        if (!existente.getEmail().equalsIgnoreCase(datosActualizados.getEmail())
                && empleadoRepository.existsByEmail(datosActualizados.getEmail())) {
            throw new IllegalArgumentException("El email " + datosActualizados.getEmail() + " ya está registrado por otro empleado.");
        }

        existente.setNombre(datosActualizados.getNombre());
        existente.setApellido(datosActualizados.getApellido());
        existente.setEmail(datosActualizados.getEmail());
        existente.setCargo(datosActualizados.getCargo());
        existente.setSalario(datosActualizados.getSalario());

        return empleadoRepository.save(existente);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!empleadoRepository.existsById(id)) {
            throw new RuntimeException("Empleado no encontrado con ID: " + id);
        }
        empleadoRepository.deleteById(id);
    }
}

