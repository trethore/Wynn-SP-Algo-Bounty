package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * LeMelon algorithm.
 *
 * @author Melon Team (riege and trethore)
 * @version 1
 */
@SuppressWarnings("DuplicatedCode")
@Information(name = "LeMelon Algorithm", version = 1, authors = "Melon")
public final class LeMelonAlgorithm implements IAlgorithm<LeMelonPlayer> {

    private static final int STR = 0;
    private static final int DEX = 1;
    private static final int INT = 2;
    private static final int DEF = 3;
    private static final int AGI = 4;
    private static final int SKILLS = AGI + 1;
    private static final int EMPTY_CAPACITY = 0;
    private static final int VISITED_LIMIT = 22;
    private static final int VISITED_WORDS = (1 << VISITED_LIMIT) >>> 6;

    private final int[] state = new int[SKILLS];
    private final int[] bestBonuses = new int[SKILLS];
    private final long[] requiredBySkill = new long[SKILLS];
    private final long[] visited = new long[VISITED_WORDS];

    private IEquipment[] items = new IEquipment[EMPTY_CAPACITY];
    private int[] weights = new int[EMPTY_CAPACITY];
    private int[] req0 = new int[EMPTY_CAPACITY];
    private int[] req1 = new int[EMPTY_CAPACITY];
    private int[] req2 = new int[EMPTY_CAPACITY];
    private int[] req3 = new int[EMPTY_CAPACITY];
    private int[] req4 = new int[EMPTY_CAPACITY];
    private int[] bonus0 = new int[EMPTY_CAPACITY];
    private int[] bonus1 = new int[EMPTY_CAPACITY];
    private int[] bonus2 = new int[EMPTY_CAPACITY];
    private int[] bonus3 = new int[EMPTY_CAPACITY];
    private int[] bonus4 = new int[EMPTY_CAPACITY];
    private boolean[] hasRequirement = new boolean[EMPTY_CAPACITY];
    private boolean[] hasNegativeBonus = new boolean[EMPTY_CAPACITY];
    private long[] impactMasks = new long[EMPTY_CAPACITY];

    private int base0;
    private int base1;
    private int base2;
    private int base3;
    private int base4;
    private int itemCount;
    private boolean useVisited;
    private long negativeMask;
    private long bestMask;
    private int bestCount;
    private int bestWeight;
    private int activatedFreeWeight;

    private LeMelonPlayer lastPlayer;
    private LeMelonPlayer preparedPlayer;
    private LeMelonPlayer activePreparedPlayer;
    private IEquipment[] preparedItems = new IEquipment[EMPTY_CAPACITY];
    private int[] preparedWeights = new int[EMPTY_CAPACITY];
    private int[] preparedReq0 = new int[EMPTY_CAPACITY];
    private int[] preparedReq1 = new int[EMPTY_CAPACITY];
    private int[] preparedReq2 = new int[EMPTY_CAPACITY];
    private int[] preparedReq3 = new int[EMPTY_CAPACITY];
    private int[] preparedReq4 = new int[EMPTY_CAPACITY];
    private int[] preparedBonus0 = new int[EMPTY_CAPACITY];
    private int[] preparedBonus1 = new int[EMPTY_CAPACITY];
    private int[] preparedBonus2 = new int[EMPTY_CAPACITY];
    private int[] preparedBonus3 = new int[EMPTY_CAPACITY];
    private int[] preparedBonus4 = new int[EMPTY_CAPACITY];
    private boolean[] preparedHasRequirement = new boolean[EMPTY_CAPACITY];
    private boolean[] preparedHasNegativeBonus = new boolean[EMPTY_CAPACITY];
    private long preparedNegativeMask;
    private List<IEquipment> lastEquipment;
    private int lastItemCount;
    private boolean lastEquipmentReusable;

