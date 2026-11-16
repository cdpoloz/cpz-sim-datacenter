package com.cpz.sim.datacenter.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerThermalProtectionTest {
    private static Server server(ThermalRestartPolicy policy) {
        return new Server(new ServerLocation("C01", "R01", "S01"),
                new ServerConfig("M", "CPZ", "Test", 100, 300, null,
                        new ServerThermalProtection(true, 90, 85, .4, 105, 70, 120, policy, 5)),
                HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
    }

    @Test void limitsDeliveredLoadAndPowerWithoutCompoundingOrLosingDemand() {
        var s = server(ThermalRestartPolicy.MANUAL);
        s.setUtilization(1); s.updatePowerConsumption();
        s.evaluateThermalProtection(90, 0);
        assertEquals(ThermalProtectionState.THROTTLED, s.getThermalProtectionState());
        assertEquals(.4, s.getUtilization());
        assertEquals(180, s.getCurrentPowerWatts());
        assertEquals(1, s.getRequestedUtilization());
        assertEquals(300, s.getRequestedPowerWatts());
        for (int i = 0; i < 10; i++) { s.setUtilization(1); s.updatePowerConsumption(); }
        assertEquals(180, s.getCurrentPowerWatts());
        s.evaluateThermalProtection(87, 10);
        assertEquals(ThermalProtectionState.THROTTLED, s.getThermalProtectionState());
        s.evaluateThermalProtection(85, 20);
        assertEquals(ThermalProtectionState.NORMAL, s.getThermalProtectionState());
        assertEquals(300, s.getCurrentPowerWatts());
        assertEquals(1, s.getUtilization());
        s.evaluateThermalProtection(90, 30);
        s.setUtilization(.1); s.updatePowerConsumption();
        assertEquals(120, s.getCurrentPowerWatts(), "A cap must not increase low demand");
        s.setCurrentPowerWatts(250); s.estimateUtilizationFromCurrentPower();
        assertEquals(180, s.getCurrentPowerWatts());
        assertEquals(.4, s.getUtilization());
        assertEquals(250, s.getRequestedPowerWatts());
    }

    @Test void manualRestartRequiresRequestCoolingAndMinimumOffTime() {
        var s = server(ThermalRestartPolicy.MANUAL);
        s.setUtilization(1); s.updatePowerConsumption();
        s.evaluateThermalProtection(105, 100);
        assertEquals(HardwareStatus.OFFLINE, s.getStatus());
        assertEquals(ThermalProtectionState.SHUTDOWN, s.getThermalProtectionState());
        assertEquals(0, s.getUtilization());
        assertEquals(5, s.getCurrentPowerWatts());
        assertThrows(IllegalStateException.class, () -> s.setStatus(HardwareStatus.OK));
        s.evaluateThermalProtection(70, 300);
        assertEquals(ThermalProtectionState.SHUTDOWN, s.getThermalProtectionState());
        s.requestThermalRestart();
        s.evaluateThermalProtection(71, 300);
        assertEquals(ThermalProtectionState.SHUTDOWN, s.getThermalProtectionState());
        s.evaluateThermalProtection(70, 300);
        assertEquals(HardwareStatus.OK, s.getStatus());
        assertEquals(ThermalProtectionState.NORMAL, s.getThermalProtectionState());
        s.evaluateThermalProtection(110, 400);
        s.requestThermalRestart();
        s.evaluateThermalProtection(70, 519);
        assertEquals(ThermalProtectionState.SHUTDOWN, s.getThermalProtectionState());
        s.evaluateThermalProtection(70, 520);
        assertEquals(ThermalProtectionState.NORMAL, s.getThermalProtectionState());
    }

    @Test void automaticRestartNeverTakesOwnershipOfAnOperatorOfflineServer() {
        var s = server(ThermalRestartPolicy.AUTOMATIC);
        s.evaluateThermalProtection(106, 0);
        s.setCurrentPowerWatts(0); s.updatePowerConsumption();
        assertEquals(5, s.getCurrentPowerWatts());
        s.evaluateThermalProtection(70, 119);
        assertEquals(HardwareStatus.OFFLINE, s.getStatus());
        s.evaluateThermalProtection(70, 120);
        assertEquals(HardwareStatus.OK, s.getStatus());
        s.evaluateThermalProtection(106, 130);
        s.setStatus(HardwareStatus.OFFLINE);
        s.evaluateThermalProtection(30, 1000);
        assertEquals(HardwareStatus.OFFLINE, s.getStatus());
        assertEquals(ThermalProtectionState.NORMAL, s.getThermalProtectionState());
        assertEquals(0, s.getCurrentPowerWatts());
        s.resetThermalProtection();
        assertEquals(HardwareStatus.OFFLINE, s.getStatus());
    }

    @Test void resetRestoresOnlyProtectionOwnedShutdown() {
        var s = server(ThermalRestartPolicy.MANUAL);
        s.evaluateThermalProtection(110, 0);
        s.resetThermalProtection();
        assertEquals(HardwareStatus.OK, s.getStatus());
        assertEquals(ThermalProtectionState.NORMAL, s.getThermalProtectionState());
        assertThrows(IllegalStateException.class, s::requestThermalRestart);
    }

    @Test void absentOrDisabledProfilesPreserveUnprotectedExperiment() {
        var disabled = new ServerThermalProtection(false, 90, 85, .4, 105, 70, 120, ThermalRestartPolicy.MANUAL, 5);
        for (ServerThermalProtection p : new ServerThermalProtection[] {null, disabled}) {
            var s = new Server(new ServerLocation("C01", "R01", "S01"),
                    new ServerConfig("M", "CPZ", "Test", 100, 300, null, p), HardwareStatus.OK, ServerRole.GENERAL_PURPOSE);
            s.setUtilization(1); s.updatePowerConsumption(); s.evaluateThermalProtection(160, 10);
            assertEquals(HardwareStatus.OK, s.getStatus());
            assertEquals(300, s.getCurrentPowerWatts());
            assertEquals(ThermalProtectionState.NORMAL, s.getThermalProtectionState());
        }
    }
}
