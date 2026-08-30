package net.phoenixvine.domains.data;

public enum ClaimFlag {

    MOB_GRIEFING(false, "Mob Griefing"),

    EXPLOSIONS(false, "Explosions"),

    FIRE_SPREAD(false, "Fire Spread"),

    FLUID_FLOW(true, "Fluid Flow"),

    HOSTILE_SPAWNING(true, "Hostile Spawning"),

    PASSIVE_SPAWNING(true, "Passive Spawning"),

    PVP(false, "PvP"),

    ALLY_BUILD(false, "Ally Build"),

    ALLY_INTERACT(true, "Ally Interact"),

    ALLY_CONTAINERS(false, "Ally Containers");

    private final boolean defaultValue;
    private final String label;

    ClaimFlag(boolean defaultValue, String label) {
        this.defaultValue = defaultValue;
        this.label = label;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public String label() {
        return label;
    }

    public static ClaimFlag byName(String name) {
        for (ClaimFlag flag : values()) {
            if (flag.name().equalsIgnoreCase(name)) return flag;
        }
        return null;
    }
}