    @Override
    public Result run(LeMelonPlayer player) {
        List<IEquipment> equipment = player.equipment;
        prepareLight(player, equipment);

        long allMask = allItemsMask();
        if (negativeMask == 0L) {
            long activeMask = activateFreeItems();
            long addedMask = closePositiveItems(allMask & ~activeMask);
            bestMask = activeMask | addedMask;
            if (bestMask == allMask) {
                setPlayerToCurrentState(player);
                return new Result(equipment, Collections.emptyList());
            }
            bestCount = Long.bitCount(bestMask);
            bestWeight = activatedFreeWeight + maskWeight(addedMask);
            captureBestBonuses();
            return finish(player, equipment);
        }

        long fastMask = greedyAllMask();
        if (fastMask == allMask) {
            setPlayerToCurrentState(player);
            return new Result(equipment, Collections.emptyList());
        }
        if ((fastMask & negativeMask) == 0L && noInactiveItemCanEquip(fastMask)) {
            bestMask = fastMask;
            bestCount = Long.bitCount(fastMask);
            bestWeight = maskWeight(fastMask);
            captureBestBonuses();
            return finish(player, equipment);
        }

        prepareImpactMasks();
        resetStateToBase();
        long activeMask = activateFreeItems();
        long remainingMask = allMask & ~activeMask;
        bestMask = activeMask;
        bestCount = Long.bitCount(activeMask);
        bestWeight = activatedFreeWeight;
        captureBestBonuses();

        if (tryEquipEverything(remainingMask, allMask)) {
            return finish(player, equipment);
        }

        prepareVisited();
        search(activeMask, remainingMask, bestCount, bestWeight, maskWeight(remainingMask));
        return finish(player, equipment);
    }

    private void prepareLight(LeMelonPlayer player, List<IEquipment> equipment) {
        itemCount = equipment.size();
        if (player == preparedPlayer) {
            if (player != activePreparedPlayer) {
                items = preparedItems;
                weights = preparedWeights;
                req0 = preparedReq0;
                req1 = preparedReq1;
                req2 = preparedReq2;
                req3 = preparedReq3;
                req4 = preparedReq4;
                bonus0 = preparedBonus0;
                bonus1 = preparedBonus1;
                bonus2 = preparedBonus2;
                bonus3 = preparedBonus3;
                bonus4 = preparedBonus4;
                hasRequirement = preparedHasRequirement;
                hasNegativeBonus = preparedHasNegativeBonus;
                negativeMask = preparedNegativeMask;
                activePreparedPlayer = player;
            }
        } else {
            activePreparedPlayer = null;
            ensureCapacity(itemCount);
            boolean append = canReuseEquipmentPrefix(equipment);
            int start = append ? lastItemCount : 0;
            boolean reusable = append && lastEquipmentReusable;
            if (!append) {
                negativeMask = 0L;
                reusable = true;
            }
            for (int i = start; i < itemCount; i++) {
                IEquipment item = equipment.get(i);
                int[] req = item.requirements();
                int[] bonus = item.bonuses();
                items[i] = item;
                loadItemStatsLight(i, req, bonus, item.hasNegativeBonus());
                reusable &= item instanceof com.wynncraft.enums.Equipment;
            }
            lastEquipment = equipment;
            lastItemCount = itemCount;
            lastEquipmentReusable = reusable;
            if (player == lastPlayer && reusable) {
                capturePrepared(player);
            } else {
                lastPlayer = player;
            }
        }

        base0 = player.allocated[STR];
        base1 = player.allocated[DEX];
        base2 = player.allocated[INT];
        base3 = player.allocated[DEF];
        base4 = player.allocated[AGI];
        resetStateToBase();
    }

    private boolean canReuseEquipmentPrefix(List<IEquipment> equipment) {
        if (equipment != lastEquipment || itemCount < lastItemCount || !lastEquipmentReusable) {
            return false;
        }
        if (equipment instanceof java.util.RandomAccess) {
            return true;
        }
        for (int i = 0; i < lastItemCount; i++) {
            if (equipment.get(i) != items[i]) {
                return false;
            }
        }
        return true;
    }

