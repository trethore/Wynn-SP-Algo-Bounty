package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.IPlayer;
import com.wynncraft.core.interfaces.IPlayerBuilder;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public final class StarvingPlayer implements IPlayer {

    private static final SkillPoint[] SKILL_POINTS = SkillPoint.values();

    /*
     * We kept this fast and dumb ->
     * StarvingGoblin reads/writes these arrays directly in the hot path.
     * That lets it copy the best bonus result into the player instead of
     * rebuilding it from equipment or going through getters for each skill wich is slow af.
     */
    final List<IEquipment> equipment;
    final int[] allocated;
    final int[] bonus = new int[SKILL_POINTS.length];
    int weight;

    private StarvingPlayer(List<IEquipment> equipment, int[] allocated) {
        this.equipment = equipment;
        this.allocated = allocated;
    }

    @Override
    public List<IEquipment> equipment() {
        return equipment;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public int total(SkillPoint skill) {
        int index = skill.ordinal();
        return allocated[index] + bonus[index];
    }

    @Override
    public int allocated(SkillPoint skill) {
        return allocated[skill.ordinal()];
    }

    @Override
    public void modify(int[] skillPoints, boolean sum) {
        // Keep the five skills unrolled, bc loop is a bottleneck here.
        int sign = sum ? 1 : -1;
        int delta0 = skillPoints[0] * sign;
        int delta1 = skillPoints[1] * sign;
        int delta2 = skillPoints[2] * sign;
        int delta3 = skillPoints[3] * sign;
        int delta4 = skillPoints[4] * sign;
        bonus[0] += delta0;
        bonus[1] += delta1;
        bonus[2] += delta2;
        bonus[3] += delta3;
        bonus[4] += delta4;
        weight += delta0 + delta1 + delta2 + delta3 + delta4;
    }

    @Override
    public void reset() {
        bonus[0] = 0;
        bonus[1] = 0;
        bonus[2] = 0;
        bonus[3] = 0;
        bonus[4] = 0;
        weight = 0;
    }

    public static final class Builder implements IPlayerBuilder<StarvingPlayer> {

        private final List<IEquipment> equipment = new ArrayList<>(16);
        private final int[] allocated = new int[SKILL_POINTS.length];

        /*
         * Keep the same player on purpose.
         * The benchmark adds one item, calls build(), then does it again.
         * Same equipment list means cache/prefix work can be reused.
         */
        private final StarvingPlayer player = new StarvingPlayer(equipment, allocated);

        @Override
        public IPlayerBuilder<StarvingPlayer> equipment(IEquipment... items) {
            Collections.addAll(equipment, items);
            return this;
        }

        @Override
        public IPlayerBuilder<StarvingPlayer> allocate(SkillPoint point, int amount) {
            allocated[point.ordinal()] = amount;
            return this;
        }

        @Override
        public StarvingPlayer build() {
            return player;
        }
    }
}
