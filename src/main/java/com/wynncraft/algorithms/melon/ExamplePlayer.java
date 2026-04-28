package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.IPlayer;
import com.wynncraft.core.interfaces.IPlayerBuilder;
import com.wynncraft.core.interfaces.Information;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Example player template.
 *
 * <p>This class is intentionally not registered in {@code AlgorithmRegistry}.
 */
@Information(name = "Example Player", version = 1, authors = "Melon")
public final class ExamplePlayer implements IPlayer {

    private static final SkillPoint[] SKILL_POINTS = SkillPoint.values();

    private final List<IEquipment> equipment;
    private final int[] allocated;
    private final int[] bonus = new int[SKILL_POINTS.length];
    private int weight;

    private ExamplePlayer(List<IEquipment> equipment, int[] allocated) {
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
        return allocated[skill.ordinal()] + bonus[skill.ordinal()];
    }

    @Override
    public int allocated(SkillPoint skill) {
        return allocated[skill.ordinal()];
    }

    @Override
    public void modify(int[] skillPoints, boolean sum) {
        int sign = sum ? 1 : -1;
        for (int i = 0; i < skillPoints.length; i++) {
            int value = skillPoints[i] * sign;
            bonus[i] += value;
            weight += value;
        }
    }

    @Override
    public void reset() {
        Arrays.fill(bonus, 0);
        weight = 0;
    }

    public static final class Builder implements IPlayerBuilder<ExamplePlayer> {

        private final List<IEquipment> equipment = new ArrayList<>();
        private final int[] allocated = new int[SKILL_POINTS.length];

        @Override
        public IPlayerBuilder<ExamplePlayer> equipment(IEquipment... equipment) {
            this.equipment.addAll(Arrays.asList(equipment));
            return this;
        }

        @Override
        public IPlayerBuilder<ExamplePlayer> allocate(SkillPoint point, int amount) {
            allocated[point.ordinal()] = amount;
            return this;
        }

        @Override
        public ExamplePlayer build() {
            return new ExamplePlayer(new ArrayList<>(equipment), allocated.clone());
        }
    }
}