    private void capturePrepared(LeMelonPlayer player) {
        preparedItems = Arrays.copyOf(items, itemCount);
        preparedWeights = Arrays.copyOf(weights, itemCount);
        preparedReq0 = Arrays.copyOf(req0, itemCount);
        preparedReq1 = Arrays.copyOf(req1, itemCount);
        preparedReq2 = Arrays.copyOf(req2, itemCount);
        preparedReq3 = Arrays.copyOf(req3, itemCount);
        preparedReq4 = Arrays.copyOf(req4, itemCount);
        preparedBonus0 = Arrays.copyOf(bonus0, itemCount);
        preparedBonus1 = Arrays.copyOf(bonus1, itemCount);
        preparedBonus2 = Arrays.copyOf(bonus2, itemCount);
        preparedBonus3 = Arrays.copyOf(bonus3, itemCount);
        preparedBonus4 = Arrays.copyOf(bonus4, itemCount);
        preparedHasRequirement = Arrays.copyOf(hasRequirement, itemCount);
        preparedHasNegativeBonus = Arrays.copyOf(hasNegativeBonus, itemCount);
        preparedNegativeMask = negativeMask;
        preparedPlayer = player;
    }

    private void loadItemStatsLight(int index, int[] req, int[] bonus, boolean negativeBonus) {
        long bit = 1L << index;
        int r0 = req[STR];
        int r1 = req[DEX];
        int r2 = req[INT];
        int r3 = req[DEF];
        int r4 = req[AGI];
        int b0 = bonus[STR];
        int b1 = bonus[DEX];
        int b2 = bonus[INT];
        int b3 = bonus[DEF];
        int b4 = bonus[AGI];
        req0[index] = r0;
        req1[index] = r1;
        req2[index] = r2;
        req3[index] = r3;
        req4[index] = r4;
        bonus0[index] = b0;
        bonus1[index] = b1;
        bonus2[index] = b2;
        bonus3[index] = b3;
        bonus4[index] = b4;
        weights[index] = b0 + b1 + b2 + b3 + b4;
        hasRequirement[index] = r0 > 0 || r1 > 0 || r2 > 0 || r3 > 0 || r4 > 0;
        hasNegativeBonus[index] = negativeBonus;
        negativeMask |= negativeBonus ? bit : 0L;
    }

    private void prepareImpactMasks() {
        Arrays.fill(requiredBySkill, 0L);
        for (int i = 0; i < itemCount; i++) {
            markRequiredSkills(i, 1L << i);
        }
        for (int i = 0; i < itemCount; i++) {
            impactMasks[i] = negativeRequirementImpactMask(i);
        }
    }

    private void markRequiredSkills(int item, long bit) {
        if (req0[item] > 0) {
            requiredBySkill[STR] |= bit;
        }
        if (req1[item] > 0) {
            requiredBySkill[DEX] |= bit;
        }
        if (req2[item] > 0) {
            requiredBySkill[INT] |= bit;
        }
        if (req3[item] > 0) {
            requiredBySkill[DEF] |= bit;
        }
        if (req4[item] > 0) {
            requiredBySkill[AGI] |= bit;
        }
    }

    private long negativeRequirementImpactMask(int item) {
        long mask = 0L;
        if (bonus0[item] < 0) {
            mask |= requiredBySkill[STR];
        }
        if (bonus1[item] < 0) {
            mask |= requiredBySkill[DEX];
        }
        if (bonus2[item] < 0) {
            mask |= requiredBySkill[INT];
        }
        if (bonus3[item] < 0) {
            mask |= requiredBySkill[DEF];
        }
        if (bonus4[item] < 0) {
            mask |= requiredBySkill[AGI];
        }
        return mask;
    }

    private long greedyAllMask() {
        long activeMask = activateFreeItems();
        long remainingMask = allItemsMask() & ~activeMask;
        long changedMask;
        do {
            changedMask = 0L;
            long candidates = remainingMask;
            while (candidates != 0L) {
                long bit = candidates & -candidates;
                candidates &= candidates - 1L;
                int item = Long.numberOfTrailingZeros(bit);
                if (canEquipNow(item)) {
                    addBonus(item);
                    if (hasNegativeBonus[item] && activeItemsHaveInvalidRequirement(activeMask)) {
                        subtractBonus(item);
                    } else {
                        changedMask |= bit;
                        activeMask |= bit;
                        remainingMask &= ~bit;
                    }
                }
            }
        } while (changedMask != 0L);
        return activeMask;
    }

