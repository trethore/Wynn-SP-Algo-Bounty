package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.IPlayer;
import com.wynncraft.core.interfaces.IPlayerBuilder;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public final class HungryPlayer implements IPlayer {

    private static final SkillPoint[] SKILL_POINTS = SkillPoint.values();

    final List<IEquipment> equipment;
    final int[] allocated;
    final int[] bonus = new int[SKILL_POINTS.length];
    int weight;

    private HungryPlayer(List<IEquipment> equipment, int[] allocated) {
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

    public static final class Builder implements IPlayerBuilder<HungryPlayer> {

        private final List<IEquipment> equipment = new ArrayList<>();
        private final int[] allocated = new int[SKILL_POINTS.length];
        private final HungryPlayer player = new HungryPlayer(equipment, allocated);

        @Override
        public IPlayerBuilder<HungryPlayer> equipment(IEquipment... items) {
            equipment.addAll(Arrays.asList(items));
            return this;
        }

        @Override
        public IPlayerBuilder<HungryPlayer> allocate(SkillPoint point, int amount) {
            allocated[point.ordinal()] = amount;
            return this;
        }

        @Override
        public HungryPlayer build() {
            return player;
        }
    }
}
