package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.temperature.RoomAirCoolingPath;

import java.util.List;

/**
 * Backward-Euler air solve after server temperatures have been eliminated.
 * Each rack equation is piecewise linear and can be eliminated analytically.
 * The remaining scalar room residual is strictly increasing: rack feedback
 * cannot exceed its passive rack-to-room conductance. No sequential time update
 * or extra capacity is introduced by this algebraic elimination.
 */
final class CoupledRackAirSolver {
    record RackEquation(double coefficient, double rhs, double roomConductance,
                        RoomAirCoolingPath cooling, double electricalPower, boolean rejectsToRoom) {
        double temperatureAt(double room) {
            double uncooled = (rhs + roomConductance * room) / coefficient;
            finite(uncooled);
            if (uncooled <= cooling.supplyTemperatureCelsius() || cooling.heatTransferWattsPerCelsius() == 0.0)
                return uncooled;
            double denominator = coefficient + cooling.heatTransferWattsPerCelsius();
            finite(denominator);
            double drop = Math.min(cooling.capacityWatts() / coefficient,
                    cooling.heatTransferWattsPerCelsius() / denominator
                            * (uncooled - cooling.supplyTemperatureCelsius()));
            double result = uncooled - drop;
            finite(result);
            return result;
        }
    }

    static double solve(double coefficient, double uncooledRoom, List<RoomAirCoolingPath> roomPaths,
                        List<RackEquation> racks) {
        finite(coefficient);
        finite(uncooledRoom);
        if (coefficient <= 0.0) throw new IllegalArgumentException("air coefficient must be > 0");
        double lower = uncooledRoom;
        double upper = uncooledRoom;
        double atCenter = residual(coefficient, uncooledRoom, uncooledRoom, roomPaths, racks);
        if (atCenter == 0.0) return uncooledRoom;
        double width = 1.0;
        boolean bracketed = false;
        for (int i = 0; i < 1024; i++) {
            if (atCenter > 0.0) {
                lower = uncooledRoom - width;
                finite(lower);
                if (residual(coefficient, uncooledRoom, lower, roomPaths, racks) <= 0.0) {
                    bracketed = true;
                    break;
                }
            } else {
                upper = uncooledRoom + width;
                finite(upper);
                if (residual(coefficient, uncooledRoom, upper, roomPaths, racks) >= 0.0) {
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
            if (residual(coefficient, uncooledRoom, middle, roomPaths, racks) > 0.0) upper = middle;
            else lower = middle;
        }
        return lower * 0.5 + upper * 0.5;
    }

    private static double residual(double coefficient, double uncooledRoom, double room,
                                   List<RoomAirCoolingPath> roomPaths, List<RackEquation> racks) {
        double value = coefficient * (room - uncooledRoom);
        for (var path : roomPaths) value += path.removedHeatWatts(room);
        for (var rack : racks) {
            double local = rack.temperatureAt(room);
            value += rack.roomConductance() * (room - local);
            if (rack.rejectsToRoom()) value -= rack.cooling().removedHeatWatts(local) + rack.electricalPower();
        }
        finite(value);
        return value;
    }

    private static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("coupled air calculation must remain finite");
    }
}
