package net.phoenixvine.domains.network;

/**
 * Shared string-length caps for this package's packets — same purpose as Phoenix-Guilds'
 * {@code GuildNetworkLimits}: previously bare numeric literals duplicated between each packet's
 * encode and decode method, currently consistent but with nothing enforcing that. Naming them
 * once and referencing the constant on both sides closes the "future edit to one side only
 * silently desyncs client/server" risk.
 */
public final class DomainNetworkLimits {

    public static final int DIMENSION_MAX = 256;
    public static final int OWNER_NAME_MAX = 64;
    public static final int ARG_MAX = 64;

    private DomainNetworkLimits() {}
}
