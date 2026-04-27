package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.IPlayer;
import com.wynncraft.core.interfaces.IPlayerBuilder;
import com.wynncraft.core.interfaces.Information;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Voracious Goblin algorithm.
 *
 * @author Melon Team (riege and trethore)
 * @version 3
 */
@SuppressWarnings("DuplicatedCode")
@Information(name = "Voracious Player", version = 3, authors = "Melon")
public final class VoraciousPlayer implements IPlayer {

    private static final SkillPoint[] SKILL_POINTS = SkillPoint.values();

    private final List<IEquipment> equipment;
    private final int[] allocated;
    private final int[] bonus = new int[SKILL_POINTS.length];
    private int weight;

    private VoraciousPlayer(List<IEquipment> equipment, int[] allocated) {
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
        for (int i = 0; i < SKILL_POINTS.length; i++) {
            int delta = skillPoints[i] * sign;
            bonus[i] += delta;
            weight += delta;
        }
    }

    @Override
    public void reset() {
        for (int i = 0; i < SKILL_POINTS.length; i++) {
            bonus[i] = 0;
        }
        weight = 0;
    }

    public static final class Builder implements IPlayerBuilder<VoraciousPlayer> {

        private final List<IEquipment> equipment = new ArrayList<>();
        private final int[] allocated = new int[SKILL_POINTS.length];

        @Override
        public IPlayerBuilder<VoraciousPlayer> equipment(IEquipment... items) {
            Collections.addAll(equipment, items);
            return this;
        }

        @Override
        public IPlayerBuilder<VoraciousPlayer> allocate(SkillPoint point, int amount) {
            allocated[point.ordinal()] = amount;
            return this;
        }

        @Override
        public VoraciousPlayer build() {
            return new VoraciousPlayer(new ArrayList<>(equipment), allocated.clone());
        }
    }
}
