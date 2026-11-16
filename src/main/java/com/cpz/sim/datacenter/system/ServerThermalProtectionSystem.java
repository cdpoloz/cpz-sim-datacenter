package com.cpz.sim.datacenter.system;

import com.cpz.sim.datacenter.model.Datacenter;
import com.cpz.sim.datacenter.model.Server;
import com.cpz.sim.foundation.engine.Simulatable;
import com.cpz.sim.foundation.time.SimulationTick;
import java.util.Objects;

/** Sample-and-hold control: evaluate last completed temperature before workload/power and thermal integration. */
public final class ServerThermalProtectionSystem implements Simulatable {
    private final Datacenter datacenter;
    private final TemperatureSystem temperatures;

    public ServerThermalProtectionSystem(Datacenter datacenter, TemperatureSystem temperatures) {
        this.datacenter = Objects.requireNonNull(datacenter);
        this.temperatures = Objects.requireNonNull(temperatures);
    }

    @Override public void update(SimulationTick tick) {
        Objects.requireNonNull(tick);
        // elapsedSeconds identifies the end of the new interval; control acts at its start.
        double startSeconds = Math.max(0, tick.elapsedSeconds() - tick.deltaSeconds());
        for (Server server : datacenter.getServers())
            server.evaluateThermalProtection(temperatures.getThermalState(server.getCode()).getTemperatureCelsius(), startSeconds);
    }

    @Override public void reset() {
        datacenter.getServers().forEach(Server::resetThermalProtection);
    }
}
