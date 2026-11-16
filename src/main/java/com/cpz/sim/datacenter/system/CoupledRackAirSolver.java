package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.temperature.RoomAirCoolingPath;

import java.util.List;

/**
 * Backward-Euler air solve after server temperatures have been eliminated.
 * Each rack equation is piecewise linear and can be eliminated analytically.
 * The remaining scalar room residual is strictly increasing: rack feedback
 * cannot exceed its passive rack-to-room conductance. No sequential time update
 * or extra capacity is introduced by this algebraic elimination. All temperatures
 * in the equations are offsets from a common reference, avoiding cancellation
 * between large absolute stored energies.
 */
final class CoupledRackAirSolver {
    record RackEquation(double coefficient, double rhs, double roomConductance,
                        RoomAirCoolingPath cooling, double electricalPower, boolean rejectsToRoom) {
        /** RHS and room are offsets from a common reference to avoid subtracting large stored energies. */
        double temperatureOffsetAt(double room, double reference) {
            double uncooled = (rhs + roomConductance * room) / coefficient;
            finite(uncooled);
            double supplyOffset = cooling.supplyTemperatureCelsius() - reference;
            if (uncooled <= supplyOffset || cooling.heatTransferWattsPerCelsius() == 0.0)
                return uncooled;
            double denominator = coefficient + cooling.heatTransferWattsPerCelsius();
            finite(denominator);
            double drop = Math.min(cooling.capacityWatts() / coefficient,
                    cooling.heatTransferWattsPerCelsius() / denominator
                            * (uncooled - supplyOffset));
            double result = uncooled - drop;
            finite(result);
            return result;
        }
    }

    /** Solves the room increment relative to reference, rather than its absolute temperature. */
    static double solveOffset(double coefficient, double uncooledRoom, double reference,
                              List<RoomAirCoolingPath> roomPaths, List<RackEquation> racks) {
        finite(reference);
        finite(coefficient);
        finite(uncooledRoom);
        if (coefficient <= 0.0) throw new IllegalArgumentException("air coefficient must be > 0");
        double lower = uncooledRoom;
        double upper = uncooledRoom;
        double atCenter = residual(coefficient, uncooledRoom, uncooledRoom, reference, roomPaths, racks);
        if (atCenter == 0.0) return uncooledRoom;
        double width = 1.0;
        boolean bracketed = false;
        for (int i = 0; i < 1024; i++) {
            if (atCenter > 0.0) {
                lower = uncooledRoom - width;
                finite(lower);
                if (residual(coefficient, uncooledRoom, lower, reference, roomPaths, racks) <= 0.0) {
                    bracketed = true;
                    break;
                }
            } else {
                upper = uncooledRoom + width;
                finite(upper);
                if (residual(coefficient, uncooledRoom, upper, reference, roomPaths, racks) >= 0.0) {
                    bracketed = true;
                    break;
                }
            }
            width *= 2.0;
            finite(width);
        }
        if (!bracketed) throw new IllegalArgumentException("could not bracket coupled air temperatures");
        for (int i = 0; i < 100; i++) {
            double middle = lower * 0.5 + upper * 0.5;
            if (middle == lower || middle == upper) break;
            if (residual(coefficient, uncooledRoom, middle, reference, roomPaths, racks) > 0.0) upper = middle;
            else lower = middle;
        }
        return lower * 0.5 + upper * 0.5;
    }

    private static double residual(double coefficient, double uncooledRoom, double room, double reference,
                                   List<RoomAirCoolingPath> roomPaths, List<RackEquation> racks) {
        double value = coefficient * (room - uncooledRoom);
        for (var path : roomPaths) value += path.removedHeatWatts(reference + room);
        for (var rack : racks) {
            double local = rack.temperatureOffsetAt(room, reference);
            value += rack.roomConductance() * (room - local);
            if (rack.rejectsToRoom()) value -= rack.cooling().removedHeatWatts(reference + local) + rack.electricalPower();
        }
        finite(value);
        return value;
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("coupled air calculation must remain finite");
    }
}