    private boolean noInactiveItemCanEquip(long activeMask) {
        long candidates = allItemsMask() & ~activeMask;
        while (candidates != 0L) {
            long bit = candidates & -candidates;
            candidates &= candidates - 1L;
            if (canEquipNow(Long.numberOfTrailingZeros(bit))) {
                return false;
            }
        }
        return true;
    }

    private long allItemsMask() {
        return itemCount == 64 ? -1L : (1L << itemCount) - 1L;
    }

    private void resetStateToBase() {
        state[STR] = base0;
        state[DEX] = base1;
        state[INT] = base2;
        state[DEF] = base3;
        state[AGI] = base4;
    }

    private void prepareVisited() {
        useVisited = itemCount <= VISITED_LIMIT;
        if (useVisited) {
            int words = Math.min(VISITED_WORDS, ((1 << itemCount) >>> 6) + 1);
            Arrays.fill(visited, 0, words, 0L);
        }
    }

    private void ensureCapacity(int size) {
        if (items.length >= size) {
            return;
        }

        int capacity = Math.max(size, items.length == 0 ? 16 : items.length * 2);
        items = Arrays.copyOf(items, capacity);
        weights = Arrays.copyOf(weights, capacity);
        req0 = Arrays.copyOf(req0, capacity);
        req1 = Arrays.copyOf(req1, capacity);
        req2 = Arrays.copyOf(req2, capacity);
        req3 = Arrays.copyOf(req3, capacity);
        req4 = Arrays.copyOf(req4, capacity);
        bonus0 = Arrays.copyOf(bonus0, capacity);
        bonus1 = Arrays.copyOf(bonus1, capacity);
        bonus2 = Arrays.copyOf(bonus2, capacity);
        bonus3 = Arrays.copyOf(bonus3, capacity);
        bonus4 = Arrays.copyOf(bonus4, capacity);
        hasRequirement = Arrays.copyOf(hasRequirement, capacity);
        hasNegativeBonus = Arrays.copyOf(hasNegativeBonus, capacity);
        impactMasks = Arrays.copyOf(impactMasks, capacity);
    }

    private long activateFreeItems() {
        long mask = 0L;
        int weight = 0;
        for (int i = 0; i < itemCount; i++) {
            if (hasRequirement[i] || hasNegativeBonus[i]) {
                continue;
            }

            mask |= 1L << i;
            weight += weights[i];
            if (weights[i] != 0) {
                addBonus(i);
            }
        }
        activatedFreeWeight = weight;
        return mask;
    }

    private boolean tryEquipEverything(long remainingMask, long allMask) {
        if (!allCanEquipNow(remainingMask)) {
            return false;
        }

        addBonuses(remainingMask);
        if (activeItemsRemainValid(allMask & impactedBy(remainingMask))) {
            bestMask = allMask;
            bestCount = itemCount;
            bestWeight += maskWeight(remainingMask);
            captureBestBonuses();
            return true;
        }
        removeBonuses(remainingMask);
        return false;
    }

