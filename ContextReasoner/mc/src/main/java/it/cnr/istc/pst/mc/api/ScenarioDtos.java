package it.cnr.istc.pst.mc.api;

import java.util.List;

/** JSON contracts for discoverable, file-based ABox scenarios. */
public final class ScenarioDtos {

    private ScenarioDtos() { }

    public record ScenarioInfo(String id, String label, String source, boolean active) { }

    public record ScenarioCatalog(List<ScenarioInfo> scenarios, String activeScenario) { }
}
