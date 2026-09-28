package com.example.lifecore.configuration.settings;

/**
 * Effective LifeCore rules for a single world.
 *
 * @param enabled               master switch
 * @param safe                  no heart changes at all
 * @param heartLoss             natural deaths remove hearts
 * @param playerKills           PvP kills transfer hearts
 * @param heartGain             killers gain hearts
 * @param mobLoss               mob deaths remove hearts
 * @param elimination           players can be eliminated
 * @param instantElimination    any death eliminates
 * @param heartDrops            heart items drop on kills
 * @param items                 LifeCore items usable
 * @param beacons               revive beacons can be placed
 * @param killerGain            override for kill.killer-gain (negative = none)
 * @param victimLoss            override for kill.victim-loss (negative = none)
 * @param naturalLossMultiplier multiplier for natural death losses
 */
public record WorldRules(boolean enabled, boolean safe, boolean heartLoss, boolean playerKills, boolean heartGain,
                         boolean mobLoss, boolean elimination, boolean instantElimination, boolean heartDrops,
                         boolean items, boolean beacons, double killerGain, double victimLoss,
                         double naturalLossMultiplier) {

    public static final WorldRules DEFAULT = new WorldRules(true, false, true, true, true, true, true, false, true,
            true, true, -1, -1, 1.0);

    /** @return true if hearts may change at all in this world. */
    public boolean lifestealActive() {
        return enabled && !safe;
    }
}