    private boolean allCanEquipNow(long mask) {
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining &= remaining - 1L;
            if (!canEquipNow(Long.numberOfTrailingZeros(bit))) {
                return false;
            }
        }
        return true;
    }

    private long impactedBy(long mask) {
        long impacted = 0L;
        long remaining = mask & negativeMask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining &= remaining - 1L;
            impacted |= impactMasks[Long.numberOfTrailingZeros(bit)];
        }
        return impacted;
    }

    private int maskWeight(long mask) {
        int weight = 0;
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining &= remaining - 1L;
            weight += weights[Long.numberOfTrailingZeros(bit)];
        }
        return weight;
    }

    private void search(long activeMask, long remainingMask, int count, int weight, int remainingWeight) {
        long addedPositiveMask = (remainingMask & negativeMask) == 0L ? closePositiveItems(remainingMask) : 0L;
        if (addedPositiveMask != 0L) {
            int addedWeight = maskWeight(addedPositiveMask);
            activeMask |= addedPositiveMask;
            remainingMask &= ~addedPositiveMask;
            count += Long.bitCount(addedPositiveMask);
            weight += addedWeight;
            remainingWeight -= addedWeight;
        }

        if (alreadyVisited(activeMask)) {
            removeBonuses(addedPositiveMask);
            return;
        }

        recordBest(activeMask, count, weight);

        if (isSearchExhausted(count, remainingMask, weight, remainingWeight)) {
            removeBonuses(addedPositiveMask);
            return;
        }

        long candidates = remainingMask;
        while (candidates != 0L) {
            long bit = candidates & -candidates;
            candidates &= candidates - 1L;
            int item = Long.numberOfTrailingZeros(bit);

            if (!canEquipNow(item)) {
                continue;
            }

            addBonus(item);
            if (!hasNegativeBonus[item] || activeItemsRemainValid(activeMask & impactMasks[item])) {
                search(activeMask | bit, remainingMask & ~bit, count + 1, weight + weights[item], remainingWeight - weights[item]);
            }
            subtractBonus(item);
        }

        removeBonuses(addedPositiveMask);
    }

    private void recordBest(long activeMask, int count, int weight) {
        if (count > bestCount || (count == bestCount && weight > bestWeight)) {
            bestMask = activeMask;
            bestCount = count;
            bestWeight = weight;
            captureBestBonuses();
        }
    }

    private boolean isSearchExhausted(int count, long remainingMask, int weight, int remainingWeight) {
        if (bestCount == itemCount) {
            return true;
        }

        int maximumReachableCount = count + Long.bitCount(remainingMask);
        return maximumReachableCount < bestCount || (maximumReachableCount == bestCount && weight + remainingWeight <= bestWeight);
    }

    private long closePositiveItems(long remainingMask) {
        long addedMask = 0L;
        long changedMask;
        do {
            changedMask = 0L;
            long candidates = remainingMask & ~addedMask;
            while (candidates != 0L) {
                long bit = candidates & -candidates;
                candidates &= candidates - 1L;
                int item = Long.numberOfTrailingZeros(bit);
                if (!hasNegativeBonus[item] && canEquipNow(item)) {
                    changedMask |= bit;
                    if (weights[item] != 0) {
                        addBonus(item);
                    }
                }
            }
            addedMask |= changedMask;
        } while (changedMask != 0L);
        return addedMask;
    }

    private boolean alreadyVisited(long mask) {
        if (!useVisited) {
            return false;
        }

        int index = (int) mask;
        int word = index >>> 6;
        long bit = 1L << (index & 63);
        if ((visited[word] & bit) != 0L) {
            return true;
        }

        visited[word] |= bit;
        return false;
    }

    private boolean canEquipNow(int item) {
        return (req0[item] <= 0 || state[STR] >= req0[item])
                && (req1[item] <= 0 || state[DEX] >= req1[item])
                && (req2[item] <= 0 || state[INT] >= req2[item])
                && (req3[item] <= 0 || state[DEF] >= req3[item])
                && (req4[item] <= 0 || state[AGI] >= req4[item]);
    }

    private boolean activeItemsRemainValid(long activeMask) {
        return !activeItemsHaveInvalidRequirement(activeMask);
    }

    private boolean activeItemsHaveInvalidRequirement(long activeMask) {
        long remaining = activeMask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining &= remaining - 1L;
            if (itemInvalidWithoutOwnBonus(Long.numberOfTrailingZeros(bit))) {
                return true;
            }
        }
        return false;
    }

    private boolean itemInvalidWithoutOwnBonus(int item) {
        return (req0[item] > 0 && state[STR] < req0[item] + bonus0[item])
                || (req1[item] > 0 && state[DEX] < req1[item] + bonus1[item])
                || (req2[item] > 0 && state[INT] < req2[item] + bonus2[item])
                || (req3[item] > 0 && state[DEF] < req3[item] + bonus3[item])
                || (req4[item] > 0 && state[AGI] < req4[item] + bonus4[item]);
    }

    private void addBonuses(long mask) {
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining &= remaining - 1L;
            addBonus(Long.numberOfTrailingZeros(bit));
        }
    }

    private void removeBonuses(long mask) {
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining &= remaining - 1L;
            subtractBonus(Long.numberOfTrailingZeros(bit));
        }
    }

    private void addBonus(int item) {
        state[STR] += bonus0[item];
        state[DEX] += bonus1[item];
        state[INT] += bonus2[item];
        state[DEF] += bonus3[item];
        state[AGI] += bonus4[item];
    }

    private void subtractBonus(int item) {
        state[STR] -= bonus0[item];
        state[DEX] -= bonus1[item];
        state[INT] -= bonus2[item];
        state[DEF] -= bonus3[item];
        state[AGI] -= bonus4[item];
    }

    private void setPlayerToCurrentState(LeMelonPlayer player) {
        int b0 = state[STR] - base0;
        int b1 = state[DEX] - base1;
        int b2 = state[INT] - base2;
        int b3 = state[DEF] - base3;
        int b4 = state[AGI] - base4;
        int[] target = player.bonus;
        target[STR] = b0;
        target[DEX] = b1;
        target[INT] = b2;
        target[DEF] = b3;
        target[AGI] = b4;
        player.weight = b0 + b1 + b2 + b3 + b4;
    }

    private void applyBestResult(LeMelonPlayer player) {
        if (bestCount == 0) {
            player.reset();
            return;
        }

        int[] target = player.bonus;
        target[STR] = bestBonuses[STR];
        target[DEX] = bestBonuses[DEX];
        target[INT] = bestBonuses[INT];
        target[DEF] = bestBonuses[DEF];
        target[AGI] = bestBonuses[AGI];
        player.weight = bestBonuses[STR] + bestBonuses[DEX] + bestBonuses[INT] + bestBonuses[DEF] + bestBonuses[AGI];
    }

    private Result finish(LeMelonPlayer player, List<IEquipment> equipment) {
        applyBestResult(player);
        return createResult(equipment);
    }

    private Result createResult(List<IEquipment> equipment) {
        if (bestCount == itemCount) {
            return new Result(equipment, Collections.emptyList());
        }

        if (bestCount == 0) {
            return new Result(Collections.emptyList(), equipment);
        }

        long allMask = allItemsMask();
        return new Result(
                new MaskEquipmentList(items, bestMask, bestCount),
                new MaskEquipmentList(items, allMask & ~bestMask, itemCount - bestCount));
    }

    private void captureBestBonuses() {
        bestBonuses[STR] = state[STR] - base0;
        bestBonuses[DEX] = state[DEX] - base1;
        bestBonuses[INT] = state[INT] - base2;
        bestBonuses[DEF] = state[DEF] - base3;
        bestBonuses[AGI] = state[AGI] - base4;
    }

    private static final class MaskEquipmentList extends AbstractList<IEquipment> {
        private final IEquipment[] items;
        private final long mask;
        private final int size;

        private MaskEquipmentList(IEquipment[] items, long mask, int size) {
            this.items = items;
            this.mask = mask;
            this.size = size;
        }

        @Override
        public IEquipment get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }

            long remaining = mask;
            for (int i = 0; i < index; i++) {
                remaining &= remaining - 1L;
            }
            return items[Long.numberOfTrailingZeros(remaining)];
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }
            if (!(object instanceof List<?> other) || other.size() != size) {
                return false;
            }

            long remaining = mask;
            for (Object otherItem : other) {
                IEquipment item = items[Long.numberOfTrailingZeros(remaining)];
                if (item != otherItem && !item.equals(otherItem)) {
                    return false;
                }
                remaining &= remaining - 1L;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            long remaining = mask;
            while (remaining != 0L) {
                IEquipment item = items[Long.numberOfTrailingZeros(remaining)];
                hash = 31 * hash + item.hashCode();
                remaining &= remaining - 1L;
            }
            return hash;
        }
    }
}
